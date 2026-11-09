package org.joget.marketplace;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class UploadPolicyTest {
    @Test
    public void imageAndVideoCannotBypassCategoryUsingExtensionSetting() {
        assertTrue(UploadPolicy.allows("PHOTO.JPG", "image", ".jpg;.png"));
        assertFalse(UploadPolicy.allows("payload.jpg.exe", "image", ""));
        assertFalse(UploadPolicy.allows("document.pdf", "image", ".pdf"));
        assertFalse(UploadPolicy.allows("photo.png", "video", ".png"));
        assertTrue(UploadPolicy.allows("clip.MP4", "video", ".mp4"));
        assertFalse(UploadPolicy.allows("clip.webm", "video", ".mp4"));
    }

    @Test
    public void genericFilesAllowExtensionlessNamesAndExactExtensionMatching() {
        assertTrue(UploadPolicy.allows("README", "file", ""));
        assertTrue(UploadPolicy.allows("report.PDF", "file", ".pdf;.docx"));
        assertFalse(UploadPolicy.allows("report.pdfx", "file", ".pdf"));
        assertFalse(UploadPolicy.allows("README", "file", ".txt"));
        assertFalse(UploadPolicy.allows(null, "file", ""));
    }

    @Test
    public void browserFilterIntersectsCategoryAndConfiguredExtensions() {
        assertEquals(".png", UploadPolicy.accept("image", ".png;.pdf"));
        assertEquals(".mp4", UploadPolicy.accept("video", ".mp4;.png"));
        assertEquals("", UploadPolicy.accept("file", ""));
    }

    @Test
    public void explicitCheckboxOverridesLegacyCredentials() {
        Map<String, Object> properties = new HashMap<>();
        assertFalse(UploadPolicy.ipfsEnabled(properties));
        properties.put("blockfrostIpfsProjectKey", "legacy-project");
        assertTrue(UploadPolicy.ipfsEnabled(properties));
        properties.put("enableIpfs", "false");
        assertFalse(UploadPolicy.ipfsEnabled(properties));
        properties.put("enableIpfs", "");
        assertFalse(UploadPolicy.ipfsEnabled(properties));
        properties.put("enableIpfs", "true");
        assertTrue(UploadPolicy.ipfsEnabled(properties));
    }

    @Test
    public void disabledIpfsDeleteIgnoresCidAndEmptyRows() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("enableIpfs", "false");
        // A null row set would fail immediately if the disabled branch reached IPFS processing.
        new IpfsFileUploadStoreBinderWrapper(properties).delete(null, null, null, false, false, false, true);
    }
}
