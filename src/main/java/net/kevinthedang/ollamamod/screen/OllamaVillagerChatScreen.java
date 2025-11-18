package net.kevinthedang.ollamamod.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class OllamaVillagerChatScreen extends Screen {

    private final Screen previousScreen;

    private EditBox chatInput;
    private Button sendButton;
    private Button backButton;
    private double scrollOffset = 0;
    private double maxScroll = 0;
    private final List<ChatMessageBubble> chatMessages = new ArrayList<>();

    // GUI dimensions
    private static final int GUI_WIDTH = 280;
    private static final int GUI_HEIGHT = 170;

    // LLM Bridge
    private final UUID conversationId = UUID.randomUUID();
    private String statusText = "";
    private static final int PLAYER_BORDER_COLOR = 0xFF1F6FE5;
    private static final int PLAYER_FILL_COLOR = 0xCC4AA5FF;
    private static final int NPC_BORDER_COLOR = 0xFF23924A;
    private static final int NPC_FILL_COLOR = 0xCC44D36A;

    public OllamaVillagerChatScreen(Screen previousScreen) {
        super(Component.literal("Villager Chat"));
        this.previousScreen = previousScreen;
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

        // chat input
        // TODO: Link Enter key into backend NPC response logic

        this.chatInput = new EditBox(
                this.font,
                startX + 5,
                startY + GUI_HEIGHT - 30,
                GUI_WIDTH - 45,
                20,
                Component.literal("Type your message...")) {
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
        this.chatInput.setHint(Component.literal("Type your message..."));
        this.addRenderableWidget(this.chatInput);

        // send button
        this.sendButton = this.addRenderableWidget(Button.builder(
                Component.literal("Send"),
                button -> this.sendMessage())
                .pos(startX + GUI_WIDTH - 35, startY + GUI_HEIGHT - 30)
                .size(30, 20)
                .build());

        // input field as focus
        this.setInitialFocus(this.chatInput);

        List<ChatMessage> history = OllamaMod.CHAT_SERVICE.getHistory(conversationId);
        for (ChatMessage msg : history) {
            appendToChatLog(msg);
        }
    }

    // TODO: Implement sendMessage
    private void sendMessage() {
        if (this.minecraft == null) {
            return;
        }

        if (this.chatInput == null) {
            return;
        }

        String text = this.chatInput.getValue().trim();
        if (text.isEmpty()) {
            return;
        }

        this.chatInput.setValue("");
        this.sendButton.active = false;

        // Add player message to the local log immediately
        appendToChatLog(new ChatMessage(ChatRole.PLAYER, text));

        VillagerChatService.UiCallbacks callbacks = new VillagerChatService.UiCallbacks() {
            @Override
            public void onThinkingStarted() {
                statusText = "Thinking...";
            }

            @Override
            public void onVillagerReplyFinished(String fullText) {
                statusText = "";
                sendButton.active = true;
                appendToChatLog(new ChatMessage(ChatRole.VILLAGER, fullText));
            }

            @Override
            public void onError(String errorMessage) {
                statusText = errorMessage;
                sendButton.active = true;
            }
        };

        // for context retrieval later
        String worldName = this.minecraft.level != null
                ? this.minecraft.level.dimension().location().toString()
                : "Unknown";

        VillagerBrain.Context ctx = new VillagerBrain.Context(
                conversationId,
                "Villager",
                "Unemployed",
                worldName
        );

        OllamaMod.CHAT_SERVICE.sendPlayerMessage(conversationId, ctx, text, callbacks);
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
        guiGraphics.drawString(this.font, "Chat with Villager", startX + 30, startY + 10, 0xFFFFFFFF, false);

        // render it all
        super.render(guiGraphics, mouseX, mouseY, partialTick);
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

        // Oldest → newest
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
            if (bubble.message().fromPlayer()) {
                bubbleRight = chatX + chatWidth - bubbleSpacing;
                bubbleLeft = bubbleRight - bubble.bubbleWidth();
            } else {
                bubbleLeft = chatX + bubbleSpacing;
                bubbleRight = bubbleLeft + bubble.bubbleWidth();
            }

            int borderColor = bubble.message().fromPlayer() ? PLAYER_BORDER_COLOR : NPC_BORDER_COLOR;
            int fillColor = bubble.message().fromPlayer() ? PLAYER_FILL_COLOR : NPC_FILL_COLOR;

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
        private final Component text;
        private final boolean fromPlayer;

        private ChatMessageBubble(Component text, boolean fromPlayer) {
            this.text = text;
            this.fromPlayer = fromPlayer;
        }

        public Component text() {
            return this.text;
        }

        public boolean fromPlayer() {
            return this.fromPlayer;
        }
    }

    private ChatMessageBubble toUiMessage(ChatMessage message) {
        boolean fromPlayer = (message.role() == ChatRole.PLAYER);
        return new ChatMessageBubble(Component.literal(message.content()), fromPlayer);
    }

    private ChatMessage toDomainMessage(String playerInput) {
        return new ChatMessage(ChatRole.PLAYER, playerInput);
    }

    private ChatMessage villagerReply(String text) {
        return new ChatMessage(ChatRole.VILLAGER, text);
    }

    private ChatMessage systemMessage(String text) {
        return new ChatMessage(ChatRole.SYSTEM, text);
    }

    private record BubbleRenderData(ChatMessageBubble message,
            List<FormattedCharSequence> lines,
            int bubbleWidth,
            int bubbleHeight) {
    }
}
