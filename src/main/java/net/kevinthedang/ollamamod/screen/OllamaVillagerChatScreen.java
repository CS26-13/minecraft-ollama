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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class OllamaVillagerChatScreen extends Screen {

    private final Screen previousScreen;

    private EditBox chatInput;
    private Button sendButton;
    private Button backButton;
    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private double scrollOffset = 0;
    private double maxScroll = 0;

    // GUI dimensions
    private static final int GUI_WIDTH = 280;
    private static final int GUI_HEIGHT = 170;
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
                        button -> this.onClose()
                )
                .pos(startX + 5, startY + 5)
                .size(12, 12)
                .build());

        // chat input
        this.chatInput = new EditBox(
                this.font,
                startX + 5,
                startY + GUI_HEIGHT - 30,
                GUI_WIDTH - 45,
                20,
                Component.literal("Type your message...")
        );
        this.chatInput.setMaxLength(128); // how many characters do we want to allow?
        this.chatInput.setHint(Component.literal("Type your message..."));
        this.addRenderableWidget(this.chatInput);

        // send button
        this.sendButton = this.addRenderableWidget(Button.builder(
                        Component.literal("Send"),
                        button -> this.sendMessage()
                )
                .pos(startX + GUI_WIDTH - 35, startY + GUI_HEIGHT - 30)
                .size(30, 20)
                .build());

        // intput field as focus
        this.setInitialFocus(this.chatInput);
    }

    private void sendMessage() {
        if (this.chatInput == null) {
            return;
        }

        String message = this.chatInput.getValue().trim();
        if (message.isEmpty()) {
            return;
        }

        this.chatMessages.add(new ChatMessage(Component.literal(message), true));
        this.scrollOffset = 0;
        this.chatInput.setValue("");
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

        // title - currently broken
        guiGraphics.drawString(this.font, "Chat with Villager", startX + 30, startY + 10, 0xf50000, false);

        // render it all
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.chatInput != null && this.chatInput.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            this.sendMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset + deltaY * 12.0D, this.maxScroll));
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

        List<BubbleRenderData> bubbleData = new ArrayList<>();
        int contentHeight = bubbleSpacing;
        for (int i = this.chatMessages.size() - 1; i >= 0; i--) {
            ChatMessage chatMessage = this.chatMessages.get(i);
            List<FormattedCharSequence> lines = this.font.split(chatMessage.text(), Math.max(20, bubbleMaxWidth - bubblePadding * 2));

            int textWidth = 0;
            for (FormattedCharSequence line : lines) {
                textWidth = Math.max(textWidth, this.font.width(line));
            }

            int bubbleHeight = lines.size() * this.font.lineHeight + bubblePadding * 2;
            int bubbleWidth = Math.min(bubbleMaxWidth, textWidth + bubblePadding * 2);
            bubbleData.add(new BubbleRenderData(chatMessage, lines, bubbleWidth, bubbleHeight));
            contentHeight += bubbleHeight + bubbleSpacing;
        }

        this.maxScroll = Math.max(0, contentHeight - chatHeight);
        if (this.scrollOffset > this.maxScroll) {
            this.scrollOffset = this.maxScroll;
        }
        if (this.scrollOffset < 0) {
            this.scrollOffset = 0;
        }

        int cursorY = chatY + chatHeight - bubbleSpacing - (int) Math.round(this.scrollOffset);
        for (BubbleRenderData bubble : bubbleData) {
            int bubbleTop = cursorY - bubble.bubbleHeight();
            int bubbleBottom = bubbleTop + bubble.bubbleHeight();
            if (bubbleBottom < chatY) {
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

            cursorY = bubbleTop - bubbleSpacing;
        }
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

    private static class ChatMessage {
        private final Component text;
        private final boolean fromPlayer;

        private ChatMessage(Component text, boolean fromPlayer) {
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

    private record BubbleRenderData(ChatMessage message,
                                    List<FormattedCharSequence> lines,
                                    int bubbleWidth,
                                    int bubbleHeight) { }
}
