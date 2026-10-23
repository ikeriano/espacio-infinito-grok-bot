package com.ikeriano.espacioinfinito;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class DbTest {
    @Test public void totalsCountOnlyCloudFiles() {
        Db.reset(); Db db = Db.get(RuntimeEnvironment.getApplication());
        Db.Rec a = rec("a.mp4", 1000, Db.NUBE), b = rec("b.jpg", 50, Db.LOCAL), c = rec("c.apk", 70, Db.SUBIENDO);
        db.insert(a); db.insert(b); db.insert(c);
        long[] t = db.totals();
        assertEquals(1000, t[0]); assertEquals(2, t[1]);
        assertEquals(2, db.list(null).size()); assertEquals(1, db.list("video").size());
        db.clearStale(); assertNull(db.byId(c.id));
        assertEquals(1000, db.freedPerDay(7)[6]);
        assertArrayEquals(new long[]{11, 22}, db.bySha(a.sha).msgs());
    }
    static Db.Rec rec(String n, long size, String st) {
        Db.Rec r = new Db.Rec(); r.name = n; r.path = "/x/" + n; r.size = size; r.kind = Logic.kind(n); r.mime = Logic.mime(n);
        r.status = st; r.sha = "sha" + n; r.msgIds = "11,22"; r.uploadedAt = System.currentTimeMillis(); r.mtime = 1; return r;
    }
}
