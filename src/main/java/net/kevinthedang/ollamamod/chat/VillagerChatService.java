package net.kevinthedang.ollamamod.chat;

import net.kevinthedang.ollamamod.OllamaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;


import java.util.List;
import java.util.UUID;

public class VillagerChatService {
    private final ChatHistoryManager historyManager;
    private final VillagerBrain brain;

    public interface UiCallbacks {
        void onThinkingStarted();

        default void onVillagerReplyDelta(String delta) {
            // Called when a chunk of the villager's reply arrives.
        }

        void onVillagerReplyFinished(String fullText);

        void onError(String errorMessage);
    }

    public VillagerChatService(ChatHistoryManager historyManager, VillagerBrain brain) {
        this.historyManager = historyManager;
        this.brain = brain;
    }

    public void sendPlayerMessage(
            UUID conversationId,
            VillagerBrain.Context context,
            String messageText,
            UiCallbacks ui,
            Boolean isStreaming,
            Vec3 villagerSoundPos
    ) {

        OllamaMod.LOGGER.info("Villager chat [{}|{}] Player: {}", context.worldName(), context.villagerName(), messageText);
        historyManager.append(conversationId, new ChatMessage(ChatRole.PLAYER, messageText));
        ui.onThinkingStarted();

        List<ChatMessage> historySnapshot = historyManager.getHistory(conversationId);

        if (isStreaming == false) {
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
                            // play once when the reply arrives
                            if (villagerSoundPos != null && reply != null && !reply.isEmpty()) {
                                playVillagerReplySound(villagerSoundPos);
                            }
                            ui.onVillagerReplyFinished(reply);
                        });
                    });
        } else {
            brain.getReplyStreaming(context, historySnapshot, messageText, new VillagerBrain.StreamCallbacks() {
                private boolean startedSound = false;

                @Override
                public void onDelta(String delta) {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        // Play once when the villager begins "speaking" (first streamed chunk)
                        if (!startedSound && villagerSoundPos != null && delta != null && !delta.isEmpty()) {
                            startedSound = true;
                            playVillagerReplySound(villagerSoundPos);
                        }
                        ui.onVillagerReplyDelta(delta);
                    });
                }

                @Override
                public void onCompleted(String fullReply) {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        historyManager.append(conversationId, new ChatMessage(ChatRole.VILLAGER, fullReply));
                        ui.onVillagerReplyFinished(fullReply);
                    });
                }

                @Override
                public void onError(Throwable t) {
                    OllamaMod.LOGGER.error("Error getting reply from Ollama for conversation {}",
                            context.conversationId(), t);

                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> ui.onError(
                            "Villager is confused (Ollama error). " +
                            "Is Ollama running at " + OllamaSettings.baseUrl + "?"
                    ));
                }
            });
        }
    }

    /**
     * Plays a short villager talking sound when a response appears
     */
    private void playVillagerReplySound(Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        mc.level.playLocalSound(
                pos.x, pos.y, pos.z,
                SoundEvents.VILLAGER_YES,
                SoundSource.NEUTRAL,
                1.0f,
                1.0f,
                false
        );
    }


    public List<ChatMessage> getHistory(UUID conversationId) {
        return historyManager.getHistory(conversationId);
    }
}
