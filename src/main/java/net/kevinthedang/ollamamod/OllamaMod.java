package net.kevinthedang.ollamamod;

import com.mojang.logging.LogUtils;
import net.kevinthedang.ollamamod.screen.OllamaVillagerChatScreen;
import net.kevinthedang.ollamamod.vectorstore.VectorStoreService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import net.kevinthedang.ollamamod.chat.ChatHistoryManager;
import net.kevinthedang.ollamamod.chat.AgenticRagVillagerBrain;
import net.kevinthedang.ollamamod.chat.OllamaSettings;
import net.kevinthedang.ollamamod.chat.VillagerChatService;
import net.kevinthedang.ollamamod.vectorstore.embedding.CachingEmbeddingService;
import net.kevinthedang.ollamamod.vectorstore.embedding.OllamaEmbeddingService;
import net.kevinthedang.ollamamod.vectorstore.store.LangChain4jVectorStore;
import net.kevinthedang.ollamamod.vectorstore.chunker.TextChunker;
import net.kevinthedang.ollamamod.vectorstore.chunker.JsonChunker;
import net.kevinthedang.ollamamod.vectorstore.chunker.ConversationChunker;
// The value here should match an entry in the META-INF/mods.toml file
@Mod(OllamaMod.MOD_ID)
public final class OllamaMod {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "ollamamod"; // change to use MOD_ID instead of MODID
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger(); // changed to public

    // Global access points for chat/LLM bridge
    public static final ChatHistoryManager CHAT_HISTORY = new ChatHistoryManager();
    public static final AgenticRagVillagerBrain VILLAGER_BRAIN = new AgenticRagVillagerBrain();
    public static final VillagerChatService CHAT_SERVICE = new VillagerChatService(CHAT_HISTORY, VILLAGER_BRAIN);
    public static final VectorStoreService VECTOR_STORE = new VectorStoreService(
        new CachingEmbeddingService(new OllamaEmbeddingService()),
        new LangChain4jVectorStore(),
        new TextChunker(), new JsonChunker(), new ConversationChunker());

