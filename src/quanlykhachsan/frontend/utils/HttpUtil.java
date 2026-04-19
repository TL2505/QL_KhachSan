package quanlykhachsan.frontend.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HttpUtil {

    private static final String BASE_URL;

    static {
        String url = "http://localhost:8080/api"; // mặc định
        try {
            java.util.Properties props = new java.util.Properties();
            java.io.File configFile = new java.io.File("config.properties");
            if (configFile.exists()) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(configFile)) {
                    props.load(fis);
                    String loaded = props.getProperty("server.url");
                    if (loaded != null && !loaded.trim().isEmpty()) {
                        url = loaded.trim();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[HttpUtil] Không đọc được config.properties, dùng localhost: " + e.getMessage());
        }
        BASE_URL = url;
        System.out.println("[HttpUtil] Server URL: " + BASE_URL);
    }

    public static String sendGet(String endpoint) throws Exception {
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000); // 10 seconds
        conn.setReadTimeout(10000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-User-Role", String.valueOf(SessionManagerUtil.getCurrentRoleId()));
        conn.setRequestProperty("ngrok-skip-browser-warning", "true");

        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String output;
        while ((output = br.readLine()) != null) sb.append(output);
        conn.disconnect();
        return sb.toString();
    }

    public static String sendPost(String endpoint, String jsonBody) throws Exception {
        return sendWithBody("POST", endpoint, jsonBody);
    }

    public static String sendPut(String endpoint, String jsonBody) throws Exception {
        return sendWithBody("PUT", endpoint, jsonBody);
    }

    public static String sendDelete(String endpoint) throws Exception {
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-User-Role", String.valueOf(SessionManagerUtil.getCurrentRoleId()));
        conn.setRequestProperty("ngrok-skip-browser-warning", "true");

        int code = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
            code >= 200 && code <= 299 ? conn.getInputStream()
                : (conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()),
            StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        conn.disconnect();
        return sb.toString();
    }

    private static String sendWithBody(String method, String endpoint, String jsonBody) throws Exception {
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-User-Role", String.valueOf(SessionManagerUtil.getCurrentRoleId()));
        conn.setRequestProperty("ngrok-skip-browser-warning", "true");

        if (jsonBody != null && !jsonBody.isEmpty()) {
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        }

        int responseCode = conn.getResponseCode();
        BufferedReader br;
        if (responseCode >= 200 && responseCode <= 299) {
            br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            br = new BufferedReader(new InputStreamReader(
                conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8));
        }

        StringBuilder sb = new StringBuilder();
        String output;
        while ((output = br.readLine()) != null) sb.append(output);
        conn.disconnect();
        return sb.toString();
    }
}
