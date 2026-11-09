# Shadow File Upload

This plugin works just like the default bundled File Upload form element in Joget, but with the added ability to handle uploads and file pinning with IPFS.

The plugin's base implementation can be found on the Joget Community Github page [here](https://github.com/jogetworkflow/jw-community/blob/7.0-SNAPSHOT/wflow-core/src/main/java/org/joget/apps/form/lib/FileUpload.java).

This plugin integrates with [Blockfrost](https://github.com/blockfrost/blockfrost-java) for IPFS functionality.

## Upload types and previews

In Form Builder, configure **Upload Type** as **File (all types)**, **Image**, or
**Video**. Image/video categories are checked both by the browser and by the
upload service. The existing **File Type** extension list can further restrict
the category (for example, `.jpg;.png`). Browser support determines which image
and video formats can be displayed or played.

The uploader shows a preview card immediately after selection, including the
filename, size, removal and enlarged-preview actions. Files upload automatically
to Joget temporary storage; submit the form to save them. The same cards are
shown when reopening saved records, in readonly forms, and after a validation
error. Images and videos have inline previews, PDFs have an enlarged viewer,
and text files up to 1 MiB have a text viewer. Other files show a file card with
an open/download link after the form has been saved. Open/download actions are
hidden for newly selected and temporary files, including inside the preview
dialog; previews remain available before saving. A failed replacement retains the previous saved file.
Finish an upload or remove its error card before saving the form.

**Enable IPFS** is unchecked for new fields. When unchecked, the Blockfrost key
and CID-field settings are hidden, files are stored by Joget, and IPFS upload,
pinning and unpinning are disabled. Check it to reveal and require the IPFS
settings. Existing fields without the checkbox property retain IPFS behavior
when a Blockfrost key is configured; explicitly uncheck the option and save the
field to switch to local storage. Disabling IPFS does not unpin existing content.

**Multiple Upload** is unchecked by default. Check it to select several files at
once or add more files to an existing record. Each file keeps its own preview
and removal action; Remove in the toolbar clears the whole selection. Filenames
must be unique within a field to prevent one stored file from overwriting another.
Duplicate selections are rejected before upload and shown as a field message;
they do not create an error card or prevent saving the valid selected files.
Save is blocked while other uploads are pending or have failed, before Joget
starts its loading overlay.
Leave it unchecked for single-file replacement behavior.

When IPFS is enabled, the CID field contains a semicolon-separated list in the
same order as the stored filenames. Adding files preserves existing CIDs;
removing files unpins only CIDs that are no longer referenced by this field.
Existing records containing a single CID remain supported.

Build and run the Java tests with `mvn package` (Java 11 or newer). Install the
resulting `target/shadow-file-upload-7.0.8.jar` in Joget Manage Plugins, then check
the field in a form with the desired upload type and IPFS setting.

The JavaScript regression tests require `jquery`, `jsdom` and Dropzone 5. Run
`node --test src/test/js/upload-preview.test.cjs` with those modules available.
To use Joget's own Dropzone script, set `DROPZONE_SCRIPT` to its full path;
`NODE_PATH` can point to a separate test dependencies directory.

Do see [documentation](https://dev.joget.org/community/display/marketplace/IPFS+File+Upload+Form+Element).

# Getting Help

JogetOSS is a community-led team for open source software related to the [Joget](https://www.joget.org) no-code/low-code application platform.
Projects under JogetOSS are community-driven and community-supported.
To obtain support, ask questions, get answers and help others, please participate in the [Community Q&A](https://answers.joget.org/).

# Contributing

This project welcomes contributions and suggestions, please open an issue or create a pull request.

Please note that all interactions fall under our [Code of Conduct](https://github.com/jogetoss/repo-template/blob/main/CODE_OF_CONDUCT.md).

# Licensing

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

NOTE: This software may depend on other packages that may be licensed under different open source licenses.
