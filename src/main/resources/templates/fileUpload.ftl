<div class="form-cell" ${elementMetaData!}>
    <#if !(request.getAttribute("org.joget.marketplace.IpfsFileUpload")??)>
        <link rel="stylesheet" href="${request.contextPath}/js/dropzone/dropzone.css" />
        <script src="${request.contextPath}/js/dropzone/dropzone.js"></script>
        <script>Dropzone.autoDiscover = false;</script>
        <link rel="stylesheet" href="${request.contextPath}/plugin/org.joget.marketplace.IpfsFileUpload/css/upload-preview.css?v=${element.version?html}" />
        <script src="${request.contextPath}/plugin/org.joget.marketplace.IpfsFileUpload/js/upload-preview.js?v=${element.version?html}"></script>
    </#if>
    <label class="label" for="${elementParamName!?html}" field-tooltip="${elementParamName!?html}">${element.properties.label!} <span class="form-cell-validator">${decoration!}</span><#if error??> <span class="form-error-message">${error?html}</span></#if></label>
    <div id="upload-preview_${elementParamName!?html}_${element.properties.elementUniqueKey!?html}" class="ipfs-upload <#if error??>form-error-cell</#if>"
         data-url="${element.serviceUrl!?html}" data-param="${elementParamName!?html}"
         data-multiple="${element.properties.multiple!?html}" data-readonly="${uploadReadonly?string('true', 'false')}" data-accept="${uploadAccept?html}" data-upload-type="${uploadType?html}"
         data-max-size="${element.properties.maxSize!?html}" data-size-message="${element.properties.maxSizeMsg!?html}"
         data-type-message="${element.properties.fileTypeMsg!?html}" data-file-types="${element.properties.fileType!?html}"
         data-resize-width="${element.properties.resizeWidth!?html}" data-resize-height="${element.properties.resizeHeight!?html}"
         data-resize-quality="${element.properties.resizeQuality!?html}" data-resize-method="${element.properties.resizeMethod!?html}"
         data-padding="${element.properties.padding!?html}">
        <div class="ipfs-upload-dropzone">
            <ul class="ipfs-upload-cards">
                <#if tempFilePaths??>
                    <#list tempFilePaths?keys as key>
                        <li class="ipfs-upload-card" data-filename="${tempFilePaths[key]!?html}" data-temp-path="${key?html}" <#if uploadFileSizes[tempFilePaths[key]]??>data-size="${uploadFileSizes[tempFilePaths[key]]?c}"</#if>>
                            <input type="hidden" name="${elementParamName!?html}_path" value="${key?html}" />
                        </li>
                    </#list>
                </#if>
                <#if filePaths??>
                    <#list filePaths?keys as key>
                        <li class="ipfs-upload-card" data-filename="${filePaths[key]!?html}" data-saved-url="${request.contextPath}${key?html}" <#if uploadFileSizes[filePaths[key]]??>data-size="${uploadFileSizes[filePaths[key]]?c}"</#if>>
                            <input type="hidden" name="${elementParamName!?html}_path" value="${filePaths[key]!?html}" />
                        </li>
                    </#list>
                </#if>
            </ul>
            <#if !uploadReadonly><div class="ipfs-upload-empty">Drop files here or choose Browse</div></#if>
        </div>
        <#if !uploadReadonly>
            <div class="ipfs-upload-toolbar">
                <span class="ipfs-upload-caption" role="status" aria-live="polite">No file selected</span>
                <button type="button" class="ipfs-upload-clear">&#215; Remove</button>
                <button type="button" class="ipfs-upload-browse"><i class="fas fa-cloud-upload-alt fa-cloud-arrow-up" aria-hidden="true"></i> Browse</button>
            </div>
            <input class="ipfs-upload-fallback" type="file" id="${elementParamName!?html}" name="${elementParamName!?html}" accept="${uploadAccept?html}" <#if element.properties.multiple! == 'true'>multiple</#if> />
            <div class="ipfs-upload-message" role="alert"></div>
        </#if>
    </div>
    <script>
        jQuery(function () {
            var target = document.getElementById("upload-preview_${elementParamName!?js_string}_${element.properties.elementUniqueKey!?js_string}");
            if (target) { jQuery(target).ipfsUploadPreview(); }
        });
    </script>
</div>
