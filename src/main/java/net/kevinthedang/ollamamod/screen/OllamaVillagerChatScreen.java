package net.kevinthedang.ollamamod.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import net.kevinthedang.ollamamod.OllamaMod;
import net.kevinthedang.ollamamod.chat.ChatMessage;
import net.kevinthedang.ollamamod.chat.ChatRole;
import net.kevinthedang.ollamamod.chat.VillagerBrain;
import net.kevinthedang.ollamamod.chat.VillagerChatService;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class OllamaVillagerChatScreen extends Screen {

    private final Screen previousScreen;

    private EditBox chatInput;
    private Button sendButton;
    private Button backButton;
    private Button exitButton;
    private double scrollOffset = 0;
    private double maxScroll = 0;
    private int thinkingBubbleIndex = -1;
    private boolean isStreaming = false;
    private long streamGeneration = 0;
    private final List<ChatMessageBubble> chatMessages = new ArrayList<>();
    private final StringBuilder streamingReplyBuffer = new StringBuilder();

    // GUI dimensions
    private static final int GUI_WIDTH = 280;
    private static final int GUI_HEIGHT = 170;

    private UUID conversationId;

    private String villagerName = "Villager";
    private String villagerProfession = "Unknown";
    private Integer villagerEntityId = null;
    private String statusText = "";
    private static final int PLAYER_BORDER_COLOR = 0xFF1F6FE5;
    private static final int PLAYER_FILL_COLOR = 0x00AAAA;
    private static final int NPC_BORDER_COLOR = 0xFF15582D;
    private static final int NPC_FILL_COLOR = 0x00AA00;
    private static final int SYSTEM_BORDER_COLOR = 0xFF888888;
    private static final int SYSTEM_FILL_COLOR = 0xFF555555;

    public OllamaVillagerChatScreen(Screen previousScreen) {
    this(previousScreen, null);
    }

    public OllamaVillagerChatScreen(Screen previousScreen, Integer villagerEntityId) {
        super(Component.literal("Villager Chat"));
        this.previousScreen = previousScreen;
        this.villagerEntityId = villagerEntityId;
    }

    @Override
    protected void init() {
        super.init();

        int startX = (this.width - GUI_WIDTH) / 2;
        int startY = (this.height - GUI_HEIGHT) / 2;

        // back button
        this.backButton = this.addRenderableWidget(Button.builder(
                Component.literal("<"),
                button -> this.onClose())
                .pos(startX + 5, startY + 5)
                .size(12, 12)
                .build());

        // exit button
        this.exitButton = this.addRenderableWidget(Button.builder(
                Component.literal("x"),
                button -> {
                    assert this.minecraft != null;
                    this.minecraft.setScreen(null); // return to game
                })
                .pos(startX + GUI_WIDTH - 17, startY + 5)
                .size(12, 12)
                .build());

        this.chatInput = new EditBox(
                this.font,
                startX + 5,
                startY + GUI_HEIGHT - 30,
                GUI_WIDTH - 45,
                20,
                Component.literal("Type your message or /help for commands...")) {

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                // Some versions deliver Enter here
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    OllamaVillagerChatScreen.this.sendMessage();
                    return true; // consume Enter
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }

            @Override
            public boolean charTyped(char codePoint, int modifiers) {
                // Some versions deliver Enter as a character (\n or \r)
                if (codePoint == '\n' || codePoint == '\r') {
                    OllamaVillagerChatScreen.this.sendMessage();
                    return true; // don't insert newline into the box
                }
                return super.charTyped(codePoint, modifiers);
            }
        };
        this.chatInput.setMaxLength(128);
        this.chatInput.setHint(Component.literal("Type your message or /help for commands..."));
        this.addRenderableWidget(this.chatInput);

        // send button
        this.sendButton = this.addRenderableWidget(Button.builder(
                Component.literal("Send"),
                button -> {
                    if (isStreaming) {
                        cancelCurrentStream();
                    } else {
                        sendMessage();
                    }
                })
                .pos(startX + GUI_WIDTH - 35, startY + GUI_HEIGHT - 30)
                .size(30, 20)
                .build());

        // input field as focus
        this.setInitialFocus(this.chatInput);

        ensurePersonaResolved();

        // Clear previous UI messages to avoid duplicates when init() is called again (e.g., window resize)
        this.chatMessages.clear();
        this.scrollOffset = 0;

        List<ChatMessage> history = OllamaMod.CHAT_SERVICE.getHistory(conversationId);
        for (ChatMessage msg : history) {
            appendToChatLog(msg);
        }
    }

    private void sendMessage() {
        if (this.minecraft == null) {
            return;
        }

        if (this.chatInput == null) {
            return;
        }

        if (isStreaming) {
            return;
        }

        String text = this.chatInput.getValue().trim();
        if (text.isEmpty()) {
            return;
        }

        this.chatInput.setValue("");

        // Intercept chat commands before sending to LLM
        if (text.startsWith("/")) {
            handleCommand(text);
            return;
        }

        // start streaming
        streamGeneration++;
        final long myStreamId = streamGeneration;
        isStreaming = true;
        streamingReplyBuffer.setLength(0);

        // button + input state
        this.sendButton.setMessage(Component.literal("Stop"));
        this.sendButton.active = true; // keep clickable to allow stopping
        this.chatInput.setEditable(false);

        // Add players' message to the local log immediately
        appendToChatLog(new ChatMessage(ChatRole.PLAYER, text));

        VillagerChatService.UiCallbacks callbacks = new VillagerChatService.UiCallbacks() {
            private final long id = myStreamId;

            @Override
            public void onThinkingStarted() {
                if (!isCurrentStream(id)) {
                    return;
                }

                ChatMessage thinkingMsg = new ChatMessage(ChatRole.VILLAGER, "Thinking...");
                appendToChatLog(thinkingMsg);
                thinkingBubbleIndex = chatMessages.size() - 1;
            }

            @Override
            public void onVillagerReplyDelta(String delta) {
                if (!isCurrentStream(id)) {
                    return;
                }

                streamingReplyBuffer.append(delta);
                updateThinkingBubble(streamingReplyBuffer.toString());
            }

            @Override
            public void onVillagerReplyFinished(String fullText) {
                if (!isCurrentStream(id)) {
                    return;
                }

                statusText = "";
                isStreaming = false;
                sendButton.setMessage(Component.literal("Send"));
                sendButton.active = true;
                if (chatInput != null) {
                    chatInput.setEditable(true);
                }

                if (thinkingBubbleIndex >= 0 && thinkingBubbleIndex < chatMessages.size()) {
                    updateThinkingBubble(fullText);
                } else {
                    appendToChatLog(new ChatMessage(ChatRole.VILLAGER, fullText));
                }

                streamingReplyBuffer.setLength(0);
                thinkingBubbleIndex = -1;
            }

            @Override
            public void onError(String errorMessage) {
                if (!isCurrentStream(id)) {
                    return;
                }

                statusText = errorMessage;
                isStreaming = false;
                sendButton.setMessage(Component.literal("Send"));
                sendButton.active = true;
                if (chatInput != null) {
                    chatInput.setEditable(true);
                }

                if (thinkingBubbleIndex >= 0 && thinkingBubbleIndex < chatMessages.size()) {
                    updateThinkingBubble("Error: " + errorMessage);
                }
            }
        };

        // World/dimension name
        String worldName = this.minecraft.level != null
                ? this.minecraft.level.dimension().location().toString()
                : "Unknown";

        ensurePersonaResolved();

        // Use the villager resolved at screen-open time (this.conversationId) to avoid
        // storing messages under a different villager if another one is nearer at send-time.
        Villager v = (this.villagerEntityId != null) ? resolveVillagerFromId(this.villagerEntityId) : null;

        VillagerBrain.Context context = new VillagerBrain.Context(
                this.conversationId,
                this.villagerName,
                this.villagerProfession,
                worldName
        );

        // Pass the villager position so the chat service can play the sound at the correct location
        OllamaMod.CHAT_SERVICE.sendPlayerMessage(
                this.conversationId,
                context,
                text,
                callbacks,
                true,
                (v != null) ? v.position() : null
        );

    }

    private void appendToChatLog(ChatMessage msg) {
        this.chatMessages.add(toUiMessage(msg));
        this.scrollOffset = Double.MAX_VALUE;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);

        int startX = (this.width - GUI_WIDTH) / 2;
        int startY = (this.height - GUI_HEIGHT) / 2;

        // panel background
        guiGraphics.fill(startX, startY, startX + GUI_WIDTH, startY + GUI_HEIGHT, 0xCC000000);
        guiGraphics.fill(startX + 2, startY + 2, startX + GUI_WIDTH - 2, startY + GUI_HEIGHT - 2, 0xFFD0D0D0);

        // dark chat background
        guiGraphics.fill(startX + 5, startY + 18, startX + GUI_WIDTH - 5, startY + GUI_HEIGHT - 35, 0xFF404040);

        this.renderChatHistory(guiGraphics, startX, startY);

        // Title
        guiGraphics.drawString(this.font, "Chat with Villager", startX + 20, startY + 7, 0xFF404040, false);

        // render it all
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Draw hint text over the input when empty (EditBox only shows hints when unfocused)
        if (this.chatInput != null && this.chatInput.getValue().isEmpty()) {
            int hintX = startX + 5 + 4;
            int hintY = startY + GUI_HEIGHT - 30 + 6;
            guiGraphics.drawString(this.font, "Type your message or /help for commands...",
                    hintX, hintY, 0xFF707070, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int startX = (this.width - GUI_WIDTH) / 2;
        int startY = (this.height - GUI_HEIGHT) / 2;
        int chatX = startX + 5;
        int chatY = startY + 18;
        int chatWidth = GUI_WIDTH - 10;
        int chatHeight = GUI_HEIGHT - 53;

        boolean insideChat = mouseX >= chatX && mouseX <= chatX + chatWidth
                && mouseY >= chatY && mouseY <= chatY + chatHeight;

        if (insideChat) {
            double previous = this.scrollOffset;

            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset - deltaY * 12.0D, this.maxScroll));

            if (previous != this.scrollOffset) {
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private void renderChatHistory(GuiGraphics guiGraphics, int startX, int startY) {
        if (this.chatMessages.isEmpty()) {
            return;
        }

        int chatX = startX + 5;
        int chatY = startY + 18;
        int chatWidth = GUI_WIDTH - 10;
        int chatHeight = GUI_HEIGHT - 53;
        int bubblePadding = 6;
        int bubbleSpacing = 4;
        int bubbleMaxWidth = chatWidth - bubbleSpacing * 2;

        // Only render the last 12 messages
        // TODO: Do we want a better way to handle large histories?
        List<ChatMessageBubble> messagesToRender;
        if (this.chatMessages.size() > 12) {
            messagesToRender = this.chatMessages.subList(
                    this.chatMessages.size() - 12,
                    this.chatMessages.size()
            );
        } else {
            messagesToRender = this.chatMessages;
        }

        // Precompute bubble layout for these messages
        List<BubbleRenderData> bubbleData = new ArrayList<>();
        int contentHeight = bubbleSpacing;

        // Oldest -> newest
        for (ChatMessageBubble currChatMessage : messagesToRender) {
            List<FormattedCharSequence> lines = this.font.split(
                    currChatMessage.text(),
                    Math.max(20, bubbleMaxWidth - bubblePadding * 2));
            int textWidth = 0;
            for (FormattedCharSequence line : lines) {
                textWidth = Math.max(textWidth, this.font.width(line));
            }

            int bubbleHeight = lines.size() * this.font.lineHeight + bubblePadding * 2;
            int bubbleWidth = Math.min(bubbleMaxWidth, textWidth + bubblePadding * 2);

            bubbleData.add(new BubbleRenderData(currChatMessage, lines, bubbleWidth, bubbleHeight));
            contentHeight += bubbleHeight + bubbleSpacing;
        }

        // Scroll range
        this.maxScroll = Math.max(0, contentHeight - chatHeight);

        if (this.scrollOffset > this.maxScroll) {
            this.scrollOffset = this.maxScroll;
        }
        if (this.scrollOffset < 0) {
            this.scrollOffset = 0;
        }

        int cursorY = chatY + bubbleSpacing - (int) Math.round(this.scrollOffset);

        guiGraphics.enableScissor(chatX, chatY, chatX + chatWidth, chatY + chatHeight);

        for (BubbleRenderData bubble : bubbleData) {
            int bubbleTop = cursorY;
            int bubbleBottom = bubbleTop + bubble.bubbleHeight();

            // Entirely above view: skip but advance cursor
            if (bubbleBottom < chatY) {
                cursorY = bubbleBottom + bubbleSpacing;
                continue;
            }

            // Entirely below view: we’re done
            if (bubbleTop > chatY + chatHeight) {
                break;
            }

            int bubbleLeft;
            int bubbleRight;
            ChatRole role = bubble.message().role();
            if (role == ChatRole.SYSTEM) {
                // Center system bubbles
                int center = chatX + chatWidth / 2;
                bubbleLeft = center - bubble.bubbleWidth() / 2;
                bubbleRight = bubbleLeft + bubble.bubbleWidth();
            } else if (role == ChatRole.PLAYER) {
                bubbleRight = chatX + chatWidth - bubbleSpacing;
                bubbleLeft = bubbleRight - bubble.bubbleWidth();
            } else {
                bubbleLeft = chatX + bubbleSpacing;
                bubbleRight = bubbleLeft + bubble.bubbleWidth();
            }

            int borderColor;
            int fillColor;
            if (role == ChatRole.SYSTEM) {
                borderColor = SYSTEM_BORDER_COLOR;
                fillColor = SYSTEM_FILL_COLOR;
            } else if (role == ChatRole.PLAYER) {
                borderColor = PLAYER_BORDER_COLOR;
                fillColor = PLAYER_FILL_COLOR;
            } else {
                borderColor = NPC_BORDER_COLOR;
                fillColor = NPC_FILL_COLOR;
            }

            guiGraphics.fill(bubbleLeft - 1, bubbleTop - 1, bubbleRight + 1, bubbleBottom + 1, borderColor);
            guiGraphics.fill(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom, fillColor);

            int textX = bubbleLeft + bubblePadding;
            int textY = bubbleTop + bubblePadding;
            for (FormattedCharSequence line : bubble.lines()) {
                guiGraphics.drawString(this.font, line, textX, textY, 0xFFFFFFFF);
                textY += this.font.lineHeight;
            }

            cursorY = bubbleBottom + bubbleSpacing;
        }

        guiGraphics.disableScissor();
    }

    @Override
    public void onClose() {
        // return to merchant screen
        assert this.minecraft != null;
        this.minecraft.setScreen(this.previousScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    private static class ChatMessageBubble {
        private Component text;
        private final ChatRole role;

        private ChatMessageBubble(Component text, ChatRole role) {
            this.text = text;
            this.role = role;
        }

        public Component text() {
            return this.text;
        }

        public ChatRole role() {
            return this.role;
        }

        public void setText(Component newText) {
            this.text = newText;
        }
    }

    private void updateThinkingBubble(String newText) {
        if (thinkingBubbleIndex >= 0 && thinkingBubbleIndex < chatMessages.size()) {
            ChatMessageBubble bubble = chatMessages.get(thinkingBubbleIndex);
            bubble.setText(Component.literal(newText));
            this.scrollOffset = Double.MAX_VALUE;
        }
    }

    private ChatMessageBubble toUiMessage(ChatMessage message) {
        return new ChatMessageBubble(Component.literal(message.content()), message.role());
    }

    private record BubbleRenderData(ChatMessageBubble message,
            List<FormattedCharSequence> lines,
            int bubbleWidth,
            int bubbleHeight) {
    }

    private boolean isCurrentStream(long id) {
        return id == this.streamGeneration;
    }

    private void cancelCurrentStream() {
        // bump generation so any late callbacks from this stream are ignored
        this.streamGeneration++;
        this.isStreaming = false;

        this.streamingReplyBuffer.setLength(0);
        this.thinkingBubbleIndex = -1;

        this.sendButton.setMessage(Component.literal("Send"));
        this.sendButton.active = true;
        if (this.chatInput != null) {
            this.chatInput.setEditable(true);
        }

        this.statusText = "Stopped.";
    }

    // Route a slash command to the appropriate handler.
    private void handleCommand(String text) {
        String command = text.toLowerCase().trim();

        if (command.equals("/help")) {
            appendSystemMessage(
                "Available commands:\n" +
                "/help - Show this help message\n" +
                "/clearmemory - Clear all memories and chat history for this villager\n" +
                "/clearhistory - Clear only the in-memory chat history (keeps long-term memories)\n" +
                "/who - Show current villager name, profession, and conversation ID\n" +
                "/recall - Show top memories the villager has for this conversation"
            );
        } else if (command.equals("/clearmemory")) {
            ensurePersonaResolved();
            OllamaMod.VECTOR_STORE.deleteMemoriesForVillager(conversationId.toString());
            OllamaMod.CHAT_HISTORY.clear(conversationId);
            chatMessages.clear();
            appendSystemMessage("Memory and chat history cleared for this villager.");
        } else if (command.equals("/clearhistory")) {
            ensurePersonaResolved();
            OllamaMod.CHAT_HISTORY.clear(conversationId);
            chatMessages.clear();
            appendSystemMessage("Chat history cleared. Long-term memories are preserved.");
        } else if (command.equals("/who")) {
            ensurePersonaResolved();
            appendSystemMessage(
                "Villager: " + villagerName + "\n" +
                "Profession: " + villagerProfession + "\n" +
                "Conversation ID: " + (conversationId != null ? conversationId.toString() : "none")
            );
        } else if (command.equals("/recall")) {
            ensurePersonaResolved();
            if (conversationId == null) {
                appendSystemMessage("No conversation active.");
                return;
            }
            appendSystemMessage("Fetching memories...");
            OllamaMod.VECTOR_STORE.queryMemories("recent memories", conversationId.toString(), 5)
                .whenComplete((docs, err) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    // Remove the "Fetching memories..." placeholder
                    if (!chatMessages.isEmpty()) chatMessages.remove(chatMessages.size() - 1);
                    if (err != null || docs == null || docs.isEmpty()) {
                        appendSystemMessage("No memories found for this villager.");
                    } else {
                        StringBuilder sb = new StringBuilder("Top memories for this villager:\n");
                        for (int i = 0; i < docs.size(); i++) {
                            String content = docs.get(i).content();
                            if (content == null) continue;
                            String snippet = content.length() > 120 ? content.substring(0, 117) + "..." : content;
                            sb.append((i + 1)).append(". ").append(snippet.replace('\n', ' ')).append("\n");
                        }
                        appendSystemMessage(sb.toString().trim());
                    }
                }));
        } else {
            appendSystemMessage("Unknown command. Type /help for available commands.");
        }
    }

    // Display a system message in the chat log.
    private void appendSystemMessage(String text) {
        chatMessages.add(new ChatMessageBubble(Component.literal(text), ChatRole.SYSTEM));
        this.scrollOffset = Double.MAX_VALUE;
    }

    private void ensurePersonaResolved() {
        // Once conversationId is set (real villager or random fallback), never change it for this screen instance.
        if (this.conversationId != null) {
            if (this.villagerEntityId != null) {
                // Refresh display name/profession in case they changed, but don't alter conversationId.
                Villager v = resolveVillagerFromId(this.villagerEntityId);
                if (v != null) {
                    this.villagerName = resolveName(v);
                    this.villagerProfession = prettyProfession(v);
                }
            }
            return;
        }

        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
            if (this.conversationId == null) this.conversationId = UUID.randomUUID();
            return;
        }

        Villager villager = null;

        if (this.villagerEntityId != null) {
            villager = resolveVillagerFromId(this.villagerEntityId);
        }

        if (villager == null) {
            villager = findNearestVillager(6.5);
        }

        if (villager != null) {
            this.villagerEntityId = villager.getId();
            this.conversationId = villager.getUUID(); // stable per villager
            this.villagerName = resolveName(villager);
            this.villagerProfession = prettyProfession(villager);
        }

        if (this.conversationId == null) {
            this.conversationId = UUID.randomUUID();
        }
    }

    private Villager resolveVillagerFromId(int entityId) {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        Entity e = this.minecraft.level.getEntity(entityId);
        if (e instanceof Villager v) return v;
        return null;
    }

    private Villager findNearestVillager(double radiusBlocks) {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) return null;

        AABB box = this.minecraft.player.getBoundingBox().inflate(radiusBlocks);
        List<Villager> villagers = this.minecraft.level.getEntitiesOfClass(Villager.class, box);
        if (villagers == null || villagers.isEmpty()) return null;

        return villagers.stream()
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(this.minecraft.player)))
                .orElse(null);
    }

    private static String resolveName(Villager v) {
        if (v == null) return "Villager";
        if (v.hasCustomName()) {
            return v.getCustomName() == null ? "Villager" : v.getCustomName().getString();
        }
        // getName() usually returns "Villager" unless customized, but is safe.
        return v.getName().getString();
    }

    private static String prettyProfession(Villager v) {
        if (v == null) return "Unknown";
        try {
            var holder = v.getVillagerData().profession(); // Holder<VillagerProfession>
            var profession = holder.value();
            var key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            if (key == null) return "Unknown";
            String raw = key.getPath(); // "farmer"
            if (raw.isEmpty()) return "Unknown";
            return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        } catch (Throwable t) {
            return "Unknown";
        }
    }
}
