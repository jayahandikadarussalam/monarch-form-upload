package org.joget.marketplace;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keeps semicolon-delimited filenames and CIDs aligned, including legacy single files. */
final class IpfsFileSet {
    interface Storage {
        String pin(File file) throws Exception;
        void unpin(String cid) throws Exception;
    }

    private IpfsFileSet() { }

    static Set<String> cids(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value != null) {
            for (String cid : value.split(";")) {
                if (!cid.isEmpty()) {
                    result.add(cid);
                }
            }
        }
        return result;
    }

    static String reconcile(String originalNames, String originalCids, List<String> names,
            Map<String, File> uploads, Storage storage) throws Exception {
        Map<String, String> previous = new HashMap<>();
        String[] oldNames = originalNames == null ? new String[0] : originalNames.split(";", -1);
        String[] oldCids = originalCids == null ? new String[0] : originalCids.split(";", -1);
        for (int i = 0; i < oldNames.length; i++) {
            previous.put(oldNames[i], i < oldCids.length ? oldCids[i] : "");
        }
        List<String> result = new ArrayList<>();
        // Pin all new files before removing any previous pins.
        for (String name : names) {
            File file = uploads.get(name);
            String cid = file == null ? previous.getOrDefault(name, "") : storage.pin(file);
            if (file != null && (cid == null || cid.isEmpty())) {
                throw new IllegalStateException("IPFS did not return a content ID");
            }
            result.add(cid);
        }
        Set<String> retained = new LinkedHashSet<>(result);
        for (String cid : cids(originalCids)) {
            if (!retained.contains(cid)) {
                storage.unpin(cid);
            }
        }
        return String.join(";", result);
    }
}
