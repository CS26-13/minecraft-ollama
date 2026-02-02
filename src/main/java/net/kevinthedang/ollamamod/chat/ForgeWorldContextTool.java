package net.kevinthedang.ollamamod.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;


public class ForgeWorldContextTool implements WorldContextTool {
    private static final int ENTITY_RADIUS = 24;
    private static final int BLOCK_SCAN_RADIUS = 8;     
    private static final int IMMEDIATE_RADIUS = 2;       
    private static final int MAX_FACTS = 10;

    @Override
    public WorldFactBundle collect(VillagerBrain.Context ctx, List<ChatMessage> history, String playerMessage) {
        List<WorldFact> facts = new ArrayList<>();
        long nowTtl = 5_000;
        long slowTtl = 30_000;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            // Guarantee >=3
            facts.add(fact("Player coordinates: Unknown (no client world)", "forge.coords", 0.2, nowTtl));
            facts.add(fact("Dimension: Unknown (no client world)", "forge.dimension", 0.2, nowTtl));
            facts.add(fact("Time: Unknown (no client world)", "forge.time", 0.2, nowTtl));
            return new WorldFactBundle(trim(facts));
        }

        Level level = mc.level;
        var player = mc.player;

        // Position facts
        BlockPos p = player.blockPosition();
        facts.add(fact("Player coordinates: x=" + p.getX() + ", y=" + p.getY() + ", z=" + p.getZ(),
                "forge.coords", 1.0, nowTtl));

        facts.add(fact("Dimension: " + prettyDimension(level.dimension()),
                "forge.dimension", 1.0, slowTtl));

        facts.add(fact(prettyTime(level),
                "forge.time", 1.0, nowTtl));

        facts.add(fact("Biome: " + tryGetBiomeName(level, p),
                "forge.biome", 0.9, slowTtl));

        facts.add(fact(prettyWeather(level),
                "forge.weather", 0.95, nowTtl));

        // Prefer the villager (persona anchor). If not present, fall back to player.
        Villager anchorVillager = findNearestVillager(level, player.blockPosition(), 12);
        BlockPos anchorPos = (anchorVillager != null) ? anchorVillager.blockPosition() : player.blockPosition();

        if (anchorVillager != null) {
            facts.add(fact("Anchor villager position: x=" + anchorPos.getX() + ", y=" + anchorPos.getY() + ", z=" + anchorPos.getZ(),
                    "forge.villager.anchor", 0.95, nowTtl));
        }

        // Agentic router
        String msg = playerMessage == null ? "" : playerMessage.toLowerCase(Locale.ROOT);

        if (wantsMobs(msg)) {
            facts.addAll(queryNearbyMobs(level, player, ENTITY_RADIUS));
        }

        if (wantsBlocks(msg)) {
            facts.addAll(queryNearbyBlocks(level, anchorPos, BLOCK_SCAN_RADIUS));
        }

        if (wantsImmediateBlocks(msg)) {
            facts.addAll(queryImmediateBlocks(level, anchorPos, player.getDirection()));
        }

        if (wantsStructures(msg)) {
            facts.addAll(queryNearbyStructuresBestEffort(mc, level, p));
        }

        // Enforce cap + ensure >=3
        if (facts.size() < 3) {
            facts.add(fact("World context: (insufficient facts; tool fallback)", "forge.fallback", 0.2, nowTtl));
        }

