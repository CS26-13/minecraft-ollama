package net.kevinthedang.ollamamod.chat;

import java.util.*;

/**
 * Composes natural-language scene descriptions from raw block/entity scan data.
 * Takes plain Java types only — no Minecraft imports — so it's fully unit-testable.
 */
class SceneDescriptionBuilder {

	/** Scene categories for villager-meaningful environment descriptions. */
	enum SceneKind {
		OPEN_FIELD,
		FOREST,
		BEACH,
		MOUNTAIN,
		CAVE,
		DEEP_UNDERGROUND,
		INSIDE_WOOD_STRUCTURE,
		INSIDE_STONE_STRUCTURE,
		INSIDE_GLASS_STRUCTURE,
		UNDER_TREE_CANOPY,
		UNDER_OVERHANG,
		VILLAGE_SQUARE,
		IN_SHALLOW_WATER,
		IN_DEEP_WATER,
		IN_LAVA,
		UNKNOWN
	}

	// A block found during scanning with its relative position to the anchor
	record BlockInfo(String blockId, int count, int dx, int dy, int dz) {}

	// An entity found nearby with its relative position
	record EntityInfo(String type, String profession, int dx, int dy, int dz) {}

	private static final int MAX_NOTABLE_ENTRIES = 6;

	// Blocks considered generic ground/filler — omit from notable descriptions
	private static final Set<String> GROUND_BLOCKS = Set.of(
			"minecraft:dirt", "minecraft:grass_block", "minecraft:coarse_dirt",
			"minecraft:podzol", "minecraft:mycelium", "minecraft:rooted_dirt",
			"minecraft:mud", "minecraft:sand", "minecraft:red_sand",
			"minecraft:gravel", "minecraft:clay", "minecraft:stone",
			"minecraft:cobblestone", "minecraft:deepslate",
			"minecraft:cobbled_deepslate", "minecraft:netherrack",
			"minecraft:end_stone", "minecraft:soul_sand", "minecraft:soul_soil",
			"minecraft:bedrock", "minecraft:snow_block"
	);

	// Blocks that are "filler" underground — skip when count is very high
	private static final Set<String> UNDERGROUND_FILLER = Set.of(
			"minecraft:bedrock", "minecraft:stone", "minecraft:deepslate",
			"minecraft:cobbled_deepslate", "minecraft:tuff", "minecraft:granite",
			"minecraft:diorite", "minecraft:andesite"
	);

	// Hostile mob types
	private static final Set<String> HOSTILE_TYPES = Set.of(
			"minecraft:zombie", "minecraft:skeleton", "minecraft:creeper",
			"minecraft:spider", "minecraft:enderman", "minecraft:witch",
			"minecraft:phantom", "minecraft:drowned", "minecraft:husk",
			"minecraft:stray", "minecraft:blaze", "minecraft:ghast",
			"minecraft:wither_skeleton", "minecraft:piglin_brute",
			"minecraft:vindicator", "minecraft:evoker", "minecraft:ravager",
			"minecraft:pillager", "minecraft:guardian", "minecraft:elder_guardian",
			"minecraft:warden", "minecraft:breeze"
	);

	// Passive mobs that should be grouped when multiple
	private static final Set<String> PASSIVE_TYPES = Set.of(
			"minecraft:cow", "minecraft:sheep", "minecraft:pig",
			"minecraft:chicken", "minecraft:horse", "minecraft:donkey",
			"minecraft:rabbit", "minecraft:cat", "minecraft:wolf",
			"minecraft:fox", "minecraft:parrot", "minecraft:bee",
			"minecraft:goat", "minecraft:frog", "minecraft:turtle",
			"minecraft:squid", "minecraft:glow_squid", "minecraft:dolphin",
			"minecraft:bat", "minecraft:cod", "minecraft:salmon",
			"minecraft:tropical_fish", "minecraft:pufferfish",
			"minecraft:armadillo", "minecraft:camel", "minecraft:sniffer"
	);

