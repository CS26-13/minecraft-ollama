package net.kevinthedang.ollamamod.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class OllamaVillagerChatScreen extends Screen {

    private final Screen previousScreen;

    private EditBox chatInput;
    private Button sendButton;
    private Button backButton;

    // GUI dimensions
    private static final int GUI_WIDTH = 280;
    private static final int GUI_HEIGHT = 170;

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

    // TODO: Implement sendMessage
    private void sendMessage() {}

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

        // TODO: show chat history

        // title - currently broken
        guiGraphics.drawString(this.font, "Chat with Villager", startX + 30, startY + 10, 0xf50000, false);

        // render it all
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // check if enter was pressed
        if (keyCode == 257 && this.chatInput.isFocused()) { // 257 is 'enter' key
            this.sendMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
}