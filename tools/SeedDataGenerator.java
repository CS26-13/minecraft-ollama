import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class SeedDataGenerator {
    private static final String DEFAULT_STORE_PATH =
        "src/main/resources/ollamamod/seed/documents.store";

    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 64;

    private static final String DEFAULT_MODEL = "nomic-embed-text";
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";

    private final HttpClient httpClient;
    private final URI embedEndpoint;
    private final String embeddingModel;

    // Entry point for CLI usage.
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 0 || hasFlag(arguments, "--help")) {
            printUsage();
            return;
        }

        ParsedArguments parsedArguments = ParsedArguments.from(arguments);
        Path storePath = Paths.get(parsedArguments.outputPath);

        if (parsedArguments.shouldClear) {
            clearStore(storePath);
            System.out.println("Cleared store: " + storePath);
            return;
        }

        if (parsedArguments.shouldList) {
            listStore(storePath);
            return;
        }

        if (!parsedArguments.ingestTargets.isEmpty()) {
            SeedDataGenerator generator = new SeedDataGenerator(
                parsedArguments.ollamaBaseUrl,
                parsedArguments.embeddingModel
            );

            List<Path> ingestFiles = expandPaths(parsedArguments.ingestTargets);
            if (ingestFiles.isEmpty()) {
                System.err.println("No ingestable files found.");
                return;
            }

            List<SeedDocument> storeDocuments = loadStore(storePath);
            int totalChunksAdded = 0;
            for (Path ingestFile : ingestFiles) {
                totalChunksAdded += generator.ingestFile(ingestFile, storeDocuments);
            }

            persistStore(storePath, storeDocuments);
            System.out.println("Ingest complete. Added " + totalChunksAdded
                + " chunks. Store size=" + storeDocuments.size());
            return;
        }

        System.err.println("No action specified. Use --ingest, --clear, or --list.");
        printUsage();
    }

    // Creates a generator with the target Ollama base URL and embedding model.
    public SeedDataGenerator(String ollamaBaseUrl, String embeddingModel) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.embedEndpoint = URI.create(ollamaBaseUrl + "/api/embed");
        this.embeddingModel = embeddingModel;
    }

    // Reads a file, chunks it, embeds each chunk, and appends to the store list.
    private int ingestFile(Path inputFile, List<SeedDocument> storeDocuments) {
        String lowerCaseName = inputFile.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(lowerCaseName.endsWith(".txt") || lowerCaseName.endsWith(".json"))) {
            System.out.println("Skipping unsupported file: " + inputFile);
            return 0;
        }

        String fileContent;
        try {
            fileContent = Files.readString(inputFile, StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            System.err.println("Failed to read file: " + inputFile + " - " + ioException.getMessage());
            return 0;
        }

        List<String> contentChunks = lowerCaseName.endsWith(".json")
            ? chunkJson(fileContent)
            : chunkText(fileContent);

        int chunksAdded = 0;
        for (int chunkIndex = 0; chunkIndex < contentChunks.size(); chunkIndex++) {
            String chunkContent = contentChunks.get(chunkIndex);
            float[] embeddingVector;
            try {
                embeddingVector = embed(chunkContent);
            } catch (Exception exception) {
                System.err.println("Embedding failed for " + inputFile + " chunk "
                    + chunkIndex + ": " + exception.getMessage());
                continue;
            }

            SeedDocument seedDocument = new SeedDocument(
                UUID.randomUUID().toString(),
                chunkContent,
                embeddingVector,
                System.currentTimeMillis(),
                chunkIndex,
                contentChunks.size()
            );
            storeDocuments.add(seedDocument);
            chunksAdded++;
        }

        System.out.println("Ingested " + inputFile + " -> " + chunksAdded + " chunks");
        return chunksAdded;
    }

    // Sends a single embedding request to Ollama and returns the float vector.
    private float[] embed(String inputText) throws Exception {
        String requestBody = "{\"model\":\"" + escapeJson(embeddingModel) + "\",\"input\":"
            + jsonString(inputText) + "}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(embedEndpoint)
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama HTTP " + response.statusCode() + ": " + response.body());
        }

        return parseEmbedding(response.body());
    }

    // Extracts the first embedding vector from the Ollama /api/embed response JSON.
    private static float[] parseEmbedding(String jsonResponse) {
        int embeddingsKeyIndex = jsonResponse.indexOf("\"embeddings\"");
        if (embeddingsKeyIndex < 0) {
            throw new IllegalArgumentException("No embeddings field in response");
        }
        int outerArrayStart = jsonResponse.indexOf('[', embeddingsKeyIndex);
        if (outerArrayStart < 0) {
            throw new IllegalArgumentException("Invalid embeddings array");
        }
        int vectorArrayStart = jsonResponse.indexOf('[', outerArrayStart + 1);
        if (vectorArrayStart < 0) {
            throw new IllegalArgumentException("Invalid embeddings array");
        }
        int vectorArrayEnd = findMatchingBracket(jsonResponse, vectorArrayStart);
        if (vectorArrayEnd < 0) {
            throw new IllegalArgumentException("Unterminated embeddings array");
        }

        String innerVector = jsonResponse.substring(vectorArrayStart + 1, vectorArrayEnd).trim();
        if (innerVector.isEmpty()) return new float[0];

        String[] vectorParts = innerVector.split(",");
        float[] embeddingVector = new float[vectorParts.length];
        for (int index = 0; index < vectorParts.length; index++) {
            embeddingVector[index] = Float.parseFloat(vectorParts[index].trim());
        }
        return embeddingVector;
    }

    // Finds the matching closing bracket for a JSON array starting at the given index.
    private static int findMatchingBracket(String text, int openingBracketIndex) {
        int nestingDepth = 0;
        for (int index = openingBracketIndex; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '[') nestingDepth++;
            else if (character == ']') {
                nestingDepth--;
                if (nestingDepth == 0) return index;
            }
        }
        return -1;
    }

    // Splits plain text into sentence-aligned chunks with overlap.
    private static List<String> chunkText(String content) {
        if (content.length() <= CHUNK_SIZE) {
            return List.of(content);
        }

        List<String> chunkList = new ArrayList<>();
        int startIndex = 0;

        while (startIndex < content.length()) {
            int endIndex = Math.min(startIndex + CHUNK_SIZE, content.length());

            if (endIndex < content.length()) {
                int sentenceEndIndex = findSentenceBoundary(content, startIndex, endIndex);
                if (sentenceEndIndex > startIndex) {
                    endIndex = sentenceEndIndex;
                }
            }

            String chunk = content.substring(startIndex, endIndex).trim();
            if (!chunk.isEmpty()) {
                chunkList.add(chunk);
            }
            startIndex = endIndex - CHUNK_OVERLAP;
            if (startIndex <= 0) startIndex = endIndex;
        }

        return chunkList;
    }

    // Locates a sentence boundary to avoid cutting mid-sentence.
    private static int findSentenceBoundary(String text, int startIndex, int endIndex) {
        int minimumIndex = startIndex + CHUNK_SIZE / 2;
        for (int index = endIndex; index > minimumIndex; index--) {
            char character = text.charAt(index - 1);
            if ((character == '.' || character == '!' || character == '?') &&
                (index == text.length() || Character.isWhitespace(text.charAt(index)))) {
                return index;
            }
        }
        return endIndex;
    }

    // Splits JSON into object-sized chunks without relying on external JSON libraries.
    private static List<String> chunkJson(String content) {
        String trimmedContent = content.trim();
        if (trimmedContent.isEmpty()) return List.of();

        if (trimmedContent.startsWith("[")) {
            List<String> elements = splitTopLevelArray(trimmedContent);
            return groupJsonElements(elements);
        }

        if (trimmedContent.startsWith("{")) {
            List<String> elements = splitFirstTopLevelArrayInObject(trimmedContent);
            if (!elements.isEmpty()) {
                return groupJsonElements(elements);
            }
        }

        return List.of(content);
    }

    // Splits a top-level JSON array into individual element strings.
    private static List<String> splitTopLevelArray(String text) {
        List<String> elements = new ArrayList<>();
        int elementStartIndex = -1;
        int nestingDepth = 0;
        boolean insideString = false;
        boolean isEscaped = false;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (isEscaped) {
                isEscaped = false;
                continue;
            }
            if (character == '\\') {
                isEscaped = true;
                continue;
            }
            if (character == '"') {
                insideString = !insideString;
                continue;
            }
            if (insideString) continue;

            if (character == '[') {
                nestingDepth++;
                if (nestingDepth == 1) {
                    continue;
                }
            } else if (character == ']') {
                if (nestingDepth == 1 && elementStartIndex >= 0) {
                    elements.add(text.substring(elementStartIndex, index).trim());
                    elementStartIndex = -1;
                }
                nestingDepth--;
            } else if (character == '{' || character == '"' || character == '-' || Character.isDigit(character)) {
                if (nestingDepth == 1 && elementStartIndex < 0) {
                    elementStartIndex = index;
                }
            } else if (character == ',' && nestingDepth == 1 && elementStartIndex >= 0) {
                elements.add(text.substring(elementStartIndex, index).trim());
                elementStartIndex = -1;
            }
        }
        if (elementStartIndex >= 0 && elementStartIndex < text.length()) {
            elements.add(text.substring(elementStartIndex).trim());
        }
        elements.removeIf(String::isEmpty);
        return elements;
    }

    // Searches for the first top-level array inside a JSON object and splits it.
    private static List<String> splitFirstTopLevelArrayInObject(String text) {
        int nestingDepth = 0;
        boolean insideString = false;
        boolean isEscaped = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (isEscaped) {
                isEscaped = false;
                continue;
            }
            if (character == '\\') {
                isEscaped = true;
                continue;
            }
            if (character == '"') {
                insideString = !insideString;
                continue;
            }
            if (insideString) continue;

            if (character == '{') nestingDepth++;
            else if (character == '}') nestingDepth--;
            else if (character == '[' && nestingDepth == 1) {
                int endIndex = findMatchingBracket(text, index);
                if (endIndex > index) {
                    String arrayText = text.substring(index, endIndex + 1);
                    return splitTopLevelArray(arrayText);
                }
            }
        }
        return Collections.emptyList();
    }

    // Groups JSON element strings into chunks that stay under the max chunk size.
    private static List<String> groupJsonElements(List<String> elements) {
        if (elements.isEmpty()) return List.of();

        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunkBuilder = new StringBuilder();
        for (String element : elements) {
            if (currentChunkBuilder.length() + element.length() > CHUNK_SIZE
                && currentChunkBuilder.length() > 0) {
                chunks.add(currentChunkBuilder.toString());
                currentChunkBuilder = new StringBuilder();
            }
            if (currentChunkBuilder.length() > 0) currentChunkBuilder.append("\n");
            currentChunkBuilder.append(element);
        }
        if (currentChunkBuilder.length() > 0) chunks.add(currentChunkBuilder.toString());
        return chunks;
    }

    // Writes the in-memory store list to disk in a compact binary format.
    private static void persistStore(Path storePath, List<SeedDocument> storeDocuments) throws IOException {
        Files.createDirectories(storePath.toAbsolutePath().getParent());
        try (DataOutputStream outputStream = new DataOutputStream(
            new BufferedOutputStream(Files.newOutputStream(storePath)))) {
            outputStream.writeInt(storeDocuments.size());
            for (SeedDocument document : storeDocuments) {
                document.write(outputStream);
            }
        }
    }

    // Loads the store from disk if it exists, otherwise returns an empty list.
    private static List<SeedDocument> loadStore(Path storePath) {
        if (!Files.exists(storePath)) return new ArrayList<>();

        List<SeedDocument> storeDocuments = new ArrayList<>();
        try (DataInputStream inputStream = new DataInputStream(
            new BufferedInputStream(Files.newInputStream(storePath)))) {
            int documentCount = inputStream.readInt();
            for (int index = 0; index < documentCount; index++) {
                storeDocuments.add(SeedDocument.read(inputStream));
            }
        } catch (EOFException eofException) {
            System.err.println("Store file appears truncated; loading what we can.");
        } catch (IOException ioException) {
            System.err.println("Failed to load store: " + ioException.getMessage());
        }
        return storeDocuments;
    }

    // Deletes the store file if it exists.
    private static void clearStore(Path storePath) throws IOException {
        if (Files.exists(storePath)) {
            Files.delete(storePath);
        }
    }

    // Prints store size and a small sample of contents.
    private static void listStore(Path storePath) {
        List<SeedDocument> storeDocuments = loadStore(storePath);
        System.out.println("Store: " + storePath + " (" + storeDocuments.size() + " documents)");
        int sampleCount = Math.min(3, storeDocuments.size());
        for (int index = 0; index < sampleCount; index++) {
            SeedDocument document = storeDocuments.get(index);
            System.out.println("- " + abbreviate(document.content, 120));
        }
    }

    // Shortens long strings for logging.
    private static String abbreviate(String text, int maximumLength) {
        if (text == null) return "";
        if (text.length() <= maximumLength) return text;
        return text.substring(0, Math.max(0, maximumLength - 3)) + "...";
    }

    // Escapes quotes and backslashes for JSON field values.
    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Converts a Java string into a JSON string literal.
    private static String jsonString(String text) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"': stringBuilder.append("\\\""); break;
                case '\\': stringBuilder.append("\\\\"); break;
                case '\n': stringBuilder.append("\\n"); break;
                case '\r': stringBuilder.append("\\r"); break;
                case '\t': stringBuilder.append("\\t"); break;
                default:
                    if (character < 0x20) {
                        stringBuilder.append(String.format("\\u%04x", (int) character));
                    } else {
                        stringBuilder.append(character);
                    }
            }
        }
        stringBuilder.append('"');
        return stringBuilder.toString();
    }

    // Checks if a flag is present in the argument list.
    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) return true;
        }
        return false;
    }

    // Reads a single value after a named option.
    private static String getOptionValue(String[] args, String option) {
        for (int index = 0; index < args.length - 1; index++) {
            if (option.equals(args[index])) {
                return args[index + 1];
            }
        }
        return null;
    }

    // Reads all values after a named option until the next flag.
    private static List<String> getOptionValues(String[] args, String option) {
        List<String> values = new ArrayList<>();
        boolean collectingValues = false;
        for (String arg : args) {
            if (option.equals(arg)) {
                collectingValues = true;
                continue;
            }
            if (collectingValues) {
                if (arg.startsWith("--")) {
                    collectingValues = false;
                } else {
                    values.add(arg);
                }
            }
        }
        return values;
    }

    // Expands file and directory paths to a sorted list of files (directories are recursive).
    private static List<Path> expandPaths(List<String> rawPaths) {
        Set<Path> uniqueFiles = new HashSet<>();
        for (String rawPath : rawPaths) {
            Path inputPath = Paths.get(rawPath);
            if (!Files.exists(inputPath)) {
                System.err.println("Path does not exist: " + inputPath);
                continue;
            }
            if (Files.isDirectory(inputPath)) {
                try {
                    Files.walk(inputPath)
                        .filter(Files::isRegularFile)
                        .forEach(uniqueFiles::add);
                } catch (IOException ioException) {
                    System.err.println("Failed to walk directory: " + inputPath
                        + " - " + ioException.getMessage());
                }
            } else {
                uniqueFiles.add(inputPath);
            }
        }
        List<Path> sortedFiles = new ArrayList<>(uniqueFiles);
        sortedFiles.sort(Comparator.comparing(Path::toString));
        return sortedFiles;
    }

    // Prints CLI usage instructions.
    private static void printUsage() {
        System.out.println("SeedDataGenerator usage:");
        System.out.println("  --ingest <file|dir> [file|dir ...]  Ingest .txt/.json files (dirs are recursive)");
        System.out.println("  --clear                             Delete the store file");
        System.out.println("  --list                              List store contents");
        System.out.println("  --output <path>                     Override output path");
        System.out.println("  --model <name>                      Embedding model (default nomic-embed-text)");
        System.out.println("  --ollama <url>                      Ollama base URL (default http://localhost:11434)");
    }

    private static class ParsedArguments {
        private final boolean shouldClear;
        private final boolean shouldList;
        private final String outputPath;
        private final String embeddingModel;
        private final String ollamaBaseUrl;
        private final List<String> ingestTargets;

        private ParsedArguments(boolean shouldClear, boolean shouldList, String outputPath,
                                String embeddingModel, String ollamaBaseUrl, List<String> ingestTargets) {
            this.shouldClear = shouldClear;
            this.shouldList = shouldList;
            this.outputPath = outputPath;
            this.embeddingModel = embeddingModel;
            this.ollamaBaseUrl = ollamaBaseUrl;
            this.ingestTargets = ingestTargets;
        }

        // Parses CLI arguments into a structured configuration object.
        private static ParsedArguments from(String[] arguments) {
            String outputPath = getOptionValue(arguments, "--output");
            if (outputPath == null) outputPath = DEFAULT_STORE_PATH;

            String embeddingModel = getOptionValue(arguments, "--model");
            if (embeddingModel == null) embeddingModel = DEFAULT_MODEL;

            String ollamaBaseUrl = getOptionValue(arguments, "--ollama");
            if (ollamaBaseUrl == null) ollamaBaseUrl = DEFAULT_OLLAMA_BASE_URL;

            List<String> ingestTargets = getOptionValues(arguments, "--ingest");

            boolean shouldClear = hasFlag(arguments, "--clear");
            boolean shouldList = hasFlag(arguments, "--list");

            return new ParsedArguments(
                shouldClear,
                shouldList,
                outputPath,
                embeddingModel,
                ollamaBaseUrl,
                ingestTargets
            );
        }
    }

    private static class SeedDocument {
        private final String id;
        private final String content;
        private final float[] embedding;
        private final long timestamp;
        private final int chunkIndex;
        private final int chunkTotal;

        SeedDocument(String id, String content, float[] embedding,
                     long timestamp, int chunkIndex, int chunkTotal) {
            this.id = id;
            this.content = content;
            this.embedding = embedding;
            this.timestamp = timestamp;
            this.chunkIndex = chunkIndex;
            this.chunkTotal = chunkTotal;
        }

        // Writes this document to the binary store output stream.
        void write(DataOutputStream outputStream) throws IOException {
            outputStream.writeUTF(id);
            outputStream.writeUTF(content);
            outputStream.writeLong(timestamp);
            outputStream.writeInt(chunkIndex);
            outputStream.writeInt(chunkTotal);
            outputStream.writeInt(embedding.length);
            for (float value : embedding) {
                outputStream.writeFloat(value);
            }
        }

        // Reads a document from the binary store input stream.
        static SeedDocument read(DataInputStream inputStream) throws IOException {
            String id = inputStream.readUTF();
            String content = inputStream.readUTF();
            long timestamp = inputStream.readLong();
            int chunkIndex = inputStream.readInt();
            int chunkTotal = inputStream.readInt();
            int length = inputStream.readInt();
            float[] embedding = new float[length];
            for (int index = 0; index < length; index++) {
                embedding[index] = inputStream.readFloat();
            }
            return new SeedDocument(id, content, embedding, timestamp, chunkIndex, chunkTotal);
        }
    }
}
