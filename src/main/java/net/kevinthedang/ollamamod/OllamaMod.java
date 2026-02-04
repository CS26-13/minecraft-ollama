package net.kevinthedang.ollamamod;

import com.mojang.logging.LogUtils;
import net.kevinthedang.ollamamod.screen.OllamaVillagerChatScreen;
import net.kevinthedang.ollamamod.vectorstore.VectorStoreService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import net.kevinthedang.ollamamod.chat.ChatHistoryManager;
import net.kevinthedang.ollamamod.chat.AgenticRagVillagerBrain;
import net.kevinthedang.ollamamod.chat.VillagerChatService;
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
    public static final VectorStoreService VECTOR_STORE = new VectorStoreService();

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
            // button click handler
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player != null) {
                player.displayClientMessage(Component.literal("Opening Villager chat..."), true);
                LOGGER.info("Chat button was clicked!");

                screen.onClose();
                mc.setScreen(new OllamaVillagerChatScreen(screen));
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class WorldEventHandler {
        // Persist vector store data when the world is saved.
        @SubscribeEvent
        public static void onWorldSave(LevelEvent.Save event) {
            if (!event.getLevel().isClientSide()) {
                VECTOR_STORE.persistAll();
                LOGGER.debug("Vector store persisted");
            }
        }

        // Load vector store data (including seed data) when the world loads.
        @SubscribeEvent
        public static void onWorldLoad(LevelEvent.Load event) {
            if (!event.getLevel().isClientSide()) {
                VECTOR_STORE.loadAll();
                VECTOR_STORE.loadSeedData();
                LOGGER.debug("Vector store loaded");
            }
        }
    }
}
