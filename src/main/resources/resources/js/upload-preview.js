(function ($) {
    'use strict';
    var imageExtensions = /\.(jpe?g|png|gif|webp|bmp|svg|avif|tiff?|ico|heic|heif)$/i;
    var videoExtensions = /\.(mp4|webm|og[gv]|mov|m4v|avi|mkv|mpeg|mpg|3gp|wmv)$/i;
    var textExtensions = /\.(txt|csv|tsv|log|json|md|xml|ya?ml)$/i;

    function kind(name) {
        if (imageExtensions.test(name)) { return 'image'; }
        if (videoExtensions.test(name)) { return 'video'; }
        if (/\.pdf$/i.test(name)) { return 'pdf'; }
        if (textExtensions.test(name)) { return 'text'; }
        return 'file';
    }
    function sizeLabel(size) {
        if (size === undefined || size === null) { return ''; }
        return size < 1024 ? size + ' B' : size < 1048576 ? (size / 1024).toFixed(2) + ' KB' : (size / 1048576).toFixed(2) + ' MB';
    }
    function previewMedia(name, url) {
        var type = kind(name);
        var media;
        if (type === 'image') {
            media = $('<img>').attr({src: url, alt: name, loading: 'lazy'});
        } else if (type === 'video') {
            media = $('<video controls preload="metadata" playsinline></video>').attr({src: url, 'aria-label': name});
        } else {
            return $('<span class="ipfs-upload-icon"></span>').text(type === 'pdf' ? 'PDF' : type === 'text' ? 'TXT' : 'FILE');
        }
        media.on('error', function () {
            $(this).replaceWith($('<span class="ipfs-upload-icon">Preview unavailable</span>'));
        });
        return media;
    }
    function openPreview(name, url, size, opener, downloadUrl) {
        var dialog = $('<dialog class="ipfs-upload-dialog" aria-label="File preview"></dialog>');
        var header = $('<div class="ipfs-upload-dialog-header"></div>').appendTo(dialog);
        $('<h2 class="ipfs-upload-dialog-title"></h2>').text(name).appendTo(header);
        $('<button type="button" class="ipfs-upload-dialog-close" aria-label="Close preview">&#215;</button>').appendTo(header).on('click', function () { dialog[0].close(); });
        var body = $('<div class="ipfs-upload-dialog-body"></div>').appendTo(dialog);
        var type = kind(name);
        if (type === 'image' || type === 'video') {
            body.append(previewMedia(name, url));
        } else if (type === 'pdf') {
            body.append($('<iframe sandbox="" title="PDF preview"></iframe>').attr('src', url));
        } else if (type === 'text' && (size === undefined || size <= 1048576)) {
            var pre = $('<pre>Loading preview...</pre>').appendTo(body);
            // Read at most 1 MiB, including files whose saved size is unknown.
            fetch(url, {credentials: 'same-origin'}).then(function (response) {
                if (!response.ok || !response.body) { throw new Error('Preview unavailable'); }
                var reader = response.body.getReader(), chunks = [], total = 0;
                function read() {
                    return reader.read().then(function (part) {
                        if (part.done) { return new Blob(chunks).text(); }
                        total += part.value.byteLength;
                        if (total > 1048576) { reader.cancel(); throw new Error('File is too large for a text preview.'); }
                        chunks.push(part.value);
                        return read();
                    });
                }
                return read();
            }).then(function (text) { pre.text(text); }).catch(function (error) { pre.text(error.message); });
        } else {
            body.append($('<p></p>').text(downloadUrl
                ? 'Preview is unavailable for this file type. Open or download the file to view it.'
                : 'Preview is unavailable for this file type. Save the form to open or download the file.'));
        }
        if (downloadUrl) {
            $('<a target="_blank" rel="noopener noreferrer">Open / download file</a>').attr('href', downloadUrl).appendTo(body);
        }
        dialog.appendTo(document.body);
        dialog.on('close', function () {
            dialog.find('video').each(function () { this.pause(); });
            dialog.remove();
            if (opener && document.contains(opener)) { opener.focus(); }
        });
        if (typeof dialog[0].showModal === 'function') {
            dialog[0].showModal();
        } else {
            dialog.remove();
            window.open(url, '_blank', 'noopener,noreferrer');
        }
    }

    $.fn.ipfsUploadPreview = function () {
        return this.each(function () {
            var root = $(this);
            if (root.data('previewInitialized')) { return; }
            root.data('previewInitialized', true);
            var option = function (name) { return root.attr('data-' + name) || ''; };
            var readonly = option('readonly') === 'true';
            var multiple = option('multiple') === 'true';
            var cards = root.find('.ipfs-upload-cards');
            var dropArea = root.find('.ipfs-upload-dropzone');
            var uploader;
            var form = root.closest('form');
            var message = root.find('.ipfs-upload-message');
            var serviceUrl = option('url');
            function tempUrl(path) { return serviceUrl + '&_path=' + encodeURIComponent(path); }
            function refresh() {
                var count = cards.children().length;
                root.find('.ipfs-upload-empty').toggle(count === 0);
                var uploading = cards.children('[data-uploading="true"]').length > 0;
                root.find('.ipfs-upload-caption').text(uploading ? 'Uploading...' : count ? count + (count === 1 ? ' file selected' : ' files selected') : 'No file selected');
                root.find('.ipfs-upload-clear').prop('disabled', count === 0);
            }
            function release(card) {
                var objectUrl = card.data('objectUrl');
                if (objectUrl) { URL.revokeObjectURL(objectUrl); card.removeData('objectUrl'); }
                card.find('video').each(function () { this.pause(); });
            }
            function remove(card) {
                var file = card.data('uploadFile');
                if (file && uploader) { uploader.removeFile(file); }
                else { release(card); card.remove(); refresh(); }
            }
            function decorate(card, name, url, size) {
                card.attr('data-filename', name).data('previewUrl', url).data('fileSize', size);
                card.children(':not(input)').remove();
                $('<div class="ipfs-upload-media"></div>').append(previewMedia(name, url)).appendTo(card);
                $('<div class="ipfs-upload-name"></div>').text(name).attr('title', name).appendTo(card);
                $('<div class="ipfs-upload-size"></div>').text(sizeLabel(size)).appendTo(card);
                var actions = $('<div class="ipfs-upload-actions"></div>').appendTo(card);
                if (card.data('downloadUrl')) {
                    $('<a target="_blank" rel="noopener noreferrer">Open</a>').attr('href', card.data('downloadUrl')).appendTo(actions);
                }
                if (!readonly) {
                    $('<button type="button" class="ipfs-upload-remove">Remove</button>').appendTo(actions).on('click', function () { remove(card); });
                }
                $('<button type="button" class="ipfs-upload-zoom" aria-label="Enlarge preview">&#8981; Preview</button>').appendTo(actions).on('click', function () {
                    openPreview(card.attr('data-filename'), card.data('previewUrl'), card.data('fileSize'), this, card.data('downloadUrl'));
                });
            }
            cards.children().each(function () {
                var card = $(this);
                var savedUrl = card.attr('data-saved-url');
                var url = savedUrl || tempUrl(card.attr('data-temp-path'));
                // Only records loaded from persistent storage expose an Open/download action.
                if (savedUrl) { card.data('downloadUrl', savedUrl); }
                // The saved endpoint may force attachment downloads. Media uses its inline variant.
                url = url.replace('?attachment=true', '');
                decorate(card, card.attr('data-filename'), url, card.attr('data-size') === undefined ? undefined : Number(card.attr('data-size')));
            });
            refresh();
            if (readonly) { return; }
            if (option('padding')) { dropArea.css('padding', option('padding')); }
            if (typeof Dropzone === 'undefined' || !Dropzone.isBrowserSupported()) {
                root.find('.ipfs-upload-browse').on('click', function () { root.find('input[type=file]')[0].click(); });
                root.find('.ipfs-upload-clear').on('click', function () { cards.children().each(function () { remove($(this)); }); root.find('input[type=file]').val(''); });
                return;
            }
            root.addClass('ipfs-upload-enhanced');
            var options = {
                url: serviceUrl,
                paramName: option('param'),
                clickable: root.find('.ipfs-upload-browse')[0],
                hiddenInputContainer: root[0],
                previewsContainer: cards[0],
                previewTemplate: '<li class="ipfs-upload-card"></li>',
                acceptedFiles: option('accept') || null,
                maxFilesize: option('max-size') ? Number(option('max-size')) / 1024 : 100000,
                timeout: 0,
                createImageThumbnails: false,
                dictInvalidFileType: option('type-message') || 'Invalid file type.',
                dictFileTooBig: option('size-message') || 'File is too large.',
                accept: function (file, done) {
                    var type = option('upload-type');
                    if (!multiple && uploader.getUploadingFiles().concat(uploader.getQueuedFiles()).some(function (other) { return other !== file; })) {
                        done('Choose one file at a time.');
                        return;
                    }
                    if (multiple && cards.children().not(file.previewElement).toArray().some(function (other) {
                        return ($(other).attr('data-filename') || '').toLowerCase() === file.name.toLowerCase();
                    })) {
                        file.shadowDuplicate = true;
                        done('A file with this name is already selected. Remove it first or rename the new file.');
                        return;
                    }
                    var validType = type === 'file' || type === kind(file.name);
                    var extensions = option('file-types').toLowerCase().split(/[;,]/).map(function (ext) { return ext.trim().replace(/^\.?/, '.'); });
                    var extension = file.name.slice(file.name.lastIndexOf('.')).toLowerCase();
                    if (!validType || (option('file-types') && extensions.indexOf(extension) < 0)) { done(options.dictInvalidFileType); }
                    else { done(); }
                }
            };
            ['width', 'height'].forEach(function (dimension) {
                if (option('resize-' + dimension)) { options['resize' + dimension.charAt(0).toUpperCase() + dimension.slice(1)] = Number(option('resize-' + dimension)); }
            });
            if (option('resize-quality')) { options.resizeQuality = Number(option('resize-quality')); }
            if (option('resize-method')) { options.resizeMethod = option('resize-method'); }
            uploader = new Dropzone(dropArea[0], options);
            root.data('previewDropzone', uploader);
            // Dropzone recreates its input after each selection. Set the picker mode before every click.
            root.find('.ipfs-upload-browse')[0].addEventListener('click', function () {
                uploader.hiddenFileInput.multiple = multiple;
            }, true);
            uploader.hiddenFileInput.multiple = multiple;
            uploader.on('addedfile', function (file) {
                var card = $(file.previewElement), url = URL.createObjectURL(file);
                card.data('objectUrl', url).data('uploadFile', file).attr('data-uploading', 'true');
                decorate(card, file.name, url, file.size);
                $('<progress class="ipfs-upload-progress" max="100" value="0" aria-label="Upload progress"></progress>').appendTo(card);
                // The form receives a path only after the temporary upload succeeds.
                $('<input type="hidden" disabled>').attr('name', option('param') + '_path').appendTo(card);
                message.empty();
                refresh();
            });
            uploader.on('uploadprogress', function (file, percent) {
                $(file.previewElement).find('progress').val(percent);
            });
            function failed(file, error) {
                if (file.shadowDuplicate) {
                    // Reject before queuing; keep only the original file and a field-level message.
                    uploader.removeFile(file);
                    message.text('File "' + file.name + '" was not added because a file with this name is already selected.');
                    return;
                }
                var card = $(file.previewElement);
                card.removeAttr('data-uploading').attr('data-upload-error', 'true').find('progress, input').remove();
                var text = typeof error === 'string' ? error : error && error.error ? error.error : 'Upload failed. Please try again.';
                if (typeof text !== 'string') { text = text.message || 'Upload failed. Please try again.'; }
                card.find('.ipfs-upload-error').remove();
                $('<div class="ipfs-upload-error" role="alert"></div>').text(text).appendTo(card);
                refresh();
            }
            uploader.on('success', function (file, response) {
                if (typeof response === 'string') {
                    try { response = JSON.parse(response); } catch (ignore) { failed(file, 'Invalid server response.'); return; }
                }
                if (!response || response.error || !response.path) { failed(file, response && response.error); return; }
                var card = $(file.previewElement);
                // A successful replacement removes the old value; a failed one leaves it intact.
                if (!multiple) { cards.children().not(card).each(function () { remove($(this)); }); }
                card.removeAttr('data-uploading data-upload-error').find('progress').remove();
                card.find('input').prop('disabled', false).val(response.path);
                // Refresh from Joget, including any server-side filename change or image resize.
                release(card);
                decorate(card, response.newFilename || response.filename || file.name, tempUrl(response.path), file.size);
                refresh();
            });
            uploader.on('error', failed);
            uploader.on('removedfile', function (file) { release($(file.previewElement)); refresh(); });
            uploader.on('dragover', function () { dropArea.addClass('ipfs-upload-dragover'); });
            uploader.on('dragleave', function () { dropArea.removeClass('ipfs-upload-dragover'); });
            uploader.on('drop', function () { dropArea.removeClass('ipfs-upload-dragover'); });
            root.find('.ipfs-upload-clear').on('click', function () {
                cards.children().each(function () { remove($(this)); });
                root.find('input[type=file]').val('');
                message.empty();
            });
            if (form.length) {
                function preventInvalidSave(event) {
                    if (cards.children('[data-uploading="true"], [data-upload-error="true"]').length) {
                        event.preventDefault();
                        event.stopImmediatePropagation();
                        message.text('Wait for the upload to finish, or remove the failed upload before saving.');
                        // Joget may already have shown its blocking overlay in a button handler.
                        if (typeof $.unblockUI === 'function') { $.unblockUI({fadeOut: 0}); }
                        message.attr('tabindex', '-1')[0].focus();
                        return false;
                    }
                }
                // Joget starts its loading overlay on click, before the form's submit event.
                form[0].addEventListener('click', function (event) {
                    var button = $(event.target).closest('button, input')[0];
                    if (button && button.form === form[0] && (button.type === 'submit' || button.type === 'image')) {
                        preventInvalidSave(event);
                    }
                }, true);
                form[0].addEventListener('submit', preventInvalidSave, true);
                // jQuery-triggered submissions do not run native capture listeners.
                form.on('submit.shadowUploadPreview', preventInvalidSave);
            }
        });
    };
})(jQuery);