    // Initialize the mod and register configuration + setup hooks.
    public OllamaMod(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        // Register the commonSetup method for modloading
        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    // Run common setup tasks for the mod.
    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));

        // Pre-warm Ollama models so the first player interaction doesn't pay the load cost.
        prewarmOllamaModels();
    }

    // Fire a 1-token throwaway request to each active Ollama model on a background thread.
    // This forces Ollama to load model weights into memory before the player talks to a villager.
    private void prewarmOllamaModels() {
        java.util.Set<String> models = new java.util.LinkedHashSet<>();
        models.add(OllamaSettings.chatModel);
        models.add(OllamaSettings.toolModel);

        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
        String chatUrl = OllamaSettings.baseUrl + "/api/chat";

        for (String model : models) {
            Thread.ofVirtual().name("ollama-prewarm-" + model).start(() -> {
                try {
                    LOGGER.info("[Prewarm] Loading model: {}", model);
                    long start = System.currentTimeMillis();

                    String body = new com.google.gson.Gson().toJson(java.util.Map.of(
                            "model", model,
                            "stream", false,
                            "keep_alive", "30m",
                            "messages", java.util.List.of(
                                    java.util.Map.of("role", "user", "content", "hi")
                            ),
                            "options", java.util.Map.of("num_predict", 1)
                    ));

                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(chatUrl))
                            .header("Content-Type", "application/json")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                            .timeout(java.time.Duration.ofSeconds(120))
                            .build();

                    java.net.http.HttpResponse<String> response =
                            client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

                    long elapsed = System.currentTimeMillis() - start;
                    if (response.statusCode() == 200) {
                        LOGGER.info("[Prewarm] Model {} ready in {}ms", model, elapsed);
                    } else {
                        LOGGER.warn("[Prewarm] Model {} returned HTTP {} in {}ms",
                                model, response.statusCode(), elapsed);
                    }
                } catch (Exception e) {
                    LOGGER.warn("[Prewarm] Failed to warm model {}: {}", model, e.getMessage());
                }
            });
        }
    }


    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {
        // Run client-only setup.
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class VillagerScreenHandler {

        // Stores the entity ID of the last villager the player right-clicked, so the chat screen
        // can lock to the exact entity rather than using the nearest-villager heuristic.
        private static volatile Integer lastInteractedVillagerId = null;

        // Capture the entity ID when the player right-clicks a villager.
        @SubscribeEvent
        public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
            if (event.getTarget() instanceof Villager villager) {
                lastInteractedVillagerId = villager.getId();
                LOGGER.debug("Player interacted with villager entity id={}", lastInteractedVillagerId);
            }
        }

        // Inject the villager chat button into the merchant screen.
        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            if (event.getScreen() instanceof MerchantScreen merchantScreen) {

                int x = merchantScreen.width / 2 + 106;
                int y = merchantScreen.height / 2 - 13;

                Button chatButton = Button.builder(Component.literal("Chat"),button -> handleChatButtonClick(merchantScreen))
                        .pos(x, y)
                        .size(25, 11)
                        .build();
                event.addListener(chatButton);
            }
        }

        // Handle the villager chat button click.
        private static void handleChatButtonClick(MerchantScreen screen) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null) return;

            // Use the entity ID captured at right-click time (more reliable than nearest-villager heuristic).
            Integer villagerEntityId = lastInteractedVillagerId;

            player.displayClientMessage(Component.literal("Opening Villager chat..."), true);
            LOGGER.info("Chat button was clicked! Locking to entity id={}", villagerEntityId);

            screen.onClose();
            mc.setScreen(new OllamaVillagerChatScreen(screen, villagerEntityId));
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class WorldEventHandler {
        // Persist vector store data and chat history when the world is saved.
        @SubscribeEvent
        public static void onWorldSave(LevelEvent.Save event) {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                java.nio.file.Path root = serverLevel.getServer().getWorldPath(LevelResource.ROOT);
                VECTOR_STORE.persistAll(root);
                CHAT_HISTORY.persistAll(root);
                LOGGER.debug("Vector store and chat history persisted");
            }
        }

        // Load vector store data and chat history when the world loads.
        @SubscribeEvent
        public static void onWorldLoad(LevelEvent.Load event) {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                java.nio.file.Path root = serverLevel.getServer().getWorldPath(LevelResource.ROOT);
                VECTOR_STORE.loadAll(root);
                CHAT_HISTORY.loadAll(root);
                if (VECTOR_STORE.count(net.kevinthedang.ollamamod.vectorstore.model.MetadataFilter.all()) == 0) {
                    VECTOR_STORE.loadSeedData();
                }
                LOGGER.debug("Vector store and chat history loaded");
            }
        }

        // Clear in-memory chat history when a world unloads — data is already on disk.
        @SubscribeEvent
        public static void onWorldUnload(LevelEvent.Unload event) {
            if (event.getLevel() instanceof ServerLevel) {
                CHAT_HISTORY.clearAll();
                LOGGER.debug("Chat history cleared on world unload");
            }
        }

        // Unload Ollama models on server shutdown to free VRAM immediately.
        @SubscribeEvent
        public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
            unloadOllamaModels();
        }

        // Send keep_alive:0 to each active model so Ollama evicts it from memory.
        private static void unloadOllamaModels() {
            java.util.Set<String> models = new java.util.LinkedHashSet<>();
            models.add(OllamaSettings.chatModel);
            models.add(OllamaSettings.toolModel);

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(3))
                    .build();
            String chatUrl = OllamaSettings.baseUrl + "/api/chat";

            for (String model : models) {
                try {
                    LOGGER.info("[Shutdown] Unloading model: {}", model);
                    String body = new com.google.gson.Gson().toJson(java.util.Map.of(
                            "model", model,
                            "stream", false,
                            "keep_alive", 0,
                            "messages", java.util.List.of(
                                    java.util.Map.of("role", "user", "content", "bye")
                            ),
                            "options", java.util.Map.of("num_predict", 1)
                    ));

                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(chatUrl))
                            .header("Content-Type", "application/json")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                            .timeout(java.time.Duration.ofSeconds(10))
                            .build();

                    java.net.http.HttpResponse<String> response =
                            client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        LOGGER.info("[Shutdown] Model {} unloaded", model);
                    } else {
                        LOGGER.warn("[Shutdown] Model {} unload returned HTTP {}", model, response.statusCode());
                    }
                } catch (Exception e) {
                    LOGGER.warn("[Shutdown] Failed to unload model {}: {}", model, e.getMessage());
                }
            }
        }
    }
}
