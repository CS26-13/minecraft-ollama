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
                Component.literal("Type your message...")) {

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                System.out.println("Screen keyPressed: keyCode=" + keyCode + ", scanCode=" + scanCode);

                // Some versions deliver Enter here
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    System.out.println("ENTER detected, sending message");
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

        List<ChatMessage> history = OllamaMod.CHAT_SERVICE.getHistory(conversationId);
        for (ChatMessage msg : history) {
            appendToChatLog(msg);
        }
    }

    private Villager findNearestVillager() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return null;

        var player = minecraft.player;
        var level = minecraft.level;

        var aabb = player.getBoundingBox().inflate(6.0);

        var list = level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, aabb);
        if (list.isEmpty()) return null;

        net.minecraft.world.entity.npc.Villager best = null;
        double bestDist = Double.MAX_VALUE;
        for (var v : list) {
            double d = v.distanceToSqr(player);
            if (d < bestDist) {
                bestDist = d;
                best = v;
            }
        }
        return best;
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

        Villager v = findNearestVillager();
        String villagerName = (v != null) ? v.getName().getString() : "Villager";
        String profession = (v != null) ? prettyProfession(v) : "Unknown";

        UUID conversationId = (v != null) ? v.getUUID() : UUID.randomUUID();

        VillagerBrain.Context context = new VillagerBrain.Context(
                conversationId,
                villagerName,
                profession,
                worldName
        );

        OllamaMod.CHAT_SERVICE.sendPlayerMessage(conversationId, context, text, callbacks, true);
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
        private Component text;
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
        boolean fromPlayer = (message.role() == ChatRole.PLAYER);
        return new ChatMessageBubble(Component.literal(message.content()), fromPlayer);
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

    private void ensurePersonaResolved() {
        if (this.conversationId != null && this.villagerEntityId != null) {
            // still refresh name/profession in case they changed
            Villager v = resolveVillagerFromId(this.villagerEntityId);
            if (v != null) {
                this.villagerName = resolveName(v);
                this.villagerProfession = prettyProfession(v);
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

        String vd = String.valueOf(v.getVillagerData());
        String lower = vd.toLowerCase();

        int idx = lower.indexOf("profession=");
        if (idx < 0) return "Unknown";

        int start = idx + "profession=".length();
        int end = lower.indexOf(",", start);
        if (end < 0) end = lower.indexOf("]", start);
        if (end < 0) end = vd.length();

        String raw = vd.substring(start, end).trim();
        if (raw.contains(":")) raw = raw.substring(raw.indexOf(":") + 1);

        if (raw.isEmpty()) return "Unknown";
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
