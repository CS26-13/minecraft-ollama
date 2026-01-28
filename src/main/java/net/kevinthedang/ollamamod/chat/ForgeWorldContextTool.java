package net.kevinthedang.ollamamod.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class ForgeWorldContextTool implements WorldContextTool {
    private static final double SCAN_RADIUS_BLOCKS = 24.0;

    @Override
    public WorldFactBundle collect(VillagerBrain.Context ctx, List<ChatMessage> history, String playerMessage) {
        List<WorldFact> facts = new ArrayList<>();

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            facts.add(WorldFact.of("Player position: Unknown (no client world)", "forge", 0.2));
            facts.add(WorldFact.of("Dimension: Unknown (no client world)", "forge", 0.2));
            facts.add(WorldFact.of("Time: Unknown (no client world)", "forge", 0.2));
            return new WorldFactBundle(facts);
        }

        Level level = mc.level;
        var player = mc.player;

        // 1. Coords
        BlockPos p = player.blockPosition();
        facts.add(WorldFact.of(
                "Player coordinates: x=" + p.getX() + ", y=" + p.getY() + ", z=" + p.getZ(),
                "forge", 1.0
        ));

        // 2. Dimension
        String dimension = prettyDimension(level.dimension());
        facts.add(WorldFact.of("Dimension: " + dimension, "forge", 1.0));

        // 3. Time (day/night)
        long dayTime = level.getDayTime();              // total ticks
        long timeOfDay = dayTime % 24000L;              // 0..23999
        String dayPart = isNight(timeOfDay) ? "Night" : "Day";
        facts.add(WorldFact.of(
                "Time: " + dayPart + " (tick=" + timeOfDay + " / 24000)",
                "forge", 1.0
        ));

        // 4. Biome (best-effort; API can vary slightly by mappings)
        String biome = tryGetBiomeName(level, p);
        facts.add(WorldFact.of("Biome: " + biome, "forge", biome.startsWith("Unknown") ? 0.5 : 1.0));

        // 5. Nearby entity counts (cheap scan)
        AABB box = player.getBoundingBox().inflate(SCAN_RADIUS_BLOCKS);
        List<Entity> entities = level.getEntities(player, box);

        int villagerCount = 0;
        int hostileCount = 0;
        int mobCount = 0;

        for (Entity e : entities) {
            if (e instanceof Villager) villagerCount++;
            if (e instanceof Monster) hostileCount++;
            if (e instanceof Mob) mobCount++;
        }

        facts.add(WorldFact.of("Nearby entities within " + (int) SCAN_RADIUS_BLOCKS + " blocks: total=" + entities.size(), "forge", 0.9));
        facts.add(WorldFact.of("Nearby mobs within " + (int) SCAN_RADIUS_BLOCKS + " blocks: mobs=" + mobCount + ", hostiles=" + hostileCount + ", villagers=" + villagerCount, "forge", 0.9));

        // Ensure at least 3 facts (if others failed)
        while (facts.size() < 3) {
            facts.add(WorldFact.of("World context: (additional facts unavailable)", "forge", 0.2));
        }

        return new WorldFactBundle(facts);
    }

    private static boolean isNight(long timeOfDay) {
        // night spans roughly 13000..23000
        return timeOfDay >= 13000L && timeOfDay <= 23000L;
    }

    private static String prettyDimension(ResourceKey<Level> dimKey) {
        if (dimKey == null) return "Unknown";
        ResourceLocation loc = dimKey.location(); // e.g., minecraft:overworld
        if (loc == null) return "Unknown";
        return loc.toString();
    }

    private static String tryGetBiomeName(Level level, BlockPos pos) {
        try {
            // Many mappings expose biome holder with unwrapKey
            var holder = level.getBiome(pos);
            var keyOpt = holder.unwrapKey();
            if (keyOpt.isPresent()) {
                return keyOpt.get().location().toString(); // e.g., minecraft:plains
            }
            return "Unknown (no key)";
        } catch (Throwable t) {
            return "Unknown (biome API mismatch)";
        }
    }
}