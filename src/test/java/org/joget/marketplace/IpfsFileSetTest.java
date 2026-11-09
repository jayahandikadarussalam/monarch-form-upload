package org.joget.marketplace;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class IpfsFileSetTest {
    private static class Storage implements IpfsFileSet.Storage {
        final List<String> events = new ArrayList<>();
        String failName;
        @Override public String pin(File file) throws Exception {
            events.add("pin:" + file.getName());
            if (file.getName().equals(failName)) { throw new IOException("Upload failed"); }
            return "cid-" + file.getName();
        }
        @Override public void unpin(String cid) { events.add("unpin:" + cid); }
    }

    @Test
    public void appendingFilesKeepsSavedCidAndPinsEveryNewFile() throws Exception {
        Storage storage = new Storage();
        Map<String, File> uploads = new HashMap<>();
        uploads.put("b.png", new File("b.png"));
        uploads.put("c.png", new File("c.png"));
        String cids = IpfsFileSet.reconcile("a.png", "legacy-cid", Arrays.asList("a.png", "b.png", "c.png"), uploads, storage);
        assertEquals("legacy-cid;cid-b.png;cid-c.png", cids);
        assertEquals(Arrays.asList("pin:b.png", "pin:c.png"), storage.events);
    }

    @Test
    public void removingMiddleFileKeepsCidsAlignedAfterReordering() throws Exception {
        Storage storage = new Storage();
        String cids = IpfsFileSet.reconcile("a;b;c", "cid-a;cid-b;cid-c", Arrays.asList("c", "a"), Collections.emptyMap(), storage);
        assertEquals("cid-c;cid-a", cids);
        assertEquals(Arrays.asList("unpin:cid-b"), storage.events);
    }

    @Test
    public void replacingPinsBeforeUnpinningAndRetainsSharedContent() throws Exception {
        Storage storage = new Storage();
        Map<String, File> uploads = Collections.singletonMap("a", new File("a"));
        assertEquals("cid-a;shared", IpfsFileSet.reconcile("a;b", "shared;shared", Arrays.asList("a", "b"), uploads, storage));
        assertEquals(Arrays.asList("pin:a"), storage.events);
        storage.events.clear();
        assertEquals("cid-a", IpfsFileSet.reconcile("a", "old-cid", Arrays.asList("a"), uploads, storage));
        assertEquals(Arrays.asList("pin:a", "unpin:old-cid"), storage.events);
    }

    @Test
    public void failedBatchDoesNotUnpinPreviousFiles() throws Exception {
        Storage storage = new Storage();
        storage.failName = "c";
        Map<String, File> uploads = new HashMap<>();
        uploads.put("b", new File("b"));
        uploads.put("c", new File("c"));
        try {
            IpfsFileSet.reconcile("a", "old-cid", Arrays.asList("b", "c"), uploads, storage);
            fail("Expected upload failure");
        } catch (IOException expected) {
            assertEquals(Arrays.asList("pin:b", "pin:c"), storage.events);
        }
    }

    @Test
    public void removeAllUnpinsEveryDistinctCid() throws Exception {
        Storage storage = new Storage();
        assertEquals("", IpfsFileSet.reconcile("a;b;c", "same;same;other", Collections.emptyList(), Collections.emptyMap(), storage));
        assertEquals(Arrays.asList("unpin:same", "unpin:other"), storage.events);
        assertEquals(2, IpfsFileSet.cids("same;;same;other;").size());
    }

    @Test
    public void missingLegacyCidDoesNotShiftOtherFiles() throws Exception {
        Storage storage = new Storage();
        assertEquals(";cid-b", IpfsFileSet.reconcile("a;b", ";cid-b", Arrays.asList("a", "b"), Collections.emptyMap(), storage));
        assertTrue(storage.events.isEmpty());
    }
}
