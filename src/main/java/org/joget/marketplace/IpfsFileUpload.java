package org.joget.marketplace;

import io.blockfrost.sdk.api.IPFSService;
import io.blockfrost.sdk.api.model.ipfs.IPFSObject;
import io.blockfrost.sdk.api.util.Constants;
import io.blockfrost.sdk.api.util.NetworkHelper;
import io.blockfrost.sdk.impl.IPFSServiceImpl;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.FileUpload;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.model.FormStoreBinder;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.FileStore;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONObject;
import org.springframework.web.multipart.MultipartFile;

public class IpfsFileUpload extends FileUpload {
    public static final String VERSION = "7.0.8";
    
    /*
        NOTE:
        - For any form DELETE action, you MUST enable "Delete Associated Child Form Data?" property for unpinning to work correctly.
    */
    
    @Override
    public String getName() {
        return "Shadow File Upload";
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "File, image and video upload with previews and optional IPFS storage.";
    }

    public boolean isIpfsEnabled() {
        return UploadPolicy.ipfsEnabled(getProperties());
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        dataModel.put("uploadAccept", UploadPolicy.accept(getPropertyString("uploadType"), getPropertyString("fileType")));
        dataModel.put("uploadType", UploadPolicy.type(getPropertyString("uploadType")));
        dataModel.put("uploadReadonly", FormUtil.isReadonly(this, formData));
        Map<String, Long> fileSizes = new HashMap<>();
        String[] values = FormUtil.getElementPropertyValues(this, formData);
        String storedValue = formData.getStoreBinderDataProperty(this);
        if (storedValue != null) {
            values = storedValue.split(";");
        }
        String[] pending = formData.getRequestParameterValues(FormUtil.getElementParameterName(this) + "_path");
        if (pending != null && pending.length > 0) {
            values = pending;
        }
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                try {
                    File file = FileManager.getFileByPath(value);
                    if (file == null) {
                        file = FileUtil.getFile(value, this, getPrimaryKeyValue(formData));
                    }
                    if (file != null && file.isFile()) {
                        fileSizes.put(file.getName(), file.length());
                    }
                } catch (Exception ex) {
                    // A missing stored file must not prevent the form from rendering.
                }
            }
        }
        dataModel.put("uploadFileSizes", fileSizes);
        // FileUpload populates saved download URLs and temporary paths; our bundle supplies fileUpload.ftl.
        return super.renderTemplate(formData, dataModel);
    }

    @Override
    public Boolean selfValidate(FormData formData) {
        boolean valid = super.selfValidate(formData);
        String[] values = FormUtil.getElementPropertyValues(this, formData);
        if (values != null) {
            Set<String> names = new HashSet<>();
            for (String value : values) {
                File file = FileManager.getFileByPath(value);
                String filename = file == null ? value : file.getName();
                if ("true".equals(getPropertyString("multiple")) && filename != null && !filename.isEmpty()
                        && !names.add(filename.toLowerCase(java.util.Locale.ROOT))) {
                    formData.addFormError(FormUtil.getElementParameterName(this), "Each file must have a unique filename.");
                    valid = false;
                }
                if (file != null && !UploadPolicy.allows(file.getName(), getPropertyString("uploadType"), getPropertyString("fileType"))) {
                    formData.addFormError(FormUtil.getElementParameterName(this), ResourceBundleUtil.getMessage("form.fileupload.fileType.msg.invalidFileType"));
                    valid = false;
                }
            }
        }
        return valid;
    }
    
    @Override
    public FormRowSet formatData(FormData formData) {
        if (!isIpfsEnabled()) {
            return super.formatData(formData);
        }
        FormRowSet rowSet = new FormRowSet();
        String id = getPropertyString(FormUtil.PROPERTY_ID);
        String[] values = FormUtil.getElementPropertyValues(this, formData);
        if (id == null || values == null || values.length == 0) {
            return rowSet;
        }
        Form form = FormUtil.findRootForm(this);
        String cidField = getPropertyString("ipfsCidField");
        String originalValue = formData.getLoadBinderDataProperty(form, id);
        String originalCids = formData.getLoadBinderDataProperty(form, cidField);
        List<String> filenames = new ArrayList<>();
        List<String> tempPaths = new ArrayList<>();
        Map<String, File> uploads = new HashMap<>();
        int count = "true".equals(getPropertyString("multiple")) ? values.length : 1;
        for (int i = 0; i < count; i++) {
            String value = values[i];
            if (value == null || value.isEmpty()) {
                continue;
            }
            File file = FileManager.getFileByPath(value);
            if (file != null) {
                tempPaths.add(value);
                filenames.add(file.getName());
                uploads.put(file.getName(), file);
            } else {
                filenames.add(value);
            }
        }
        FormRow result = new FormRow();
        IPFSService service = new IPFSServiceImpl(Constants.BLOCKFROST_IPFS_URL, getPropertyString("blockfrostIpfsProjectKey"));
        try {
            String cids = IpfsFileSet.reconcile(originalValue, originalCids, filenames, uploads, new IpfsFileSet.Storage() {
                @Override
                public String pin(File file) throws Exception {
                    IPFSObject object = service.add(file);
                    service.pinAdd(object.getIpfsHash());
                    return object.getIpfsHash();
                }
                @Override
                public void unpin(String cid) throws Exception {
                    service.removePinnedObject(cid);
                }
            });
            result.setProperty(cidField, cids);
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, "Unable to synchronize uploaded files with IPFS");
            return null;
        } finally {
            NetworkHelper.getInstance().shutdown();
        }
        if (!tempPaths.isEmpty()) {
            result.putTempFilePath(id, tempPaths.toArray(String[]::new));
        }
        if ("true".equals(getPropertyString("removeFile")) && originalValue != null) {
            Set<String> removed = new HashSet<>(Arrays.asList(originalValue.split(";")));
            removed.removeAll(filenames);
            result.putDeleteFilePath(id, removed.toArray(String[]::new));
        }
        String[] savedNames = filenames.toArray(String[]::new);
        result.setProperty(id, FormUtil.generateElementPropertyValues(savedNames));
        rowSet.add(result);
        String paramName = FormUtil.getElementParameterName(this);
        formData.addRequestParameterValues(paramName, savedNames);
        formData.addRequestParameterValues(paramName + "_path", new String[]{});
        // The wrapper stores separately from the root form binder.
        if (form != null) {
            formData.setStoreBinderData(form.getStoreBinder(), rowSet);
        }
        return rowSet;
    }

    @Override
    public void setStoreBinder(FormStoreBinder storeBinder) {
        if (!isIpfsEnabled()) {
            super.setStoreBinder(storeBinder);
            return;
        }
        /* 
            When custom store binder is set, formData will keep storeBinderData separate from form object's storeBinderData.
            Must manually handle data to be stored by this element. See formatData() above.
        */
        
        IpfsFileUploadStoreBinderWrapper binder = new IpfsFileUploadStoreBinderWrapper(getProperties());
        super.setStoreBinder(binder);
    }
    
    public static String getFileHash(File file) throws FileNotFoundException, IOException {
        if (file == null) {
            return null;
        }
//        return DigestUtils.md5Hex(new FileInputStream(file));
        try (FileInputStream stream = new FileInputStream(file)) {
            return DigestUtils.sha256Hex(stream);
        }
    }
    
    @Override
    public String getServiceUrl() {     
        //Changed to match this class name
        String url = WorkflowUtil.getHttpServletRequest().getContextPath()+ "/web/json/plugin/" + getClass().getName() + "/service";
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        
        //create nonce
        String paramName = FormUtil.getElementParameterName(this);
        String fileType = getPropertyString("fileType");
        String uploadType = UploadPolicy.type(getPropertyString("uploadType"));
        String nonce = SecurityUtil.generateNonce(
                new String[]{
                    getClass().getSimpleName(), //Changed to match this class name
                    appDef.getAppId(), 
                    appDef.getVersion().toString(), 
                    paramName, 
                    fileType,
                    uploadType
                }, 1
        );
        
        try {
            url = url + 
                    "?_nonce=" + URLEncoder.encode(nonce, "UTF-8") +
                    "&_paramName=" + URLEncoder.encode(paramName, "UTF-8") +
                    "&_appId=" + URLEncoder.encode(appDef.getAppId(), "UTF-8") +
                    "&_appVersion=" + URLEncoder.encode(appDef.getVersion().toString(), "UTF-8") +
                    "&_ft=" + URLEncoder.encode(fileType, "UTF-8") +
                    "&_ut=" + URLEncoder.encode(uploadType, "UTF-8");
        } catch (Exception e) {}
        
        return url;
    }
    
    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String nonce = request.getParameter("_nonce");
        String paramName = request.getParameter("_paramName");
        String appId = request.getParameter("_appId");
        String appVersion = request.getParameter("_appVersion");
        String filePath = request.getParameter("_path");
        String fileType = request.getParameter("_ft");
        String uploadType = request.getParameter("_ut");

        //Changed to match this class name
        if (!SecurityUtil.verifyNonce(nonce, new String[]{getClass().getSimpleName(), appId, appVersion, paramName, fileType, uploadType})) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ResourceBundleUtil.getMessage("general.error.error403"));
            return;
        }
        
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            try {
                JSONObject obj = new JSONObject();
                try {
                    // handle multipart files
                    String validatedParamName = SecurityUtil.validateStringInput(paramName);
                    MultipartFile file = FileStore.getFile(validatedParamName);
                    if (file != null && file.getOriginalFilename() != null && !file.getOriginalFilename().isEmpty()) {
                        if (UploadPolicy.allows(file.getOriginalFilename(), uploadType, fileType)) {
                            String path = FileManager.storeFile(file);
                            obj.put("path", path);
                            obj.put("filename", file.getOriginalFilename());
                            obj.put("newFilename", path.substring(path.lastIndexOf(File.separator) + 1));
                        } else {
                            obj.put("error", ResourceBundleUtil.getMessage("form.fileupload.fileType.msg.invalidFileType"));
                        }
                    }

                    Collection<String> errorList = FileStore.getFileErrorList();
                    if (errorList != null && !errorList.isEmpty() && errorList.contains(paramName)) {
                        obj.put("error", ResourceBundleUtil.getMessage("general.error.fileSizeTooLarge", new Object[]{FileStore.getFileSizeLimit()}));
                    }
                } catch (Exception e) {
                    obj.put("error", e.getLocalizedMessage());
                } finally {
                    FileStore.clear();
                }
                obj.write(response.getWriter());
            } catch (Exception ex) {}
        } else if (filePath != null && !filePath.isEmpty()) {
            String normalizedFilePath = SecurityUtil.normalizedFileName(filePath);

            File file = FileManager.getFileByPath(normalizedFilePath);
            
            if (file == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            
            ServletOutputStream stream = response.getOutputStream();
            DataInputStream in = new DataInputStream(new FileInputStream(file));
            byte[] bbuf = new byte[65536];

            try {
                String contentType = request.getSession().getServletContext().getMimeType(file.getName());
                if (contentType != null) {
                    response.setContentType(contentType);
                }
                response.setHeader("X-Content-Type-Options", "nosniff");
                // Active documents are downloadable, never executed in the Joget origin.
                if (contentType == null || !(contentType.startsWith("image/") || contentType.startsWith("video/") || "application/pdf".equals(contentType))) {
                    response.setHeader("Content-Disposition", "attachment");
                }
                if ("image/svg+xml".equals(contentType)) {
                    response.setHeader("Content-Security-Policy", "sandbox");
                }

                // send output
                int length = 0;
                while ((in != null) && ((length = in.read(bbuf)) != -1)) {
                    stream.write(bbuf, 0, length);
                }
            } catch (Exception e) {
                //do nothing
            } finally {
                in.close();
                stream.flush();
                stream.close();
            }
        }
    }
    
    @Override
    public String getLabel() {
        return getName();
    }
    
    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/ipfsFileUpload.json", null, true, "messages/ipfsFileUpload");
    }

    @Override
    public String getFormBuilderCategory() {
        return "<span><i class=\"fas fa-crown\"></i> Monarch </span>";
    }

    @Override
    public int getFormBuilderPosition() {
        return 1;
    }

    @Override
    public String getFormBuilderIcon() {
        return "<i class=\"fas fa-cloud-upload-alt fa-cloud-arrow-up\"></i>";
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<label class='label'>" + getName() + "</label><input type='file' />";
    }
}
