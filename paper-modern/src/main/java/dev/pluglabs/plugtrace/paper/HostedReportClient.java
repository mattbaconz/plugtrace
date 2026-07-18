package dev.pluglabs.plugtrace.paper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Explicit hosted-report upload: gzip → AES-GCM → POST ciphertext.
 * Layout of ciphertext blob: 12-byte IV || ciphertext+tag.
 * Decryption key is base64url and belongs only in the viewer URL fragment (#k=…).
 */
final class HostedReportClient {
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private HostedReportClient() {
    }

    record UploadResult(
            String id,
            String expiresAt,
            String deleteToken,
            String shareUrl,
            String viewerPath
    ) {
    }

    static UploadResult upload(
            String uploadUrl,
            String viewerUrl,
            String jsonReport,
            String schemaVersion,
            int ttlDays
    ) throws IOException, GeneralSecurityException {
        byte[] gzipped = gzip(jsonReport.getBytes(StandardCharsets.UTF_8));
        byte[] keyBytes = new byte[32];
        RANDOM.nextBytes(keyBytes);
        byte[] iv = new byte[GCM_IV_LENGTH];
        RANDOM.nextBytes(iv);

        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(gzipped);

        byte[] blob = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, blob, 0, iv.length);
        System.arraycopy(encrypted, 0, blob, iv.length, encrypted.length);
        if (blob.length > MAX_BODY_BYTES) {
            throw new IOException("Encrypted report exceeds " + MAX_BODY_BYTES + " bytes");
        }

        String keyB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
        String endpoint = ensureTrailingSlash(uploadUrl) + "api/v1/reports";

        String bodyJson = "{"
                + "\"ciphertextBase64\":\"" + Base64.getEncoder().encodeToString(blob) + "\","
                + "\"contentType\":\"application/vnd.plugtrace.report+aesgcm\","
                + "\"schemaVersion\":\"" + escapeJson(schemaVersion) + "\","
                + "\"ttlDays\":" + ttlDays
                + "}";

        HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "plugtrace-plugin");
        byte[] body = bodyJson.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }

        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String response = stream == null ? "" : new String(readAll(stream), StandardCharsets.UTF_8);
        if (code < 200 || code >= 300) {
            throw new IOException("Upload failed HTTP " + code + ": " + truncate(response, 240));
        }

        String id = extractJsonString(response, "id");
        String expiresAt = extractJsonString(response, "expiresAt");
        String deleteToken = extractJsonString(response, "deleteToken");
        String viewerPath = extractJsonString(response, "viewerPath");
        if (id == null || id.isBlank()) {
            throw new IOException("Upload response missing id");
        }
        if (viewerPath == null || viewerPath.isBlank()) {
            viewerPath = "/r/" + id;
        }
        String base = stripTrailingSlash(viewerUrl);
        String shareUrl = base + viewerPath + "#k=" + keyB64;
        return new UploadResult(id, expiresAt, deleteToken, shareUrl, viewerPath);
    }

    private static byte[] gzip(byte[] input) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(input);
        }
        return bos.toByteArray();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    static String sha256Hex(String value) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String ensureTrailingSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    private static String stripTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return null;
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        int end = start + 1;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '"' && json.charAt(end - 1) != '\\') {
                break;
            }
            end++;
        }
        if (end >= json.length()) {
            return null;
        }
        return json.substring(start + 1, end);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
