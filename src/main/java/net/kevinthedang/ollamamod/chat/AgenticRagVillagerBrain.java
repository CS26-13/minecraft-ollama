package net.kevinthedang.ollamamod.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kevinthedang.ollamamod.OllamaMod;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Agentic (router-driven) context + prompt composer, then stream the final answer.
 *
 * This class is intentionally "M3-safe": it always injects >=3 world facts and keeps
 * the existing UI + streaming flow unchanged.
 */
public class AgenticRagVillagerBrain implements VillagerBrain {

    private final HttpClient client;
    private final URI chatUri;
    private final Gson gson = new Gson();

    // M3 components (rule-based, cheap, deterministic)
    private final RouterPolicy router;
    private final WorldContextTool worldContextTool;
    private final PromptComposer promptComposer;

    public AgenticRagVillagerBrain() {
        this(new RuleBasedRouterPolicy(), new BasicWorldContextTool(), new PromptComposerV1());
    }

    public AgenticRagVillagerBrain(RouterPolicy router, WorldContextTool worldContextTool, PromptComposer promptComposer) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.chatUri = URI.create(OllamaSettings.baseUrl + "/api/chat");

        this.router = router;
        this.worldContextTool = worldContextTool;
        this.promptComposer = promptComposer;
    }

    @Override
    public CompletableFuture<String> getReply(Context context, List<ChatMessage> history, String playerMessage) {
        RoutePlan plan = router.plan(context, history, playerMessage);
        WorldFactBundle worldFacts = plan.useWorld() ? worldContextTool.collect(context, history, playerMessage)
                : WorldFactBundle.empty();

        List<Map<String, String>> messages = promptComposer.buildMessages(
                context,
                history,
                playerMessage,
                worldFacts
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", OllamaSettings.model);
        requestBody.put("stream", false);
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
                        throw new RuntimeException("Ollama HTTP " + response.statusCode() + ": " + response.body());
                    }
                    return parseReply(response.body());
                });
    }

    @Override
    public void getReplyStreaming(Context context, List<ChatMessage> history, String playerMessage, StreamCallbacks callbacks) {
        // M3: only the final answer streams.
        RoutePlan plan = router.plan(context, history, playerMessage);
        WorldFactBundle worldFacts = plan.useWorld() ? worldContextTool.collect(context, history, playerMessage)
                : WorldFactBundle.empty();

        List<Map<String, String>> messages = promptComposer.buildMessages(
                context,
                history,
                playerMessage,
                worldFacts
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", OllamaSettings.model);
        requestBody.put("stream", true);
        requestBody.put("messages", messages);

        String json = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(chatUri)
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        // Run the streaming call on a background thread so we don’t block the client
        Thread t = new Thread(() -> {
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Ollama HTTP " + response.statusCode());
                }

                StringBuilder fullReply = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;

                        JsonObject chunk = JsonParser.parseString(line).getAsJsonObject();
                        if (chunk.has("error")) {
                            throw new RuntimeException("Ollama error: " + chunk.get("error").getAsString());
                        }

                        if (chunk.has("message")) {
                            JsonObject msgObj = chunk.getAsJsonObject("message");
                            if (msgObj != null && msgObj.has("content")) {
                                String delta = msgObj.get("content").getAsString();
                                if (!delta.isEmpty()) {
                                    fullReply.append(delta);
                                    callbacks.onDelta(delta);
                                }
                            }
                        }

                        if (chunk.has("done") && chunk.get("done").getAsBoolean()) {
                            break;
                        }
                    }
                }

                callbacks.onCompleted(fullReply.toString());
            } catch (Exception e) {
                callbacks.onError(e);
            }
        }, "AgenticRAG-Ollama-Streaming-Thread");

        t.setDaemon(true);
        t.start();

        // Debug logging
        OllamaMod.LOGGER.info(
                "[AgenticRAG] route useWorld={} facts={} history={} msg='{}'",
                plan.useWorld(),
                worldFacts.facts().size(),
                history.size(),
                abbreviate(playerMessage, 80)
        );
    }

    private String parseReply(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonObject message = root.getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            return "[No reply from Ollama]";
        }
        return message.get("content").getAsString();
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 3)) + "...";
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
