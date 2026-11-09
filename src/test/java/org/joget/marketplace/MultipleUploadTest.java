package org.joget.marketplace;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Test;
import static org.junit.Assert.*;

public class MultipleUploadTest {
    private IpfsFileUpload field(boolean multiple) {
        Form form = new Form();
        Map<String, Object> formProperties = new HashMap<>();
        formProperties.put("id", "record");
        form.setProperties(formProperties);
        IpfsFileUpload field = new IpfsFileUpload();
        Map<String, Object> properties = new HashMap<>();
        properties.put("id", "attachment");
        properties.put("enableIpfs", "false");
        properties.put("multiple", multiple ? "true" : "false");
        field.setProperties(properties);
        field.setParent(form);
        return field;
    }

    @Test
    public void multipleValuesSurviveValidationAndLocalStorageFormatting() {
        IpfsFileUpload field = field(true);
        FormData data = new FormData();
        data.addRequestParameterValues("attachment_path", new String[]{"saved-a.png", "saved-b.png", "saved-c.png"});
        field.formatDataForValidation(data);
        FormRowSet rows = field.formatData(data);
        assertEquals("saved-a.png;saved-b.png;saved-c.png", rows.get(0).getProperty("attachment"));
        assertEquals(Arrays.asList("saved-a.png", "saved-b.png", "saved-c.png"), Arrays.asList(data.getRequestParameterValues("attachment")));
    }

    @Test
    public void removingAllFilesStoresAnEmptyValue() {
        IpfsFileUpload field = field(true);
        FormData data = new FormData();
        field.formatDataForValidation(data);
        assertEquals("", field.formatData(data).get(0).getProperty("attachment"));
    }

    @Test
    public void uncheckedMultipleKeepsSingleFileBehavior() {
        IpfsFileUpload field = field(false);
        FormData data = new FormData();
        data.addRequestParameterValues("attachment_path", new String[]{"a.png", "b.png"});
        field.formatDataForValidation(data);
        assertEquals("a.png", field.formatData(data).get(0).getProperty("attachment"));
    }

    @Test
    public void duplicateFilenamesAreRejectedByServerValidation() {
        IpfsFileUpload field = field(true);
        FormData data = new FormData();
        data.addRequestParameterValues("attachment_path", new String[]{"photo.png", "PHOTO.PNG"});
        field.formatDataForValidation(data);
        assertFalse(field.selfValidate(data));
        assertEquals("Each file must have a unique filename.", data.getFormErrors().get("attachment"));
    }
}
