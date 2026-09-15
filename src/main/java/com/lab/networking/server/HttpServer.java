package com.lab.networking.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class HttpServer {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
        "html", "text/html",
        "css", "text/css",
        "js", "text/javascript",
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg",
        "png", "image/png"
    );

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Ready to receive on port " + port + "...");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            handleConnection(clientSocket);
        }
    }

    private static void handleConnection(Socket clientSocket) {
        try (
            clientSocket;
            BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream())
            );
            OutputStream out = clientSocket.getOutputStream()
        ) {
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendError(out, 400, "Bad Request");
                return;
            }

            String method = parts[0];
            if (!method.equals("GET")) {
                sendError(out, 405, "Method Not Allowed");
                return;
            }

            String rawPath = parts[1];
            String path = rawPath;
            String queryString = "";

            int qIndex = rawPath.indexOf('?');
            if (qIndex != -1) {
                path = rawPath.substring(0, qIndex);
                queryString = rawPath.substring(qIndex + 1);
            }

            // --- Servicios hardcoded (Sección 4) ---
            if (path.equals("/api/greeting")) {
                handleGreeting(out, queryString);
                return;
            }
            if (path.equals("/api/square")) {
                handleSquare(out, queryString);
                return;
            }
            if (path.equals("/api/time")) {
                handleTime(out);
                return;
            }
            if (path.equals("/api/health")) {
                handleHealth(out);
                return;
            }

            // --- Recursos estáticos (Sección 3) ---
            if (path.contains("..")) {
                sendError(out, 400, "Bad Request");
                return;
            }

            if (path.equals("/")) {
                path = "/index.html";
            }

            String resourcePath = "/public" + path;
            InputStream resource = HttpServer.class.getResourceAsStream(resourcePath);
            if (resource == null) {
                sendError(out, 404, "Not Found");
                return;
            }

            String contentType = resolveContentType(path);
            byte[] content = resource.readAllBytes();
            resource.close();

            String responseHeaders =
                "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + content.length + "\r\n"
                + "\r\n";

            out.write(responseHeaders.getBytes(StandardCharsets.UTF_8));
            out.write(content);

        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
        }
    }

    // ---------- Servicios ----------

    private static void handleGreeting(OutputStream out, String queryString) throws IOException {
        Map<String, String> params = parseQuery(queryString);
        String name = params.get("name");

        if (name == null || name.isBlank()) {
            sendError(out, 400, "Bad Request: missing 'name' parameter");
            return;
        }

        String json = "{\"greeting\":\"Hello, " + escapeJson(name) + "!\"}";
        sendJson(out, 200, "OK", json);
    }

    private static void handleSquare(OutputStream out, String queryString) throws IOException {
        Map<String, String> params = parseQuery(queryString);
        String valueStr = params.get("value");
        double value;

        try {
            value = Double.parseDouble(valueStr);
        } catch (Exception e) {
            sendError(out, 400, "Bad Request: invalid or missing 'value' parameter");
            return;
        }

        double square = value * value;
        String json = "{\"input\":" + value + ",\"square\":" + square + "}";
        sendJson(out, 200, "OK", json);
    }

    private static void handleTime(OutputStream out) throws IOException {
        String json = "{\"serverTime\":\"" + LocalDateTime.now() + "\"}";
        sendJson(out, 200, "OK", json);
    }

    private static void handleHealth(OutputStream out) throws IOException {
        sendJson(out, 200, "OK", "{\"status\":\"UP\"}");
    }

    // ---------- Utilidades ----------

    private static Map<String, String> parseQuery(String queryString) {
        Map<String, String> params = new HashMap<>();
        if (queryString == null || queryString.isBlank()) {
            return params;
        }

        for (String pair : queryString.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void sendJson(OutputStream out, int status, String reason, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
            + "Content-Type: application/json\r\n"
            + "Content-Length: " + body.length + "\r\n"
            + "\r\n";

        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(body);
    }

    private static void sendError(OutputStream out, int statusCode, String statusMessage) throws IOException {
        byte[] body = statusMessage.getBytes(StandardCharsets.UTF_8);
        String response = "HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n"
            + "Content-Type: text/plain\r\n"
            + "Content-Length: " + body.length + "\r\n"
            + "\r\n";

        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.write(body);
    }

    private static String resolveContentType(String path) {
        int dotIndex = path.lastIndexOf(".");
        if (dotIndex == -1) {
            return "application/octet-stream";
        }
        String extension = path.substring(dotIndex + 1);
        return CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }
}