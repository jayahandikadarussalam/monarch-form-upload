package org.joget.marketplace;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared upload rules for temporary uploads and form submission. */
final class UploadPolicy {
    static final String IMAGE_EXTENSIONS = ".jpg;.jpeg;.png;.gif;.webp;.bmp;.svg;.avif;.tif;.tiff;.ico;.heic;.heif";
    static final String VIDEO_EXTENSIONS = ".mp4;.webm;.ogv;.ogg;.mov;.m4v;.avi;.mkv;.mpeg;.mpg;.3gp;.wmv";

    private UploadPolicy() { }

    static String type(String value) {
        return "image".equals(value) || "video".equals(value) ? value : "file";
    }

    static boolean ipfsEnabled(Map properties) {
        if (properties == null) {
            return false;
        }
        if (properties.containsKey("enableIpfs")) {
            return "true".equals(String.valueOf(properties.get("enableIpfs")));
        }
        // Existing forms predate the checkbox: retain their configured IPFS behavior.
        Object key = properties.get("blockfrostIpfsProjectKey");
        return key != null && !key.toString().trim().isEmpty();
    }

    static boolean allows(String filename, String uploadType, String extensions) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot).toLowerCase(Locale.ROOT);
        String normalizedType = type(uploadType);
        if (!"file".equals(normalizedType)) {
            String allowed = "image".equals(normalizedType) ? IMAGE_EXTENSIONS : VIDEO_EXTENSIONS;
            if (!new HashSet<>(Arrays.asList(allowed.split(";"))).contains(extension)) {
                return false;
            }
        }
        if (extensions == null || extensions.trim().isEmpty()) {
            return true;
        }
        Set<String> allowed = new HashSet<>();
        for (String item : extensions.toLowerCase(Locale.ROOT).split("[;,]")) {
            String ext = item.trim();
            if (!ext.isEmpty()) {
                allowed.add(ext.startsWith(".") ? ext : "." + ext);
            }
        }
        return allowed.contains(extension);
    }

    static String accept(String uploadType, String extensions) {
        String candidates = "image".equals(type(uploadType)) ? IMAGE_EXTENSIONS
                : "video".equals(type(uploadType)) ? VIDEO_EXTENSIONS : extensions;
        if (candidates == null || candidates.trim().isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String item : candidates.split("[;,]")) {
            String ext = item.trim();
            if (!ext.startsWith(".")) {
                ext = "." + ext;
            }
            if (allows("file" + ext, uploadType, extensions)) {
                if (result.length() > 0) {
                    result.append(',');
                }
                result.append(ext);
            }
        }
        return result.toString();
    }
}
