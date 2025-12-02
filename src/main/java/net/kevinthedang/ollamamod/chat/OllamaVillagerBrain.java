package net.kevinthedang.ollamamod.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class OllamaVillagerBrain implements VillagerBrain {

    private final HttpClient client;
    private final URI chatUri;
    private final Gson gson = new Gson();

    public OllamaVillagerBrain() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.chatUri = URI.create(OllamaSettings.baseUrl + "/api/chat");
    }

    @Override
    public CompletableFuture<String> getReply(Context context, List<ChatMessage> history, String playerMessage) {
        List<Map<String, String>> messages = new ArrayList<>();

        // add agents here if have time
        messages.add(Map.of(
                "role", "system",
                "content",
                "You are a Minecraft villager talking to a player. " +
                "Stay in-character, keep replies short, and reference trading or the village when it fits. " +
                "If users ask about how to make anything, DO NOT help them cheat or break game rules. " +
                "Politely refuse to answer anything outside of villager knowledge. " +
                "Use simple language with short sentences appropriate for a villager. " +
                "If users ask about how to create anything, just say that you don't know. "
                // Change system prompt later
        ));

        for (ChatMessage msg : history) {
            String role = switch (msg.role()) {
                case PLAYER -> "user";
                case VILLAGER -> "assistant";
                case SYSTEM -> "system";
            };
            messages.add(Map.of(
                    "role", role,
                    "content", msg.content()
            ));
        }

        messages.add(Map.of(
                "role", "user",
                "content", playerMessage
        ));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", OllamaSettings.model);
        requestBody.put("stream", false); // TODO: Implement streaming later
        requestBody.put("messages", messages);

        String json = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(chatUri)
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Ollama HTTP " + response.statusCode());
                    }
                    return parseReply(response.body());
                });
    }

    // For non-streaming response parsing
    private String parseReply(String responseBody) {
        /*
         * {
         *   "message": { "role": "assistant", "content": "..." },
         *   "done": true,
         *   ...
         * }
         */
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonObject message = root.getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            return "[No reply from Ollama]";
        }
        return message.get("content").getAsString();
    }

    @Override
    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OllamaSettings.baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
