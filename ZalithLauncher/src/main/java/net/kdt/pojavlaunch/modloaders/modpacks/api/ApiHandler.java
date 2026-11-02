
package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.util.ArrayMap;

import com.google.gson.Gson;
import com.movtery.zalithlauncher.feature.log.Logging;
import com.movtery.zalithlauncher.utils.path.UrlManager;

import net.kdt.pojavlaunch.Tools;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("unused")
public class ApiHandler {
    public final String baseUrl;
    public final Map<String, String> additionalHeaders = new ArrayMap<>();

    public ApiHandler(String url) {
        baseUrl = url;
    }

    public ApiHandler(String url, String apiKey) {
        this(url);
        additionalHeaders.put("x-api-key", apiKey);
    }

    public <T> T get(String endpoint, Class<T> tClass) {
        return getFullUrl(additionalHeaders, baseUrl + "/" + endpoint, tClass);
    }

    public <T> T get(String endpoint, HashMap<String, Object> query, Class<T> tClass) {
        return getFullUrl(additionalHeaders, baseUrl + "/" + endpoint, query, tClass);
    }

    public <T> T post(String endpoint, T body, Class<T> tClass) {
        return postFullUrl(additionalHeaders, baseUrl + "/" + endpoint, body, tClass);
    }

    public <T> T post(String endpoint, HashMap<String, Object> query, T body, Class<T> tClass) {
        return postFullUrl(additionalHeaders, baseUrl + "/" + endpoint, query, body, tClass);
    }

    public static String getRaw(String url) {
        return getRaw(null, url);
    }

    public static String getRaw(Map<String, String> headers, String url) {
        HttpURLConnection conn = null;

        try {
            Logging.d("ApiHandler", "GET " + url);

            conn = UrlManager.createHttpConnection(new URL(url));
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            addHeaders(conn, headers);

            return readResponse(conn);

        } catch (InterruptedIOException e) {
            Logging.d("ApiHandler",
                    "The connection was interrupted or canceled.\n"
                            + Tools.printToString(e));
            return null;

        } catch (Exception e) {
            Logging.e("ApiHandler", Tools.printToString(e));
            throw new RuntimeException("API GET request failed: " + e.getMessage(), e);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static String postRaw(String url, String body) {
        return postRaw(null, url, body);
    }

    public static String postRaw(Map<String, String> headers, String url, String body) {
        HttpURLConnection conn = null;

        try {
            Logging.d("ApiHandler", "POST " + url);

            conn = UrlManager.createHttpConnection(new URL(url));
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            addHeaders(conn, headers);
            conn.setDoOutput(true);

            byte[] input = body.getBytes(StandardCharsets.UTF_8);

            try (OutputStream outputStream = conn.getOutputStream()) {
                outputStream.write(input);
                outputStream.flush();
            }

            return readResponse(conn);

        } catch (InterruptedIOException e) {
            Logging.d("ApiHandler",
                    "The connection was interrupted or canceled.\n"
                            + Tools.printToString(e));
            return null;

        } catch (Exception e) {
            Logging.e("ApiHandler", Tools.printToString(e));
            throw new RuntimeException("API POST request failed: " + e.getMessage(), e);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();

        InputStream stream;

        if (status >= 200 && status < 300) {
            stream = conn.getInputStream();
        } else {
            stream = conn.getErrorStream();
        }

        String response = "";

        if (stream != null) {
            try (InputStream inputStream = new BufferedInputStream(stream)) {
                response = Tools.read(inputStream);
            }
        }

        if (status < 200 || status >= 300) {
            String error = "HTTP " + status + ": " + response;

            Logging.e("ApiHandler", error);

            throw new RuntimeException(error);
        }

        Logging.d("ApiHandler", "HTTP " + status + " response received");

        return response;
    }

    private static void addHeaders(
            HttpURLConnection connection,
            Map<String, String> headers
    ) {
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    private static String parseQueries(HashMap<String, Object> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }

        StringBuilder params = new StringBuilder();

        for (Map.Entry<String, Object> entry : query.entrySet()) {
            params.append(params.length() == 0 ? "?" : "&")
                    .append(urlEncodeUTF8(entry.getKey()))
                    .append("=")
                    .append(urlEncodeUTF8(
                            Objects.toString(entry.getValue(), "")
                    ));
        }

        return params.toString();
    }

    public static <T> T getFullUrl(String url, Class<T> tClass) {
        return getFullUrl(null, url, tClass);
    }

    public static <T> T getFullUrl(
            String url,
            HashMap<String, Object> query,
            Class<T> tClass
    ) {
        return getFullUrl(null, url, query, tClass);
    }

    public static <T> T postFullUrl(String url, T body, Class<T> tClass) {
        return postFullUrl(null, url, body, tClass);
    }

    public static <T> T postFullUrl(
            String url,
            HashMap<String, Object> query,
            T body,
            Class<T> tClass
    ) {
        return postFullUrl(null, url, query, body, tClass);
    }

    public static <T> T getFullUrl(
            Map<String, String> headers,
            String url,
            Class<T> tClass
    ) {
        return new Gson().fromJson(getRaw(headers, url), tClass);
    }

    public static <T> T getFullUrl(
            Map<String, String> headers,
            String url,
            HashMap<String, Object> query,
            Class<T> tClass
    ) {
        return getFullUrl(headers, url + parseQueries(query), tClass);
    }

    public static <T> T postFullUrl(
            Map<String, String> headers,
            String url,
            T body,
            Class<T> tClass
    ) {
        return new Gson().fromJson(
                postRaw(headers, url, body.toString()),
                tClass
        );
    }

    public static <T> T postFullUrl(
            Map<String, String> headers,
            String url,
            HashMap<String, Object> query,
            T body,
            Class<T> tClass
    ) {
        return postFullUrl(
                headers,
                url + parseQueries(query),
                body,
                tClass
        );
    }

    private static String urlEncodeUTF8(String input) {
        try {
            return URLEncoder.encode(input, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 is required", e);
        }
    }
                                              }
        