	// Irregular plurals — same word for singular and plural
	private static final Set<String> UNCOUNTABLE = Set.of(
			"sheep", "cod", "salmon", "squid", "glow squid"
	);

	/**
	 * Converts world dx/dz to a viewer-relative direction using the anchor's yaw.
	 * Minecraft yaw: 0=south(+Z), 90=west(-X), 180=north(-Z), 270=east(+X).
	 * Returns one of 8 labels: "ahead", "to your left", "behind and to your right", etc.
	 */
	private static String relativeDirection(float viewerYawDegrees, int dx, int dz) {
		double yawRad = Math.toRadians(viewerYawDegrees);
		// Rotate (dx, dz) by -yaw into viewer-local frame
		// With yaw=0 (south): +localX = east = viewer's left, +localZ = south = forward
		double localX = dx * Math.cos(yawRad) + dz * Math.sin(yawRad);
		double localZ = -dx * Math.sin(yawRad) + dz * Math.cos(yawRad);
		// atan2(localX, localZ): 0 = ahead, positive = left, negative = right
		double angle = Math.toDegrees(Math.atan2(localX, localZ));
		if (angle < 0) angle += 360;
		// 8 buckets, 45 degrees each
		if (angle < 22.5 || angle >= 337.5) return "ahead of you";
		if (angle < 67.5)  return "ahead and to your left";
		if (angle < 112.5) return "to your left";
		if (angle < 157.5) return "behind and to your left";
		if (angle < 202.5) return "behind you";
		if (angle < 247.5) return "behind and to your right";
		if (angle < 292.5) return "to your right";
		return "ahead and to your right";
	}

	/** Qualitative distance bucket from dx/dy/dz offsets. */
	private static String qualitativeDistance(int dx, int dy, int dz) {
		int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
		if (dist <= 2) return "right beside you";
		if (dist <= 6) return "close at hand";
		if (dist <= 15) return "a short walk away";
		if (dist <= 30) return "some distance away";
		return "far off in the distance";
	}

	/** Returns a count qualifier word, or empty string for singular (use "A"/"An" instead). */
	private static String countQualifier(int count) {
		if (count <= 1) return "";
		if (count == 2) return "Two";
		if (count == 3) return "Three";
		if (count <= 6) return "A few";
		if (count <= 15) return "Several";
		return "Many";
	}

	/** Pluralizes a name if count > 1, handling irregular forms. */
	private static String pluralize(String name, int count) {
		if (count <= 1 || UNCOUNTABLE.contains(name)) return name;
		return name + "s";
	}

	/** Returns "a" or "an" based on the first letter of the noun. */
	private static String aOrAn(String noun) {
		if (noun == null || noun.isEmpty()) return "a";
		char first = Character.toLowerCase(noun.charAt(0));
		return (first == 'a' || first == 'e' || first == 'i' || first == 'o' || first == 'u') ? "an" : "a";
	}

	/**
	 * Produces a natural direction phrase from dx/dy/dz offsets using viewer-relative directions.
	 * Backwards-compatible overload using yaw=0 (facing south).
	 */
	static String naturalDirection(int dx, int dy, int dz) {
		return naturalDirection(dx, dy, dz, 0f);
	}

