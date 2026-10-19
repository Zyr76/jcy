package com.jcy.client.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public final class Http {
    private Http() {
    }

    public static byte[] getBytes(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = open(url, "GET", headers);
        try {
            return readResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    public static byte[] postBytes(String url, byte[] body, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = open(url, "POST", headers);
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }
        try {
            return readResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String method, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("User-Agent", "JcyClient/1.0");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return connection;
    }

    private static byte[] readResponse(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        InputStream in = code >= 200 && code < 400
                ? connection.getInputStream()
                : connection.getErrorStream();
        byte[] body = in == null ? new byte[0] : readAll(in);
        if (code < 200 || code >= 300) {
            String text = new String(body, "UTF-8");
            if (text.length() > 300) {
                text = text.substring(0, 300);
            }
            throw new IOException("HTTP " + code + " " + text);
        }
        return body;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}
