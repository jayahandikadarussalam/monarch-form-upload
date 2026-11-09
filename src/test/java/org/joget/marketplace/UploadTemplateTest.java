package org.joget.marketplace;

import freemarker.template.Configuration;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class UploadTemplateTest {
    public static class Request {
        public Object getAttribute(String name) { return null; }
        public String getContextPath() { return "/jw"; }
    }
    public static class Upload extends IpfsFileUpload {
        @Override public String getServiceUrl() { return "/service?_nonce=example&_ut=image"; }
    }
    private String render(boolean readonly) throws Exception {
        return render(readonly, false);
    }
    private String render(boolean readonly, boolean multiple) throws Exception {
        Configuration configuration = new Configuration();
        configuration.setClassForTemplateLoading(getClass(), "/templates");
        Map<String, Object> model = new HashMap<>();
        Upload upload = new Upload();
        Map<String, Object> properties = new HashMap<>();
        properties.put("label", "Attachment");
        properties.put("elementUniqueKey", "unique");
        properties.put("multiple", multiple ? "true" : "false");
        upload.setProperties(properties);
        model.put("element", upload);
        model.put("request", new Request());
        model.put("uploadType", "image");
        model.put("uploadAccept", ".png");
        model.put("uploadReadonly", readonly);
        model.put("elementParamName", "attachment");
        Map<String, String> temp = new LinkedHashMap<>();
        temp.put("temporary/path.png", "photo\"<test>.png");
        model.put("tempFilePaths", temp);
        Map<String, String> saved = new LinkedHashMap<>();
        saved.put("/download/photo.png.?attachment=true", "photo.png");
        model.put("filePaths", saved);
        Map<String, Long> sizes = new HashMap<>();
        sizes.put("photo.png", 12345L);
        model.put("uploadFileSizes", sizes);
        StringWriter writer = new StringWriter();
        configuration.getTemplate("fileUpload.ftl").process(model, writer);
        return writer.toString();
    }
    @Test
    public void rendersTemporaryAndSavedPreviewsWithEscapedFilenames() throws Exception {
        String html = render(false);
        assertTrue(html.contains("photo&quot;&lt;test&gt;.png"));
        assertTrue(html.contains("data-temp-path=\"temporary/path.png\""));
        assertTrue(html.contains("data-saved-url=\"/jw/download/photo.png.?attachment=true\""));
        assertTrue(html.contains("data-size=\"12345\""));
        assertTrue(html.contains("class=\"ipfs-upload-browse\""));
    }
    @Test
    public void multipleModeRendersPickerAndIndependentValues() throws Exception {
        String html = render(false, true);
        assertTrue(html.contains("data-multiple=\"true\""));
        assertTrue(html.contains("accept=\".png\" multiple"));
        assertEquals(2, html.split("name=\"attachment_path\"", -1).length - 1);
        assertFalse(render(false).contains("accept=\".png\" multiple"));
    }
    @Test
    public void assetsUsePluginVersionToAvoidCachedSingleUploadCode() throws Exception {
        String html = render(false, true);
        String version = new IpfsFileUpload().getVersion();
        assertTrue(html.contains("/js/upload-preview.js?v=" + version + "\""));
        assertTrue(html.contains("/css/upload-preview.css?v=" + version + "\""));
        assertFalse(html.contains("/js/upload-preview.js\""));
    }
    @Test
    public void readonlyPreservesValuesAndHidesUploadControls() throws Exception {
        String html = render(true);
        assertTrue(html.contains("data-readonly=\"true\""));
        assertTrue(html.contains("name=\"attachment_path\""));
        assertFalse(html.contains("type=\"file\""));
        assertFalse(html.contains("class=\"ipfs-upload-browse\""));
    }
}
