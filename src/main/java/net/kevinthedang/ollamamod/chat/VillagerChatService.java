package net.kevinthedang.ollamamod.chat;

import net.kevinthedang.ollamamod.OllamaMod;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.UUID;

public class VillagerChatService {
    private final ChatHistoryManager historyManager;
    private final VillagerBrain brain;

    public interface UiCallbacks {
        void onThinkingStarted();

        void onVillagerReplyFinished(String fullText);

        void onError(String errorMessage);
    }

    public VillagerChatService(ChatHistoryManager historyManager, VillagerBrain brain) {
        this.historyManager = historyManager;
        this.brain = brain;
    }

    public void sendPlayerMessage(UUID conversationId, VillagerBrain.Context context, String messageText, UiCallbacks ui) {

        OllamaMod.LOGGER.info("Villager chat [{}|{}] Player: {}", context.worldName(), context.villagerName(), messageText);
        historyManager.append(conversationId, new ChatMessage(ChatRole.PLAYER, messageText));
        ui.onThinkingStarted();

        List<ChatMessage> historySnapshot = historyManager.getHistory(conversationId);

        brain.getReply(context, historySnapshot, messageText)
                .whenComplete((reply, throwable) -> {
                    // Ensure UI updates happen on the Minecraft client thread
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (throwable != null) {
                            OllamaMod.LOGGER.error("Error getting reply from Ollama for conversation {}", context.conversationId(), throwable);
                            ui.onError("Villager is confused (Ollama error). " +
                                       "Is Ollama running at " + OllamaSettings.baseUrl + "?");
                            return;
                        }
                        historyManager.append(conversationId,
                                new ChatMessage(ChatRole.VILLAGER, reply));
                        ui.onVillagerReplyFinished(reply);
                    });
                });
    }

    public List<ChatMessage> getHistory(UUID conversationId) {
        return historyManager.getHistory(conversationId);
    }
}
