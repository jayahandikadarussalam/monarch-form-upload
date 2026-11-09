package org.joget.marketplace;

import io.blockfrost.sdk.api.IPFSService;
import io.blockfrost.sdk.api.util.Constants;
import io.blockfrost.sdk.api.util.NetworkHelper;
import io.blockfrost.sdk.impl.IPFSServiceImpl;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormDeleteBinder;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormStoreBinder;
import org.joget.commons.util.LogUtil;

public class IpfsFileUploadStoreBinderWrapper extends FormBinder implements FormStoreBinder, FormDeleteBinder {

    protected final Map properties;
    
    @Override
    public String getName() {
        return "Shadow File Upload Store Binder Wrapper";
    }

    @Override
    public String getVersion() {
        return IpfsFileUpload.VERSION;
    }

    @Override
    public String getDescription() {
        return "Simple wrapper to track form CRUD operations & handle IPFS content pinning.";
    }

    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return "";
    }

    public IpfsFileUploadStoreBinderWrapper (Map properties) {
        this.properties = properties;
    }
    
    @Override
    public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
        //Do nothing and just return as is
        return rows;
    }

    @Override
    public void delete(Element element, FormRowSet rows, FormData formData, boolean deleteGrid, boolean deleteSubform, boolean abortProcess, boolean deleteFiles) {
        if (!UploadPolicy.ipfsEnabled(properties) || rows == null || rows.isEmpty()) {
            return;
        }
        String blockfrostIpfsProjectKey = (String) properties.get("blockfrostIpfsProjectKey");
        String ipfsCidField = (String) properties.get("ipfsCidField");
        
        Set<String> cids = new LinkedHashSet<>();
        for (FormRow row : rows) {
            cids.addAll(IpfsFileSet.cids(row.getProperty(ipfsCidField)));
        }
        if (!cids.isEmpty()) {
            IPFSService ipfsService = new IPFSServiceImpl(Constants.BLOCKFROST_IPFS_URL, blockfrostIpfsProjectKey);
            try {
                for (String cid : cids) {
                    try {
                        ipfsService.removePinnedObject(cid);
                        LogUtil.info(getClass().getName(), "Form record deleted. Unpinning file cid of: " + cid);
                    } catch (Exception ex) {
                        LogUtil.error(getClass().getName(), ex, "Unable to unpin file on IPFS upon form record deletion");
                    }
                }
            } finally {
                NetworkHelper.getInstance().shutdown();
            }
        }
    }
}
