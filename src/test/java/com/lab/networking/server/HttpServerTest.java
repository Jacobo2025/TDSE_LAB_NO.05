package com.lab.networking.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HttpServerTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        Thread serverThread = new Thread(() -> {
            try {
                HttpServer.main(new String[] {String.valueOf(port)});
            } catch (IOException e) {
                throw new RuntimeException("Server error", e);
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(300);
    }

    @Test
    void shouldServeHomePageWithHtmlContentType() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/"))
            .GET()
            .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(response.body().contains("My Web Site"));
    }

    @Test
    void shouldServeJavascriptAndImageResources() throws Exception {
        HttpRequest jsRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/app.js"))
            .GET()
            .build();

        HttpResponse<String> jsResponse = CLIENT.send(jsRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, jsResponse.statusCode());
        assertTrue(jsResponse.headers().firstValue("Content-Type").orElse("").contains("javascript"));

        HttpRequest imgRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/images/ballet.jpg"))
            .GET()
            .build();

        HttpResponse<byte[]> imgResponse = CLIENT.send(imgRequest, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, imgResponse.statusCode());
        assertTrue(imgResponse.headers().firstValue("Content-Type").orElse("").contains("image/jpeg"));
        assertTrue(imgResponse.body().length > 0);
    }

    @Test
    void shouldRespondJsonForGreetingService() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/greeting?name=Ana"))
            .GET()
            .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
        assertTrue(response.body().contains("Hello, Ana!"));
    }

    @Test
    void shouldRejectMissingNameAndInvalidSquareInput() throws Exception {
        HttpRequest missingName = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/greeting"))
            .GET()
            .build();

        HttpResponse<String> missingNameResponse = CLIENT.send(missingName, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, missingNameResponse.statusCode());

        HttpRequest invalidSquare = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/square?value=abc"))
            .GET()
            .build();

        HttpResponse<String> invalidSquareResponse = CLIENT.send(invalidSquare, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, invalidSquareResponse.statusCode());
    }

    @Test
    void shouldReturnHealthAndTime() throws Exception {
        HttpRequest healthRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/health"))
            .GET()
            .build();

        HttpResponse<String> healthResponse = CLIENT.send(healthRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, healthResponse.statusCode());
        assertTrue(healthResponse.body().contains("UP"));

        HttpRequest timeRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/time"))
            .GET()
            .build();

        HttpResponse<String> timeResponse = CLIENT.send(timeRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, timeResponse.statusCode());
        assertTrue(timeResponse.body().contains("serverTime"));
    }

    @Test
    void shouldRejectUnsupportedMethodAndMissingResources() throws Exception {
        HttpRequest postRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/"))
            .method("POST", HttpRequest.BodyPublishers.noBody())
            .build();

        HttpResponse<String> postResponse = CLIENT.send(postRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, postResponse.statusCode());

        HttpRequest missingRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/missing-file.txt"))
            .GET()
            .build();

        HttpResponse<String> missingResponse = CLIENT.send(missingRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, missingResponse.statusCode());
    }

    @Test
    void shouldRejectTraversalAttempt() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/../README.md"))
            .GET()
            .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
    }
}
