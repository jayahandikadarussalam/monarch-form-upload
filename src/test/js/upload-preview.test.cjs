const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {JSDOM} = require('jsdom');
const jquery = require('jquery');
const dropzoneScript = process.env.DROPZONE_SCRIPT || require.resolve('dropzone/dist/dropzone.js');
const previewScript = path.resolve(__dirname, '../../main/resources/resources/js/upload-preview.js');

function fixture({temporary = false, readonly = false} = {}) {
    const dom = new JSDOM('<!doctype html><html><body><form><div id="section-actions"><button type="submit"><span>Save</span></button></div></form></body></html>', {
        url: 'http://localhost/jw/', runScripts: 'outside-only', pretendToBeVisual: true
    });
    const window = dom.window;
    const $ = jquery(window);
    window.jQuery = window.$ = $;
    let objectId = 0;
    const revoked = [];
    window.URL.createObjectURL = () => 'blob:test-' + (++objectId);
    window.URL.revokeObjectURL = url => revoked.push(url);
    window.eval(fs.readFileSync(dropzoneScript, 'utf8'));
    window.Dropzone.autoDiscover = false;
    window.eval(fs.readFileSync(previewScript, 'utf8'));
    const form = $('form');
    const root = $('<div class="ipfs-upload" data-url="/service?_nonce=test" data-param="attachment" data-readonly="false" data-multiple="true" data-upload-type="file" data-accept=""><div class="ipfs-upload-dropzone"><ul class="ipfs-upload-cards"><li class="ipfs-upload-card" data-filename="saved.png" data-saved-url="/download/saved.png"><input type="hidden" name="attachment_path" value="saved.png"></li></ul><div class="ipfs-upload-empty"></div></div><span class="ipfs-upload-caption"></span><button type="button" class="ipfs-upload-clear">Remove</button><button type="button" class="ipfs-upload-browse">Browse</button><div class="ipfs-upload-message" role="alert"></div></div>').prependTo(form);
    // Register Joget's loader before initializing the plugin, as in a rendered form.
    let loading = false, submitted = 0;
    $('#section-actions button').on('click', () => { loading = true; });
    $.unblockUI = () => { loading = false; };
    form[0].submit = () => { submitted++; };
    root.attr('data-readonly', readonly ? 'true' : 'false');
    if (temporary) {
        root.find('.ipfs-upload-card').removeAttr('data-saved-url').attr('data-temp-path', 'temp/saved.png');
    }
    root.ipfsUploadPreview();
    const dz = root.data('previewDropzone');
    if (dz) { dz.options.autoProcessQueue = false; }
    return {
        window, $, root, form, dz, revoked,
        loading: () => loading,
        submitted: () => submitted,
        setLoading: () => { loading = true; },
        file: name => new window.File(['test upload'], name, {type: 'image/png'}),
        clickSave: () => {
            const event = new window.MouseEvent('click', {bubbles: true, cancelable: true});
            $('#section-actions button span')[0].dispatchEvent(event);
            return event;
        },
        submit: () => {
            const event = new window.Event('submit', {bubbles: true, cancelable: true});
            form[0].dispatchEvent(event);
            return event;
        },
        close: () => { if (dz) { dz.destroy(); } dom.window.close(); }
    };
}

test('duplicate of a saved file never queues or blocks valid form values', () => {
    const f = fixture();
    try {
        const duplicate = f.file('SAVED.PNG');
        f.dz.addFile(duplicate);
        assert.equal(duplicate.accepted, false);
        assert.equal(f.dz.files.includes(duplicate), false);
        assert.equal(f.dz.getQueuedFiles().length, 0);
        assert.equal(f.root.find('.ipfs-upload-card').length, 1);
        assert.equal(f.root.find('[data-upload-error], [data-uploading]').length, 0);
        assert.equal(f.root.find('input[name="attachment_path"]').val(), 'saved.png');
        assert.match(f.root.find('.ipfs-upload-message').text(), /was not added/);
        assert.equal(f.root.find('.ipfs-upload-caption').text(), '1 file selected');
        assert.equal(f.submit().defaultPrevented, false);
        assert.equal(f.loading(), false);
        assert.equal(f.revoked.length, 1);
    } finally { f.close(); }
});