        return new WorldFactBundle(trim(facts));
    }

    // Query functions
    private static List<WorldFact> queryNearbyMobs(Level level, Entity player, int radius) {
        List<WorldFact> out = new ArrayList<>();
        AABB box = player.getBoundingBox().inflate(radius);
        List<Entity> entities = level.getEntities(player, box);

        int total = entities.size();
        int mobs = 0, hostiles = 0, villagers = 0;

        Map<String, Integer> mobTop = new HashMap<>();
        Map<String, Integer> villagerProf = new HashMap<>();
        Map<String, Integer> villagerType = new HashMap<>();

        for (Entity e : entities) {
            if (e instanceof Mob) mobs++;
            if (e instanceof Monster) hostiles++;

            String typeId = safeId(e);
            mobTop.merge(typeId, 1, Integer::sum);

            if (e instanceof Villager v) {
                villagers++;
                String prof = villagerProfessionId(v);
                String vtype = villagerTypeId(v);
                villagerProf.merge(prof, 1, Integer::sum);
                villagerType.merge(vtype, 1, Integer::sum);

                System.out.println("[ForgeWorldContextTool] villager="
                    + v.getUUID()
                    + " prof=" + prof
                    + " type=" + vtype
                    + " data=" + v.getVillagerData());
            }
        }

        out.add(fact("Nearby entities within " + radius + " blocks: total=" + total + ", mobs=" + mobs + ", hostiles=" + hostiles + ", villagers=" + villagers,
                "forge.entities.scan", 0.95, 3_000));

        out.add(fact("Top nearby entity types: " + topN(mobTop, 5),
                "forge.entities.types", 0.85, 3_000));

        if (!villagerProf.isEmpty()) {
            out.add(fact("Nearby villager professions: " + topN(villagerProf, 5),
                    "forge.villager.professions", 0.9, 10_000));
        }
        if (!villagerType.isEmpty()) {
            out.add(fact("Nearby villager types: " + topN(villagerType, 5),
                    "forge.villager.types", 0.9, 10_000));
        }

        return out;
    }

    private static String villagerProfessionId(Villager v) {
        try {
            var holder = v.getVillagerData().profession(); // Holder<VillagerProfession>
            var profession = holder.value();
            var key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            return key == null ? "unknown" : key.getPath(); // e.g. "farmer"
        } catch (Throwable t) {
            return "unknown";
        }
}

    private static String villagerTypeId(Villager v) {
        try {
            var holder = v.getVillagerData().type(); // Holder<VillagerType>
            var type = holder.value();
            var key = BuiltInRegistries.VILLAGER_TYPE.getKey(type);
            return key == null ? "unknown" : key.getPath(); // e.g. "plains"
        } catch (Throwable t) {
            return "unknown";
        }
    }
    
    private static List<WorldFact> queryNearbyBlocks(Level level, BlockPos center, int radius) {
        List<WorldFact> out = new ArrayList<>();

        Map<String, Integer> counts = new HashMap<>();
        int scanned = 0;
        int air = 0;

        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(level.getMinY(), center.getY() - radius);
        int maxY = Math.min(level.getMaxY() - 1, center.getY() + radius);
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    scanned++;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState st = level.getBlockState(pos);
                    if (st.isAir()) {
                        air++;
                        continue;
                    }
                    String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
                    counts.merge(id, 1, Integer::sum);
                }
            }
        }

        out.add(fact("Block scan around (" + center.getX() + "," + center.getY() + "," + center.getZ() + ") radius=" + radius +
                        ": scanned=" + scanned + " blocks, air=" + air,
                "forge.blocks.scan", 0.9, 8_000));

        out.add(fact("Most common nearby blocks: " + topN(counts, 8),
                "forge.blocks.top", 0.85, 8_000));

        return out;
    }

    private static List<WorldFact> queryImmediateBlocks(Level level, BlockPos anchor, Direction facing) {
        List<WorldFact> out = new ArrayList<>();

        Map<String, String> rel = new LinkedHashMap<>();
        rel.put("underfoot (y-1)", blockId(level, anchor.below()));
        rel.put("at feet (y)", blockId(level, anchor));
        rel.put("above head (y+2)", blockId(level, anchor.above(2)));

        BlockPos front = anchor.relative(facing, 1);
        BlockPos back = anchor.relative(facing.getOpposite(), 1);
        BlockPos left = anchor.relative(facing.getCounterClockWise(), 1);
        BlockPos right = anchor.relative(facing.getClockWise(), 1);

        rel.put("front (" + facing.getName() + ")", blockId(level, front));
        rel.put("back", blockId(level, back));
        rel.put("left", blockId(level, left));
        rel.put("right", blockId(level, right));

        List<String> notable = new ArrayList<>();
        for (int dx = -IMMEDIATE_RADIUS; dx <= IMMEDIATE_RADIUS; dx++) {
            for (int dz = -IMMEDIATE_RADIUS; dz <= IMMEDIATE_RADIUS; dz++) {
                BlockPos p = anchor.offset(dx, 0, dz);
                String ground = blockId(level, p.below());
                String top = blockId(level, p);
                if (ground.contains("lava")) notable.add("lava near anchor (" + dx + "," + dz + ")");
                if (top.contains("snow")) notable.add("snow layer near anchor (" + dx + "," + dz + ")");
                if (top.contains("water")) notable.add("water near anchor (" + dx + "," + dz + ")");
            }
        }

        String relText = rel.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));

        out.add(fact("Immediate blocks relative to anchor: " + relText,
                "forge.blocks.immediate", 0.9, 3_000));

        if (!notable.isEmpty()) {
            out.add(fact("Notable nearby ground conditions: " + String.join("; ", notable.stream().limit(8).toList()),
                    "forge.blocks.notable", 0.8, 3_000));
        }

        return out;
    }

    private static List<WorldFact> queryNearbyStructuresBestEffort(Minecraft mc, Level clientLevel, BlockPos origin) {
        List<WorldFact> out = new ArrayList<>();

        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            out.add(fact("Nearest structures: unavailable (client has no integrated server; multiplayer limitation)",
                    "forge.structures.unavailable", 0.4, 10_000));
            return out;
        }

        ServerLevel serverLevel = server.getLevel(clientLevel.dimension());
        if (serverLevel == null) serverLevel = server.overworld();

        List<String> tagFieldNames = List.of("VILLAGE", "MINESHAFT", "STRONGHOLD");
        List<String> found = new ArrayList<>();

        for (String tagField : tagFieldNames) {
            String res = tryFindNearestStructureByTag(serverLevel, origin, tagField, 128);
            if (res != null) found.add(res);
        }

        if (found.isEmpty()) {
            out.add(fact("Nearest structures: (no result / API mismatch)", "forge.structures.none", 0.5, 20_000));
        } else {
            out.add(fact("Nearest structures (approx): " + String.join(" | ", found),
                    "forge.structures.nearest", 0.85, 20_000));
        }

        return out;
    }

    // Utlities functions
    private static String prettyTime(Level level) {
        long dayTime = level.getDayTime();
        long tod = dayTime % 24000L;
        String part = (tod >= 13000L && tod <= 23000L) ? "Night" : "Day";
        return "Time: " + part + " (tick=" + tod + " / 24000)";
    }

    private static String prettyWeather(Level level) {
        boolean rain = level.isRaining();
        boolean thunder = level.isThundering();
        float rainLevel = level.getRainLevel(1.0F);
        float thunderLevel = level.getThunderLevel(1.0F);

        String state;
        if (thunder) state = "Thunderstorm";
        else if (rain) state = "Raining";
        else state = "Clear";

        return "Weather: " + state + " (rainLevel=" + round2(rainLevel) + ", thunderLevel=" + round2(thunderLevel) + ")";
    }

    private static String tryGetBiomeName(Level level, BlockPos pos) {
        try {
            var holder = level.getBiome(pos);
            var keyOpt = holder.unwrapKey();
            if (keyOpt.isPresent()) return keyOpt.get().location().toString();
            return "Unknown (no biome key)";
        } catch (Throwable t) {
            return "Unknown (biome API mismatch)";
        }
    }

    private static String prettyDimension(ResourceKey<Level> key) {
        if (key == null) return "Unknown";
        ResourceLocation loc = key.location();
        return loc == null ? "Unknown" : loc.toString();
    }

   // Routing route
    private static boolean wantsStructures(String msg) {
        return containsAny(msg, "structure", "village", "stronghold", "mansion", "temple", "mineshaft", "fortress", "nearby structure");
    }

    private static boolean wantsMobs(String msg) {
        return containsAny(msg, "mob", "mobs", "enemy", "enemies", "hostile", "zombie", "skeleton", "creeper", "villager", "nearby");
    }

    private static boolean wantsBlocks(String msg) {
        return containsAny(msg, "block", "blocks", "ore", "wood", "stone", "dirt", "sand", "snow", "lava", "water", "how much", "count");
    }

    private static boolean wantsImmediateBlocks(String msg) {
        return containsAny(msg, "here", "around you", "around us", "under your feet", "ground", "immediate", "snow", "lava", "water", "standing on");
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null) return false;
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }

    // Helpers
    private static WorldFact fact(String text, String evidence, double conf, long ttlMillis) {
        return new WorldFact(text, evidence, conf, ttlMillis);
    }

    private static List<WorldFact> trim(List<WorldFact> facts) {
        if (facts.size() <= MAX_FACTS) return facts;
        return facts.subList(0, MAX_FACTS);
    }

    private static String blockId(Level level, BlockPos pos) {
        try {
            BlockState st = level.getBlockState(pos);
            if (st.isAir()) return "minecraft:air";
            return BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
        } catch (Throwable t) {
            return "unknown:block";
        }
    }

    private static Villager findNearestVillager(Level level, BlockPos center, int radius) {
        try {
            AABB box = new AABB(center).inflate(radius);
            List<Villager> list = level.getEntitiesOfClass(Villager.class, box);
            if (list.isEmpty()) return null;
            Villager best = null;
            double bestDist = Double.MAX_VALUE;
            for (Villager v : list) {
                double d = v.blockPosition().distSqr(center);
                if (d < bestDist) { bestDist = d; best = v; }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String safeId(Entity e) {
        try {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            return id == null ? "unknown" : id.toString();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static String topN(Map<String, Integer> counts, int n) {
        if (counts == null || counts.isEmpty()) return "(none)";
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(n)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private static String parseVillagerDataField(String villagerDataToString, String key, String fallback) {
        if (villagerDataToString == null) return fallback;
        String lower = villagerDataToString.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(key.toLowerCase(Locale.ROOT) + "=");
        if (idx < 0) return fallback;

        int start = idx + (key.length() + 1);
        int end = lower.indexOf(",", start);
        if (end < 0) end = lower.indexOf("]", start);
        if (end < 0) end = villagerDataToString.length();

        String raw = villagerDataToString.substring(start, end).trim();
        // often minecraft:farmer
        if (raw.contains(":")) raw = raw.substring(raw.indexOf(":") + 1);
        if (raw.isEmpty()) return fallback;
        return raw;
    }

    private static String round2(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String tryFindNearestStructureByTag(ServerLevel level, BlockPos origin, String structureTagsField, int radius) {
        try {
            // 1. Load net.minecraft.tags.StructureTags
            Class<?> tagsCls = Class.forName("net.minecraft.tags.StructureTags");

            // 2. Get static field like StructureTags.VILLAGE
            Field f = tagsCls.getField(structureTagsField);
            Object tagKey = f.get(null);
            if (tagKey == null) return null;

            // 3. Find a method named findNearestMapStructure with suitable params
            Method target = null;
            for (Method m : level.getClass().getMethods()) {
                if (!m.getName().equals("findNearestMapStructure")) continue;
                Class<?>[] p = m.getParameterTypes();
                // expecting (TagKey, BlockPos, int, boolean) or similar
                if (p.length == 4 && p[1].getName().equals(BlockPos.class.getName()) && p[2] == int.class && p[3] == boolean.class) {
                    target = m;
                    break;
                }
            }
            if (target == null) return null;

            Object result = target.invoke(level, tagKey, origin, radius, false);
            if (result == null) return null;

            // result is often a Pair<BlockPos, Holder<Structure>>
            // try to extract first() / getFirst()
            BlockPos foundPos = null;
            try {
                Method first = result.getClass().getMethod("getFirst");
                Object fp = first.invoke(result);
                if (fp instanceof BlockPos bp) foundPos = bp;
            } catch (NoSuchMethodException ignored) {
                try {
                    Method first = result.getClass().getMethod("first");
                    Object fp = first.invoke(result);
                    if (fp instanceof BlockPos bp) foundPos = bp;
                } catch (NoSuchMethodException ignored2) {
                    // Some versions return just BlockPos directly
                    if (result instanceof BlockPos bp) foundPos = bp;
                }
            }

            if (foundPos == null) return null;

            double dist = Math.sqrt(foundPos.distSqr(origin));
            return structureTagsField.toLowerCase(Locale.ROOT) + " @ x=" + foundPos.getX() + ",y=" + foundPos.getY() + ",z=" + foundPos.getZ() + " (≈" + (int) dist + "m)";

        } catch (Throwable t) {
            return null;
        }
    }
}