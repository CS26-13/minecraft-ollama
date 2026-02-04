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

    // Chunker for JSON content with explicit size settings.
    public JsonChunker(int maxChunkSize) {
        super(maxChunkSize, 0);
    }

    // Split JSON into object-based chunks without splitting objects.
    @Override
    public List<String> chunk(String content) {
        try {
            JsonElement root = JsonParser.parseString(content);
            List<JsonElement> elements = extractChunkElements(root);
            if (elements.isEmpty()) {
                return List.of(content);
            }
            return groupJsonElements(elements);
        } catch (JsonSyntaxException exception) {
            throw new IllegalArgumentException("Invalid JSON content", exception);
        }
    }

    // Recursively extract JSON elements, prioritizing nested arrays/objects over large parents.
    private List<JsonElement> extractChunkElements(JsonElement element) {
        if (element.isJsonArray()) {
            return extractFromArray(element.getAsJsonArray());
        }

        if (element.isJsonObject()) {
            if (!shouldDrillDown(element)) {
                return List.of(element);
            }

            JsonObject object = element.getAsJsonObject();
            List<JsonElement> arrayChildren = new ArrayList<>();
            List<JsonElement> objectChildren = new ArrayList<>();

            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonArray()) {
                    arrayChildren.add(value);
                } else if (value.isJsonObject()) {
                    objectChildren.add(value);
                }
            }

            if (!arrayChildren.isEmpty()) {
                List<JsonElement> results = new ArrayList<>();
                // Prefer arrays containing objects over arrays of primitives.
                List<JsonElement> objectArrays = new ArrayList<>();
                for (JsonElement child : arrayChildren) {
                    if (arrayContainsObjects(child.getAsJsonArray())) {
                        objectArrays.add(child);
                    }
                }
                List<JsonElement> arraysToProcess = objectArrays.isEmpty() ? arrayChildren : objectArrays;
                for (JsonElement child : arraysToProcess) {
                    results.addAll(extractChunkElements(child));
                }
                return results;
            }

            if (!objectChildren.isEmpty()) {
                List<JsonElement> results = new ArrayList<>();
                for (JsonElement child : objectChildren) {
                    results.addAll(extractChunkElements(child));
                }
                return results;
            }

            return List.of(element);
        }

        return List.of(element);
    }

    // Extract elements from an array, preferring objects without drilling into them.
    private List<JsonElement> extractFromArray(JsonArray array) {
        List<JsonElement> results = new ArrayList<>();
        boolean hasObject = false;
        for (JsonElement child : array) {
            if (child.isJsonObject()) {
                hasObject = true;
                break;
            }
        }
        for (JsonElement child : array) {
            if (hasObject && shouldDrillDown(child)) {
                results.addAll(extractChunkElements(child));
            } else {
                results.add(child);
            }
        }
        return results;
    }

    // Check if the array contains object elements.
    private boolean arrayContainsObjects(JsonArray array) {
        for (JsonElement child : array) {
            if (child.isJsonObject()) {
                return true;
            }
        }
        return false;
    }

    // Decide whether to drill into a JSON element based on size and structure.
    private boolean shouldDrillDown(JsonElement element) {
        if (!element.isJsonArray() && !element.isJsonObject()) {
            return false;
        }
        String serialized = gson.toJson(element);
        if (serialized.length() <= maxChunkSize) {
            return false;
        }
        if (element.isJsonArray()) {
            return arrayContainsObjects(element.getAsJsonArray());
        }
        JsonObject object = element.getAsJsonObject();
        return objectContainsComplexChildren(object);
    }

    // Check if an object has nested structures worth drilling into.
    private boolean objectContainsComplexChildren(JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonArray()) {
                if (arrayContainsObjects(value.getAsJsonArray())) {
                    return true;
                }
            } else if (value.isJsonObject()) {
                return true;
            }
        }
        return false;
    }

    // Group JSON elements into chunks that stay under the max chunk size.
    private List<String> groupJsonElements(List<JsonElement> elements) {
        if (elements.isEmpty()) return List.of();

        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunkBuilder = new StringBuilder();
        for (JsonElement element : elements) {
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
        return chunks;
    }
}