test('new upload has Preview but no Open before or after temporary upload succeeds', () => {
    const f = fixture();
    try {
        const upload = f.file('new.png');
        f.dz.addFile(upload);
        const card = f.$(upload.previewElement);
        assert.equal(card.find('.ipfs-upload-actions a').length, 0);
        assert.equal(card.find('.ipfs-upload-zoom').length, 1);
        upload.status = f.window.Dropzone.SUCCESS;
        f.dz.emit('success', upload, {path: 'temp/new.png', newFilename: 'new.png'});
        assert.equal(card.find('.ipfs-upload-actions a').length, 0);
        assert.equal(card.find('.ipfs-upload-zoom').length, 1);
        assert.equal(card.find('input:enabled').val(), 'temp/new.png');
        assert.equal(f.root.find('[data-saved-url] .ipfs-upload-actions a').attr('href'), '/download/saved.png');
    } finally { f.close(); }
});

test('temporary previews restored after validation never expose Open, including readonly', () => {
    for (const readonly of [false, true]) {
        const f = fixture({temporary: true, readonly});
        try {
            assert.equal(f.root.find('.ipfs-upload-actions a').length, 0);
            assert.equal(f.root.find('.ipfs-upload-zoom').length, 1);
        } finally { f.close(); }
    }
});

test('saved records expose Open and unsaved preview dialogs omit download links', () => {
    for (const temporary of [false, true]) {
        const f = fixture({temporary, readonly: true});
        try {
            f.window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
            assert.equal(f.root.find('.ipfs-upload-actions a').length, temporary ? 0 : 1);
            f.root.find('.ipfs-upload-zoom').trigger('click');
            const dialog = f.$('.ipfs-upload-dialog');
            assert.equal(dialog.length, 1);
            assert.equal(dialog.find('img').length, 1);
            assert.equal(dialog.find('a').length, temporary ? 0 : 1);
            if (!temporary) { assert.equal(dialog.find('a').attr('href'), '/download/saved.png'); }
        } finally { f.close(); }
    }
});

test('duplicate within a new batch keeps the first queued upload only', () => {
    const f = fixture();
    try {
        const first = f.file('new.png'), duplicate = f.file('NEW.PNG');
        f.dz.addFile(first); f.dz.addFile(duplicate);
        assert.equal(f.dz.getQueuedFiles().length, 1);
        assert.equal(f.dz.getQueuedFiles()[0], first);
        assert.equal(f.root.find('.ipfs-upload-card').length, 2);
        assert.equal(f.root.find('[data-upload-error]').length, 0);
        assert.equal(f.clickSave().defaultPrevented, true);
        assert.equal(f.loading(), false);
        first.status = f.window.Dropzone.SUCCESS;
        f.dz.emit('success', first, {path: 'temp/new.png', newFilename: 'new.png'});
        assert.equal(f.submit().defaultPrevented, false);
        assert.equal(f.root.find('input[name="attachment_path"]:enabled').length, 2);
    } finally { f.close(); }
});

test('Save is intercepted before Joget click loader while upload is pending', () => {
    const f = fixture();
    try {
        f.dz.addFile(f.file('pending.png'));
        assert.equal(f.clickSave().defaultPrevented, true);
        assert.equal(f.loading(), false);
        assert.match(f.root.find('.ipfs-upload-message').text(), /Wait for the upload/);
    } finally { f.close(); }
});

test('cancelled submit clears an overlay already shown by the host', () => {
    const f = fixture();
    try {
        f.dz.addFile(f.file('pending.png'));
        f.setLoading();
        assert.equal(f.submit().defaultPrevented, true);
        assert.equal(f.loading(), false);
    } finally { f.close(); }
});

test('network failure blocks Save without leaving a spinner and removal allows retry', () => {
    const f = fixture();
    try {
        const failed = f.file('failed.png');
        f.dz.addFile(failed);
        failed.status = f.window.Dropzone.ERROR;
        f.dz.emit('error', failed, 'Network failed');
        assert.equal(f.clickSave().defaultPrevented, true);
        assert.equal(f.loading(), false);
        f.root.find('[data-upload-error] .ipfs-upload-remove').trigger('click');
        assert.equal(f.submit().defaultPrevented, false);
    } finally { f.close(); }
});

test('jQuery-triggered submit is also prevented without locking the form', () => {
    const f = fixture();
    try {
        f.dz.addFile(f.file('pending.png'));
        f.setLoading();
        const event = f.$.Event('submit');
        f.form.trigger(event);
        assert.equal(event.isDefaultPrevented(), true);
        assert.equal(f.submitted(), 0);
        assert.equal(f.loading(), false);
    } finally { f.close(); }
});
