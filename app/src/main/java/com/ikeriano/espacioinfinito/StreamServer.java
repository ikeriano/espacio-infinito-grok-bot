package com.ikeriano.espacioinfinito;

import org.drinkless.tdlib.TdApi;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;

/** Mini servidor HTTP local (127.0.0.1) para reproducir vídeos mientras se descargan de Telegram. */
public final class StreamServer {
    private static StreamServer inst;
    private ServerSocket ss;
    public static synchronized StreamServer get() throws Exception {
        if (inst == null) { inst = new StreamServer(); inst.open(); }
        return inst;
    }
    private void open() throws Exception {
        ss = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(new Runnable() { public void run() {
            while (true) {
                try { final Socket s = ss.accept(); new Thread(new Runnable() { public void run() { serve(s); } }).start(); }
                catch (Exception e) { return; }
            }
        } });
        t.setDaemon(true); t.start();
    }
    public String url(long chatId, long msgId, String name) {
        return "http://127.0.0.1:" + ss.getLocalPort() + "/" + chatId + "/" + msgId + "/" + android.net.Uri.encode(name);
    }

    private void serve(Socket s) {
        try {
            s.setSoTimeout(60000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String req = in.readLine(); if (req == null) return;
            long start = 0; String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                String l = line.toLowerCase(Locale.ROOT);
                if (l.startsWith("range: bytes=")) { String v = l.substring(13); int d = v.indexOf('-'); start = Long.parseLong(v.substring(0, d).trim()); }
            }
            String[] parts = req.split(" ")[1].split("/");
            long chat = Long.parseLong(parts[1]), msg = Long.parseLong(parts[2]);
            Tg tg = Tg.get();
            TdApi.File f = Tg.docFile((TdApi.Message) tg.call(new TdApi.GetMessage(chat, msg)));
            long size = f.size;
            if (start >= size) start = 0;
            String mime = Logic.mime(parts.length > 3 ? android.net.Uri.decode(parts[3]) : "x.mp4");
            OutputStream out = s.getOutputStream();
            String head = (start > 0 ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n") + "Content-Type: " + mime + "\r\nAccept-Ranges: bytes\r\n"
                    + "Content-Length: " + (size - start) + "\r\n" + (start > 0 ? "Content-Range: bytes " + start + "-" + (size - 1) + "/" + size + "\r\n" : "") + "Connection: close\r\n\r\n";
            out.write(head.getBytes("UTF-8"));
            if (req.startsWith("HEAD")) { out.flush(); return; }
            tg.sendAsync(new TdApi.DownloadFile(f.id, 32, start, 0, false), null);
            long pos = start; byte[] buf = new byte[64 * 1024]; RandomAccessFile raf = null; int idle = 0;
            try {
                while (pos < size) {
                    long avail = ((TdApi.FileDownloadedPrefixSize) tg.call(new TdApi.GetFileDownloadedPrefixSize(f.id, pos))).size;
                    if (avail <= 0) {
                        if (++idle > 1200) break; // 2 min sin datos
                        Thread.sleep(100); continue;
                    }
                    idle = 0;
                    if (raf == null) { TdApi.File cur = (TdApi.File) tg.call(new TdApi.GetFile(f.id)); raf = new RandomAccessFile(cur.local.path, "r"); }
                    raf.seek(pos);
                    int n = raf.read(buf, 0, (int) Math.min(buf.length, avail));
                    if (n <= 0) { Thread.sleep(50); continue; }
                    out.write(buf, 0, n); pos += n;
                }
            } finally { if (raf != null) raf.close(); }
            out.flush();
        } catch (Exception ignored) {
        } finally { try { s.close(); } catch (Exception ignored) {} }
    }
}
