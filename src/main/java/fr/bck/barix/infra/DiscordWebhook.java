package fr.bck.barix.infra;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public final class DiscordWebhook {
    private final URI uri;
    private final String username;
    private final HttpClient http;

    public DiscordWebhook(String url, String username) {
        this.uri = URI.create(url);
        this.username = username == null || username.isBlank() ? "Barix" : username;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public CompletableFuture<Void> sendContent(String content) {
        String json = "{\"username\":\"" + esc(username) + "\",\"content\":\"" + esc(content) + "\"}";
        return post(json);
    }

    public CompletableFuture<Void> sendEmbed(String title, String description, int color) {
        String json = "{" + "\"username\":\"" + esc(username) + "\"," + "\"embeds\":[{" + "\"title\":\"" + esc(title) + "\"," + "\"description\":\"" + esc(description) + "\"," + "\"color\":" + color + "," + "\"timestamp\":\"" + Instant.now().toString() + "\"" + "}]" + "}";
        return post(json);
    }

    private CompletableFuture<Void> post(String json) {
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json; charset=utf-8").POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();

        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenCompose(resp -> {
            int code = resp.statusCode();
            if (code == 204 || code == 200) return CompletableFuture.completedFuture(null);
            if (code == 429) {
                String retry = resp.headers().firstValue("Retry-After").orElse("1");
                long delayMs = Math.max(1L, parseLongSafe(retry)) * 1000L;
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                }
                return http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).thenAccept(r2 -> {
                });
            }
            return CompletableFuture.failedFuture(new RuntimeException("Discord webhook HTTP " + code));
        }).exceptionally(ex -> null);
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(' ');
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    private static long parseLongSafe(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 1L;
        }
    }
}
