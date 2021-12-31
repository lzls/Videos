/*
 * Created on 2020-10-14 10:28:29 AM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.common.Configs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Set;

/**
 * @author 刘振林
 */
public class HttpRequester {
    private HttpRequester() {
    }

    private static final String CHARSET = Configs.DEFAULT_CHARSET;

    public static final String PARAM_CONTENT_TYPE_JSON = "application/Json; charset=" + CHARSET;

    public static final int RESPONSE_OK = 200;
    public static final int RESPONSE_OK_PARTIAL_CONTENT = 206;

    public static final int TIMEOUT_INFINITE = 0;

    public static void doGet(
            @NonNull String url,
            @Nullable Map<String, String> params,
            @Nullable long[] dataRange,
            @NonNull Callback callback) throws IOException {
        doGet(url, params, dataRange, callback, TIMEOUT_INFINITE, TIMEOUT_INFINITE);
    }

    public static void doGet(
            @NonNull String url,
            @Nullable Map<String, String> params,
            @Nullable long[] dataRange,
            @NonNull Callback callback,
            int readTimeout,
            int connectionTimeout) throws IOException {
        if (dataRange != null && dataRange.length != 2) {
            throw new IllegalArgumentException("dataRange must be null or an array of two integers");
        }
        HttpURLConnection conn = null;
        try {
            StringBuilder urlBuilder = new StringBuilder(url);
            if (params != null && !params.isEmpty()) {
                int count = 0;
                Set<Map.Entry<String, String>> entries = params.entrySet();
                for (Map.Entry<String, String> param : entries) {
                    urlBuilder.append(count == 0 ? "?" : "&")
                            .append(URLEncoder.encode(param.getKey(), CHARSET))
                            .append("=")
                            .append(URLEncoder.encode(param.getValue(), CHARSET));
                    count++;
                }
            }
            conn = (HttpURLConnection) new URL(urlBuilder.toString()).openConnection();
            conn.setRequestMethod("GET");
            if (dataRange != null) {
                conn.setRequestProperty("Range", "bytes=" + dataRange[0] + "-" + dataRange[1]);
            }
            conn.setConnectTimeout(connectionTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setUseCaches(true);
            conn.setDoOutput(false);
            callback.onResult(url, conn.getResponseCode(), conn.getInputStream());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static void doPost(
            @NonNull String url,
            @Nullable String params,
            @NonNull Callback callback) throws IOException {
        doPost(url, params, PARAM_CONTENT_TYPE_JSON, callback, TIMEOUT_INFINITE, TIMEOUT_INFINITE);
    }

    public static void doPost(
            @NonNull String url,
            @Nullable String params,
            @NonNull String paramsContentType,
            @NonNull Callback callback) throws IOException {
        doPost(url, params, paramsContentType, callback, TIMEOUT_INFINITE, TIMEOUT_INFINITE);
    }

    public static void doPost(
            @NonNull String url,
            @Nullable String params,
            @NonNull String paramsContentType,
            @NonNull Callback callback,
            int readTimeout,
            int connectionTimeout) throws IOException {
        HttpURLConnection conn = null;
        OutputStream out = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", paramsContentType);
            conn.setConnectTimeout(connectionTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            if (params != null && params.length() > 0) {
                out = conn.getOutputStream();
                out.write(params.getBytes());
                out.flush();
            }
            callback.onResult(url, conn.getResponseCode(), conn.getInputStream());
        } finally {
            IOUtils.closeSilently(out);
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public interface Callback {
        void onResult(@NonNull String url, int responseCode, @NonNull InputStream dataStream)
                throws IOException;
    }
}
