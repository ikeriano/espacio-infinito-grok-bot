package com.ikeriano.espacioinfinito;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class LogicTest {
    @Test public void smallFileIsOnePart() {
        List<long[]> p = Logic.plan(1000, Logic.PART_NORMAL);
        assertEquals(1, p.size()); assertEquals(0, p.get(0)[0]); assertEquals(1000, p.get(0)[1]);
    }
    @Test public void bigFileSplitsEvenlyAndCoversEverything() {
        long size = 5L * 1024 * 1024 * 1024 + 7; // 5 GB
        List<long[]> p = Logic.plan(size, Logic.PART_NORMAL);
        assertEquals(3, p.size());
        long off = 0, sum = 0;
        for (long[] x : p) { assertEquals(off, x[0]); assertTrue(x[1] <= Logic.PART_NORMAL); off += x[1]; sum += x[1]; }
        assertEquals(size, sum);
    }
    @Test public void premiumUsesBiggerParts() {
        long size = 3500L * Logic.MIB;
        assertEquals(2, Logic.plan(size, Logic.PART_NORMAL).size());
        assertEquals(1, Logic.plan(size, Logic.PART_PREMIUM).size());
        assertTrue(Logic.PART_NORMAL < 2000L * Logic.MIB); // por debajo del límite de 2 GB de Telegram
    }
    @Test public void exactLimit() { assertEquals(1, Logic.plan(Logic.PART_NORMAL, Logic.PART_NORMAL).size()); assertEquals(2, Logic.plan(Logic.PART_NORMAL + 1, Logic.PART_NORMAL).size()); }

    @Test public void kinds() {
        assertEquals("foto", Logic.kind("IMG_2024.JPG")); assertEquals("video", Logic.kind("VID.mp4"));
        assertEquals("apk", Logic.kind("juego.apk")); assertEquals("audio", Logic.kind("a.opus")); assertEquals("otro", Logic.kind("doc.pdf"));
        assertEquals("application/vnd.android.package-archive", Logic.mime("x.apk"));
    }
    @Test public void sizesInSpanish() {
        assertEquals("1,5 GB", Logic.size(1536L * Logic.MIB)); assertEquals("500 MB", Logic.size(500L * Logic.MIB)); assertEquals("12 B", Logic.size(12));
    }
    @Test public void captionRoundTrip() {
        String c = Logic.caption("vídeo boda.mp4", "/storage/emulated/0/DCIM/Camera/vídeo boda.mp4", 2, 3, 123456789L, "abc123", 1700000000000L);
        Map<String, String> m = Logic.parseCaption(c);
        assertNotNull(m);
        assertEquals("vídeo boda.mp4", m.get("nombre")); assertEquals("2", m.get("part")); assertEquals("3", m.get("parts"));
        assertEquals("123456789", m.get("tamano")); assertEquals("abc123", m.get("sha256"));
        assertNull(Logic.parseCaption("hola")); assertNull(Logic.parseCaption(null));
        assertTrue(c.length() < 1024);
    }
    @Test public void chooseOldestAndLargest() {
        List<Logic.Cand> l = new ArrayList<>();
        l.add(new Logic.Cand("a", 100, 3)); l.add(new Logic.Cand("b", 500, 1)); l.add(new Logic.Cand("c", 300, 2));
        List<Logic.Cand> old = Logic.choose(l, "antiguos", 350);
        assertEquals("b", old.get(0).path); assertEquals(1, old.size());
        List<Logic.Cand> big = Logic.choose(l, "grandes", 600);
        assertEquals("b", big.get(0).path); assertEquals("c", big.get(1).path); assertEquals(2, big.size());
        assertTrue(Logic.choose(l, "grandes", 0).isEmpty());
    }
    @Test public void neededSpace() { assertEquals(0, Logic.needed(2000, 1000, 100)); assertEquals(600, Logic.needed(500, 1000, 100)); }

    @Test public void neverDeleteUnlessEverythingMatches() {
        long[] planned = {600, 400};
        assertTrue(Logic.safeToDelete(1000, 5, 5, new long[]{600, 400}, new boolean[]{true, true}, planned));
        assertFalse("parte sin confirmar", Logic.safeToDelete(1000, 5, 5, new long[]{600, 400}, new boolean[]{true, false}, planned));
        assertFalse("tamaño remoto distinto", Logic.safeToDelete(1000, 5, 5, new long[]{600, 399}, new boolean[]{true, true}, planned));
        assertFalse("archivo modificado durante la subida", Logic.safeToDelete(1000, 5, 6, new long[]{600, 400}, new boolean[]{true, true}, planned));
        assertFalse("falta una parte", Logic.safeToDelete(1000, 5, 5, new long[]{600}, new boolean[]{true}, planned));
        assertFalse(Logic.safeToDelete(0, 5, 5, new long[]{0}, new boolean[]{true}, new long[]{0}));
        assertFalse(Logic.safeToDelete(1000, 5, 5, null, null, planned));
    }
    @Test public void exclusions() {
        assertTrue(Logic.excluded("/storage/emulated/0/Android/data/x/file.mp4"));
        assertTrue(Logic.excluded("/storage/emulated/0/DCIM/.thumbnails/a.jpg"));
        assertTrue(Logic.excluded("/storage/emulated/0/Download/EspacioInfinito/a.mp4"));
        assertTrue(Logic.excluded("/storage/emulated/0/Download/a.crdownload"));
        assertFalse(Logic.excluded("/storage/emulated/0/DCIM/Camera/VID_1.mp4"));
    }
}
