package net.kevinthedang.ollamamod.vectorstore.chunker;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.kevinthedang.ollamamod.vectorstore.VectorStoreSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonChunker extends Chunker {
    private final Gson gson = new Gson();

    // Chunker for JSON content using object boundaries.
    public JsonChunker() {
        super(VectorStoreSettings.chunkSize, 0);
    }

    // Split JSON into object-based chunks without splitting objects.
    @Override
    public List<String> chunk(String content) {
        List<String> chunks = new ArrayList<>();

        try {
            JsonElement root = JsonParser.parseString(content);

            if (root.isJsonArray()) {
                JsonArray array = root.getAsJsonArray();
                StringBuilder currentChunkBuilder = new StringBuilder();

                for (JsonElement element : array) {
                    String elementText = gson.toJson(element);

                    if (currentChunkBuilder.length() + elementText.length() > maxChunkSize
                        && currentChunkBuilder.length() > 0) {
                        chunks.add(currentChunkBuilder.toString());
                        currentChunkBuilder = new StringBuilder();
                    }

                    if (currentChunkBuilder.length() > 0) {
                        currentChunkBuilder.append("\n");
                    }
                    currentChunkBuilder.append(elementText);
                }

                if (currentChunkBuilder.length() > 0) {
                    chunks.add(currentChunkBuilder.toString());
                }

            } else if (root.isJsonObject()) {
                JsonObject object = root.getAsJsonObject();

                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    if (entry.getValue().isJsonArray()) {
                        JsonArray array = entry.getValue().getAsJsonArray();
                        for (JsonElement element : array) {
                            chunks.add(gson.toJson(element));
                        }
                        return chunks;
                    }
                }

                chunks.add(content);
            } else {
                chunks.add(content);
            }

        } catch (JsonSyntaxException exception) {
            chunks.add(content);
        }

        return chunks;
    }
}
