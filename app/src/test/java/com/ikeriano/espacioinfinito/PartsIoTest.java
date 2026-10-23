package com.ikeriano.espacioinfinito;

import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Trocea un archivo de verdad y lo vuelve a unir: el SHA-256 debe coincidir. */
@RunWith(RobolectricTestRunner.class)
public class PartsIoTest {
    @Test public void splitAndJoinKeepsHash() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "ei_test_" + System.nanoTime()); dir.mkdirs();
        File src = new File(dir, "orig.bin");
        byte[] data = new byte[3 * 1024 * 1024 + 123]; new Random(42).nextBytes(data);
        FileOutputStream o = new FileOutputStream(src); o.write(data); o.close();
        List<long[]> plan = Logic.plan(src.length(), 1024 * 1024);
        assertEquals(4, plan.size());
        File joined = new File(dir, "joined.bin");
        for (int i = 0; i < plan.size(); i++) {
            File part = Files.writePart(src, plan.get(i)[0], plan.get(i)[1], new File(dir, "p" + i));
            assertEquals(plan.get(i)[1], part.length());
            Files.copy(part, joined, true);
        }
        assertEquals(Files.sha256(src), Files.sha256(joined));
        assertEquals(64, Files.sha256(src).length());
    }
}