	/**
	 * Produces a villager-compatible direction phrase from dx/dy/dz offsets.
	 * Uses qualitative distances and viewer-relative directions based on anchor yaw.
	 * Examples: "right next to you", "close at hand, ahead of you",
	 * "a short walk away, to your left and below"
	 */
	static String naturalDirection(int dx, int dy, int dz, float viewerYaw) {
		int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));

		if (dist <= 1) return "right next to you";

		// Check if mostly vertical
		int horizDist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		int absDy = Math.abs(dy);
		if (absDy > 2 && horizDist <= 2) {
			return dy > 0 ? "just above you" : "just below your feet";
		}

		// Qualitative distance
		String distPhrase = qualitativeDistance(dx, dy, dz);

		// Viewer-relative horizontal direction
		String dirPhrase;
		if (horizDist <= 2) {
			dirPhrase = "";
		} else {
			dirPhrase = relativeDirection(viewerYaw, dx, dz);
		}

		// Vertical suffix
		String vert = "";
		if (dy > 2) vert = " and above";
		else if (dy < -2) vert = " and below";

		if (dirPhrase.isEmpty()) {
			return distPhrase + vert;
		}
		return distPhrase + ", " + dirPhrase + vert;
	}

	/** Returns a villager-friendly base description for the given scene kind. */
	static String sceneDescription(SceneKind kind) {
		return switch (kind) {
			case OPEN_FIELD -> "You stand in an open field, the sky wide above you";
			case FOREST -> "You are among the trees, branches rustling overhead";
			case BEACH -> "You stand on the sandy shore, water lapping nearby";
			case MOUNTAIN -> "You stand high on a rocky slope";
			case CAVE -> "You are in a cavern, stone walls close around you";
			case DEEP_UNDERGROUND -> "You are deep beneath the surface, far from the light of day";
			case INSIDE_WOOD_STRUCTURE -> "You are inside a wooden building";
			case INSIDE_STONE_STRUCTURE -> "You are inside a stone building";
			case INSIDE_GLASS_STRUCTURE -> "You are beneath a glass roof, the sky dim above";
			case UNDER_TREE_CANOPY -> "You stand beneath the leafy canopy of a tree";
			case UNDER_OVERHANG -> "You stand beneath a rocky overhang";
			case VILLAGE_SQUARE -> "You stand in the heart of the village";
			case IN_SHALLOW_WATER -> "You wade in shallow water, the surface just above";
			case IN_DEEP_WATER -> "You are deep beneath the water";
			case IN_LAVA -> "You are engulfed in lava";
			case UNKNOWN -> "You stand in an unfamiliar place";
		};
	}

	/**
	 * Builds a 1-2 sentence description of immediate surroundings.
	 *
	 * @param kind the detected scene category
	 * @param immediateBlocks tier-1 block scan results
	 */
	static String buildSurroundings(SceneKind kind, List<BlockInfo> immediateBlocks) {
		if (immediateBlocks == null || immediateBlocks.isEmpty()) {
			return sceneDescription(kind) + ".";
		}

		// Categorize blocks
		List<String> treeTypes = new ArrayList<>();
		List<String> functionalNames = new ArrayList<>();

		for (BlockInfo b : immediateBlocks) {
			String id = b.blockId();
			String pretty = prettyBlockName(id);

			if (GROUND_BLOCKS.contains(id)) {
				// Ground info is now captured by the scene kind — skip
			} else if (id.contains("_log")) {
				String wood = pretty.replace(" log", "");
				if (!treeTypes.contains(wood)) {
					treeTypes.add(wood);
				}
			} else if (id.contains("_leaves") || id.contains("_sapling")) {
				// Skip — implied by trees
			} else if (isFunctionalBlock(id)) {
				functionalNames.add(pretty);
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append(sceneDescription(kind));

		// Append tree context if the scene kind doesn't already mention trees
		if (!treeTypes.isEmpty() && kind != SceneKind.FOREST && kind != SceneKind.UNDER_TREE_CANOPY) {
			sb.append(", with ").append(joinNatural(treeTypes)).append(" trees nearby");
		} else if (!treeTypes.isEmpty()) {
			// Scene already mentions trees — add specific types
			sb.append(". ").append(capitalizeFirst(joinNatural(treeTypes))).append(" trees grow close by");
		}
		sb.append(".");

		// Functional blocks
		if (!functionalNames.isEmpty()) {
			sb.append(" ");
			if (functionalNames.size() == 1) {
				sb.append("A ").append(functionalNames.get(0)).append(" is within arm's reach.");
			} else {
				sb.append(capitalizeFirst(joinNatural(functionalNames))).append(" are within arm's reach.");
			}
		}

		return sb.toString();
	}

	/**
	 * Builds a bullet-point list of notable/rare blocks with natural directions.
	 * Backwards-compatible overload using yaw=0.
	 */
	static String buildNotableBlocks(List<BlockInfo> notableBlocks) {
		return buildNotableBlocks(notableBlocks, 0f);
	}

	/**
	 * Builds a bullet-point list of notable/rare blocks with villager-relative directions.
	 * Collapses duplicate block types into counted entries, filters underground filler,
	 * and sorts by distance.
	 */
	static String buildNotableBlocks(List<BlockInfo> notableBlocks, float viewerYaw) {
		if (notableBlocks == null || notableBlocks.isEmpty()) return "";

		// Filter out underground filler with high counts
		List<BlockInfo> filtered = notableBlocks.stream()
				.filter(b -> !(UNDERGROUND_FILLER.contains(b.blockId()) && b.count() > 10))
				.toList();

		if (filtered.isEmpty()) return "";

		// Merge by blockId: sum counts, keep nearest position
		Map<String, int[]> merged = new LinkedHashMap<>();
		for (BlockInfo b : filtered) {
			int distSq = b.dx() * b.dx() + b.dy() * b.dy() + b.dz() * b.dz();
			int[] existing = merged.get(b.blockId());
			if (existing == null) {
				merged.put(b.blockId(), new int[]{b.count(), b.dx(), b.dy(), b.dz(), distSq});
			} else {
				existing[0] += b.count();
				if (distSq < existing[4]) {
					existing[1] = b.dx();
					existing[2] = b.dy();
					existing[3] = b.dz();
					existing[4] = distSq;
				}
			}
		}

		// Sort by nearest distance, limit
		List<Map.Entry<String, int[]>> sorted = merged.entrySet().stream()
				.sorted(Comparator.comparingInt(e -> e.getValue()[4]))
				.limit(MAX_NOTABLE_ENTRIES)
				.toList();

		StringBuilder sb = new StringBuilder();
		for (var entry : sorted) {
			sb.append("\n- ");
			String pretty = prettyBlockName(entry.getKey());
			int totalCount = entry.getValue()[0];
			int dx = entry.getValue()[1], dy = entry.getValue()[2], dz = entry.getValue()[3];

			String qualifier = countQualifier(totalCount);
			if (qualifier.isEmpty()) {
				sb.append(capitalizeFirst(aOrAn(pretty))).append(" ").append(pretty);
			} else {
				sb.append(qualifier).append(" ").append(pluralize(pretty, totalCount));
			}
			sb.append(", ").append(naturalDirection(dx, dy, dz, viewerYaw));
		}
		return sb.toString();
	}

	/**
	 * Builds a bullet-point list of nearby entities with natural directions.
	 * Backwards-compatible overload using yaw=0.
	 */
	static String buildEntities(List<EntityInfo> entities) {
		return buildEntities(entities, 0f);
	}

	/**
	 * Builds a bullet-point list of nearby entities with villager-relative directions.
	 * Groups entities by type with count qualifiers.
	 */
	static String buildEntities(List<EntityInfo> entities, float viewerYaw) {
		if (entities == null || entities.isEmpty()) return "";

		StringBuilder sb = new StringBuilder();

		// Separate by type
		List<EntityInfo> villagers = new ArrayList<>();
		Map<String, List<EntityInfo>> hostileGroups = new LinkedHashMap<>();
		Map<String, List<EntityInfo>> passiveGroups = new LinkedHashMap<>();
		List<EntityInfo> other = new ArrayList<>();

		for (EntityInfo e : entities) {
			if ("minecraft:villager".equals(e.type())) {
				villagers.add(e);
			} else if (HOSTILE_TYPES.contains(e.type())) {
				hostileGroups.computeIfAbsent(e.type(), k -> new ArrayList<>()).add(e);
			} else if (PASSIVE_TYPES.contains(e.type())) {
				passiveGroups.computeIfAbsent(e.type(), k -> new ArrayList<>()).add(e);
			} else {
				other.add(e);
			}
		}

		// Villagers — individual with profession (profession matters, so keep separate)
		for (EntityInfo v : villagers) {
			String prof = (v.profession() != null && !v.profession().isBlank())
					? v.profession() + " villager"
					: "villager";
			sb.append("\n- A ").append(prof).append(", ")
					.append(naturalDirection(v.dx(), v.dy(), v.dz(), viewerYaw));
		}

		// Hostiles — grouped by type with count qualifier
		for (var entry : hostileGroups.entrySet()) {
			appendEntityGroup(sb, prettyEntityName(entry.getKey()), entry.getValue(), viewerYaw);
		}

		// Passive — grouped by type with count qualifier
		for (var entry : passiveGroups.entrySet()) {
			appendEntityGroup(sb, prettyEntityName(entry.getKey()), entry.getValue(), viewerYaw);
		}

		return sb.toString();
	}

	/** Appends a grouped entity line with count qualifier and average direction. */
	private static void appendEntityGroup(StringBuilder sb, String name, List<EntityInfo> group, float viewerYaw) {
		int avgDx = (int) group.stream().mapToInt(EntityInfo::dx).average().orElse(0);
		int avgDy = (int) group.stream().mapToInt(EntityInfo::dy).average().orElse(0);
		int avgDz = (int) group.stream().mapToInt(EntityInfo::dz).average().orElse(0);
		String qualifier = countQualifier(group.size());
		if (qualifier.isEmpty()) {
			sb.append("\n- A ").append(name).append(", ");
		} else {
			sb.append("\n- ").append(qualifier).append(" ").append(pluralize(name, group.size())).append(", ");
		}
		sb.append(naturalDirection(avgDx, avgDy, avgDz, viewerYaw));
	}

	// Checks if a block is functional (workstation, container, etc.) vs ore/hazard
	private static boolean isFunctionalBlock(String blockId) {
		return blockId.contains("crafting_table") || blockId.contains("furnace")
				|| blockId.contains("smoker") || blockId.contains("anvil")
				|| blockId.contains("enchanting_table") || blockId.contains("brewing_stand")
				|| blockId.contains("chest") || blockId.contains("barrel")
				|| blockId.contains("beacon") || blockId.contains("lectern")
				|| blockId.contains("composter") || blockId.contains("loom")
				|| blockId.contains("stonecutter") || blockId.contains("grindstone")
				|| blockId.contains("cartography_table") || blockId.contains("fletching_table")
				|| blockId.contains("smithing_table") || blockId.contains("campfire")
				|| blockId.contains("bed");
	}

	// Converts "minecraft:deepslate_diamond_ore" to "deepslate diamond ore"
	static String prettyBlockName(String fullId) {
		if (fullId == null) return "unknown";
		String name = fullId.startsWith("minecraft:") ? fullId.substring("minecraft:".length()) : fullId;
		return name.replace('_', ' ');
	}

	// Converts "minecraft:zombie" to "zombie"
	private static String prettyEntityName(String fullId) {
		if (fullId == null) return "unknown";
		String name = fullId.startsWith("minecraft:") ? fullId.substring("minecraft:".length()) : fullId;
		return name.replace('_', ' ');
	}

	// Joins a list naturally: ["a", "b", "c"] → "a, b, and c"
	private static String joinNatural(List<String> items) {
		if (items.isEmpty()) return "";
		if (items.size() == 1) return items.get(0);
		if (items.size() == 2) return items.get(0) + " and " + items.get(1);
		return String.join(", ", items.subList(0, items.size() - 1))
				+ ", and " + items.get(items.size() - 1);
	}

	private static String capitalizeFirst(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
