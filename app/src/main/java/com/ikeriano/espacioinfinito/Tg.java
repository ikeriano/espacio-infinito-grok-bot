package com.ikeriano.espacioinfinito;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Conexión con Telegram mediante TDLib. Todo se guarda en "Mensajes guardados". */
public final class Tg {
    public interface Listener { void onAuth(TdApi.AuthorizationState s); }
    public interface Progress { void on(long done, long total); }

    public static class TgException extends Exception {
        public final int code;
        TgException(TdApi.Error e) { super(e.message); code = e.code; }
        TgException(String m) { super(m); code = 0; }
    }

    private static Tg inst;
    public static synchronized Tg get() { if (inst == null) inst = new Tg(); return inst; }

    private static Boolean libOk;
    public static synchronized boolean libAvailable() {
        if (libOk == null) {
            try { System.loadLibrary("tdjni"); libOk = true; } catch (Throwable t) { libOk = false; }
        }
        return libOk;
    }

    private Client client;
    private Context ctx;
    private volatile TdApi.AuthorizationState auth;
    private volatile TdApi.User me;
    private volatile long savedChat;
    public final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Long, AtomicReference<Object>> sends = new HashMap<>();
    private final Map<Integer, Progress> progress = new HashMap<>();

    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("ajustes", Context.MODE_PRIVATE); }
    public static boolean hasKeys(Context c) { return prefs(c).getInt("api_id", 0) != 0 && !prefs(c).getString("api_hash", "").isEmpty(); }

    public synchronized void start(Context c) {
        ctx = c.getApplicationContext();
        if (client != null || !hasKeys(ctx) || !libAvailable()) return;
        try { Client.execute(new TdApi.SetLogVerbosityLevel(1)); } catch (Throwable ignored) {}
        client = Client.create(new Client.ResultHandler() { public void onResult(TdApi.Object o) { onUpdate(o); } }, null, null);
    }

    public boolean started() { return client != null; }
    public TdApi.AuthorizationState auth() { return auth; }
    public boolean ready() { return auth instanceof TdApi.AuthorizationStateReady; }
    public TdApi.User me() { return me; }

    private void onUpdate(TdApi.Object o) {
        if (o instanceof TdApi.UpdateAuthorizationState) {
            auth = ((TdApi.UpdateAuthorizationState) o).authorizationState;
            if (auth instanceof TdApi.AuthorizationStateWaitTdlibParameters) sendParams();
            if (auth instanceof TdApi.AuthorizationStateReady) {
                client.send(new TdApi.GetMe(), new Client.ResultHandler() { public void onResult(TdApi.Object r) { if (r instanceof TdApi.User) me = (TdApi.User) r; } });
            }
            if (auth instanceof TdApi.AuthorizationStateClosed) { synchronized (this) { client = null; me = null; savedChat = 0; } if (ctx != null) start(ctx); }
            for (Listener l : listeners) l.onAuth(auth);
        } else if (o instanceof TdApi.UpdateMessageSendSucceeded) {
            TdApi.UpdateMessageSendSucceeded u = (TdApi.UpdateMessageSendSucceeded) o;
            done(u.oldMessageId, u.message);
        } else if (o instanceof TdApi.UpdateMessageSendFailed) {
            TdApi.UpdateMessageSendFailed u = (TdApi.UpdateMessageSendFailed) o;
            done(u.oldMessageId, u.error);
        } else if (o instanceof TdApi.UpdateFile) {
            TdApi.File f = ((TdApi.UpdateFile) o).file;
            Progress p;
            synchronized (progress) { p = progress.get(f.id); }
            if (p != null) {
                long total = f.size > 0 ? f.size : f.expectedSize;
                p.on(f.remote.isUploadingActive || f.remote.isUploadingCompleted ? f.remote.uploadedSize : f.local.downloadedSize, total);
            }
        }
    }

    private void done(long oldId, Object result) {
        synchronized (sends) {
            AtomicReference<Object> ref = sends.get(oldId);
            if (ref == null) { ref = new AtomicReference<>(); sends.put(oldId, ref); }
            synchronized (ref) { ref.set(result); ref.notifyAll(); }
        }
    }

    private void sendParams() {
        SharedPreferences p = prefs(ctx);
        TdApi.SetTdlibParameters s = new TdApi.SetTdlibParameters();
        s.databaseDirectory = new File(ctx.getFilesDir(), "tdlib").getAbsolutePath();
        s.filesDirectory = new File(ctx.getCacheDir(), "tdfiles").getAbsolutePath();
        s.databaseEncryptionKey = new byte[0];
        s.useFileDatabase = true; s.useChatInfoDatabase = true; s.useMessageDatabase = true; s.useSecretChats = false;
        s.apiId = p.getInt("api_id", 0); s.apiHash = p.getString("api_hash", "");
        s.systemLanguageCode = "es"; s.deviceModel = Build.MANUFACTURER + " " + Build.MODEL;
        s.systemVersion = "Android " + Build.VERSION.RELEASE; s.applicationVersion = "Espacio Infinito 1.0";
        client.send(s, new Client.ResultHandler() { public void onResult(TdApi.Object r) {
            if (r instanceof TdApi.Error) for (Listener l : listeners) l.onAuth(auth); } });
    }

    /** Llamada bloqueante. No usar en el hilo principal. */
    public TdApi.Object call(TdApi.Function f, long timeoutSec) throws TgException {
        Client c = client;
        if (c == null) throw new TgException("Telegram no está conectado");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<TdApi.Object> res = new AtomicReference<>();
        c.send(f, new Client.ResultHandler() { public void onResult(TdApi.Object o) { res.set(o); latch.countDown(); } });
        try { if (!latch.await(timeoutSec, TimeUnit.SECONDS)) throw new TgException("Telegram tarda demasiado"); }
        catch (InterruptedException e) { throw new TgException("Cancelado"); }
        TdApi.Object o = res.get();
        if (o instanceof TdApi.Error) throw new TgException((TdApi.Error) o);
        return o;
    }
    public TdApi.Object call(TdApi.Function f) throws TgException { return call(f, 120); }

    public void sendAsync(TdApi.Function f, Client.ResultHandler h) { Client c = client; if (c == null) return; if (h == null) h = new Client.ResultHandler() { public void onResult(TdApi.Object o) {} }; c.send(f, h); }

    public long savedChat() throws TgException {
        if (savedChat != 0) return savedChat;
        if (me == null) me = (TdApi.User) call(new TdApi.GetMe());
        TdApi.Chat chat = (TdApi.Chat) call(new TdApi.CreatePrivateChat(me.id, false));
        return savedChat = chat.id;
    }
    public boolean premium() { return me != null && me.isPremium; }
    public long partLimit() { return premium() ? Logic.PART_PREMIUM : Logic.PART_NORMAL; }

    public void watch(int fileId, Progress p) { synchronized (progress) { if (p == null) progress.remove(fileId); else progress.put(fileId, p); } }

    /**
     * Sube un archivo como documento a Mensajes guardados y ESPERA a que el servidor confirme.
     * Devuelve el mensaje definitivo (ya en el servidor).
     */
    public TdApi.Message upload(String path, String caption, String thumbPath, Progress p, java.util.concurrent.atomic.AtomicBoolean cancel) throws TgException {
        long chat = savedChat();
        TdApi.InputThumbnail th = null;
        if (thumbPath != null && new File(thumbPath).exists()) th = new TdApi.InputThumbnail(new TdApi.InputFileLocal(thumbPath), 0, 0);
        TdApi.SendMessage sm = new TdApi.SendMessage();
        sm.chatId = chat;
        sm.inputMessageContent = new TdApi.InputMessageDocument(new TdApi.InputDocument(new TdApi.InputFileLocal(path), th, true),
                new TdApi.FormattedText(caption, new TdApi.TextEntity[0]));
        TdApi.Message tmp = (TdApi.Message) call(sm);
        int fileId = docFile(tmp) != null ? docFile(tmp).id : 0;
        if (fileId != 0 && p != null) watch(fileId, p);
        AtomicReference<Object> ref;
        synchronized (sends) { ref = sends.get(tmp.id); if (ref == null) { ref = new AtomicReference<>(); sends.put(tmp.id, ref); } }
        try {
            synchronized (ref) {
                long deadline = System.currentTimeMillis() + 12L * 3600 * 1000;
                while (ref.get() == null) {
                    if (cancel != null && cancel.get()) {
                        try { call(new TdApi.DeleteMessages(chat, new long[]{tmp.id}, true)); } catch (TgException ignored) {}
                        throw new TgException("Cancelado");
                    }
                    if (System.currentTimeMillis() > deadline) throw new TgException("La subida no terminó");
                    try { ref.wait(1000); } catch (InterruptedException e) { throw new TgException("Cancelado"); }
                }
            }
        } finally {
            if (fileId != 0) watch(fileId, null);
            synchronized (sends) { sends.remove(tmp.id); }
        }
        Object r = ref.get();
        if (r instanceof TdApi.Error) throw new TgException((TdApi.Error) r);
        return (TdApi.Message) r;
    }

    public static TdApi.File docFile(TdApi.Message m) {
        if (m != null && m.content instanceof TdApi.MessageDocument) return ((TdApi.MessageDocument) m.content).document.document;
        return null;
    }

    /** Vuelve a pedir el mensaje al servidor y comprueba que el archivo está completo. Devuelve el tamaño remoto o -1. */
    public long verifyRemote(long chatId, long msgId) {
        try {
            TdApi.Message m = (TdApi.Message) call(new TdApi.GetMessage(chatId, msgId));
            if (m.sendingState != null) return -1;
            TdApi.File f = docFile(m);
            if (f == null) return -1;
            f = (TdApi.File) call(new TdApi.GetFile(f.id));
            if (!f.remote.isUploadingCompleted || f.remote.id == null || f.remote.id.isEmpty()) return -1;
            return f.size;
        } catch (TgException e) { return -1; }
    }

    /** Descarga completa (bloqueante). Devuelve la ruta local en la caché de TDLib. */
    public TdApi.File download(long chatId, long msgId, Progress p) throws TgException {
        TdApi.Message m = (TdApi.Message) call(new TdApi.GetMessage(chatId, msgId));
        TdApi.File f = docFile(m);
        if (f == null) throw new TgException("El mensaje ya no tiene archivo");
        if (p != null) watch(f.id, p);
        try {
            f = (TdApi.File) call(new TdApi.DownloadFile(f.id, 32, 0, 0, true), 24L * 3600);
        } finally { if (p != null) watch(f.id, null); }
        if (!f.local.isDownloadingCompleted) throw new TgException("La descarga no se completó");
        return f;
    }

    public void logout() { sendAsync(new TdApi.LogOut(), null); }
}
