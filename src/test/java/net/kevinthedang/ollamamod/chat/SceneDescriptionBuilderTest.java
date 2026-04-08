package net.kevinthedang.ollamamod.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SceneDescriptionBuilderTest {

	// === naturalDirection() tests (zero-arg overload → yaw=0, facing south) ===
	// With yaw=0: +Z=ahead, -Z=behind, +X=to your left, -X=to your right

	@Test
	void nearbyWhenAllAxesSmall() {
		String result = SceneDescriptionBuilder.naturalDirection(1, 0, 1);
		assertEquals("right next to you", result);
	}

	@Test
	void nearbyWhenZero() {
		String result = SceneDescriptionBuilder.naturalDirection(0, 0, 0);
		assertEquals("right next to you", result);
	}

	@Test
	void behindWhenNegativeZ() {
		// -Z = behind when facing south (yaw=0)
		String result = SceneDescriptionBuilder.naturalDirection(0, 0, -10);
		assertTrue(result.contains("behind"), "should contain behind: " + result);
		assertTrue(result.contains("a short walk away"), "dist 10 should be 'a short walk away': " + result);
	}

	@Test
	void aheadWhenPositiveZ() {
		// +Z = ahead when facing south (yaw=0)
		String result = SceneDescriptionBuilder.naturalDirection(0, 0, 8);
		assertTrue(result.contains("ahead"), "should contain ahead: " + result);
		assertTrue(result.contains("a short walk away"), "dist 8 should be 'a short walk away': " + result);
	}

	@Test
	void leftWhenPositiveX() {
		// +X = to your left when facing south (yaw=0)
		String result = SceneDescriptionBuilder.naturalDirection(5, 0, 0);
		assertTrue(result.contains("to your left"), "should contain 'to your left': " + result);
		assertTrue(result.contains("close at hand"), "dist 5 should be 'close at hand': " + result);
	}

	@Test
	void aheadAndLeftCombined() {
		// +X, +Z = ahead and to your left
		String result = SceneDescriptionBuilder.naturalDirection(8, 0, 6);
		assertTrue(result.contains("ahead") && result.contains("left"),
				"should contain ahead and left: " + result);
	}

	@Test
	void aheadAndLeftAndBelowCombined() {
		String result = SceneDescriptionBuilder.naturalDirection(8, -5, 6);
		assertTrue(result.contains("left") || result.contains("ahead"),
				"should contain a relative direction: " + result);
		assertTrue(result.contains("below"), "should contain below: " + result);
	}

	@Test
	void justBelowWhenMostlyVertical() {
		String result = SceneDescriptionBuilder.naturalDirection(1, -8, 0);
		assertEquals("just below your feet", result);
	}

	@Test
	void justAboveWhenMostlyVertical() {
		String result = SceneDescriptionBuilder.naturalDirection(0, 6, 1);
		assertEquals("just above you", result);
	}

	@Test
	void behindAndRightAndAbove() {
		// -X, -Z = behind and to your right
		String result = SceneDescriptionBuilder.naturalDirection(-10, 5, -10);
		assertTrue(result.contains("behind") && result.contains("right"),
				"should contain behind and right: " + result);
		assertTrue(result.contains("above"), "should contain above: " + result);
	}

	// === naturalDirection with explicit yaw tests ===

	@Test
	void yaw90FacingWestForwardIsNegativeX() {
		// yaw=90 = facing west (-X). Entity at dx=-10, dz=0 is ahead.
		String result = SceneDescriptionBuilder.naturalDirection(-10, 0, 0, 90f);
		assertTrue(result.contains("ahead"), "entity at -X should be ahead when facing west: " + result);
	}

	@Test
	void yaw180FacingNorthForwardIsNegativeZ() {
		// yaw=180 = facing north (-Z). Entity at dx=0, dz=-10 is ahead.
		String result = SceneDescriptionBuilder.naturalDirection(0, 0, -10, 180f);
		assertTrue(result.contains("ahead"), "entity at -Z should be ahead when facing north: " + result);
	}

	@Test
	void yaw270FacingEastForwardIsPositiveX() {
		// yaw=270 = facing east (+X). Entity at dx=10, dz=0 is ahead.
		String result = SceneDescriptionBuilder.naturalDirection(10, 0, 0, 270f);
		assertTrue(result.contains("ahead"), "entity at +X should be ahead when facing east: " + result);
	}

	// === qualitative distance tests ===

	@Test
	void qualitativeDistanceBuckets() {
		// dist 2 → right next to you (handled by dist<=1 check... actually dist=2)
		String close = SceneDescriptionBuilder.naturalDirection(2, 0, 0);
		// dist=2, horizDist=2, which is <=2, so dirPhrase="" → just distance
		assertTrue(close.contains("right beside you"), "dist 2 should be 'right beside you': " + close);

		// dist 5 → close at hand
		String near = SceneDescriptionBuilder.naturalDirection(0, 0, 5);
		assertTrue(near.contains("close at hand"), "dist 5 should be 'close at hand': " + near);

		// dist 20 → some distance away
		String far = SceneDescriptionBuilder.naturalDirection(0, 0, 20);
		assertTrue(far.contains("some distance away"), "dist 20 should be 'some distance away': " + far);

		// dist 40 → far off in the distance
		String veryFar = SceneDescriptionBuilder.naturalDirection(0, 0, 40);
		assertTrue(veryFar.contains("far off"), "dist 40 should be 'far off': " + veryFar);
	}

	// === "to the nearby" bug is fixed ===

	@Test
	void noMoreToTheNearbyBug() {
		// dx=2, dz=2, dist~3 — previously produced "about 3 blocks to the nearby"
		String result = SceneDescriptionBuilder.naturalDirection(2, 0, 2);
		assertFalse(result.contains("to the nearby"), "should not contain 'to the nearby': " + result);
	}

	// === sceneDescription() tests ===

	@Test
	void sceneDescriptionCoversAllKinds() {
		// Every SceneKind should return a non-empty description
		for (SceneDescriptionBuilder.SceneKind kind : SceneDescriptionBuilder.SceneKind.values()) {
			String desc = SceneDescriptionBuilder.sceneDescription(kind);
			assertNotNull(desc, "description should not be null for " + kind);
			assertFalse(desc.isEmpty(), "description should not be empty for " + kind);
		}
	}

	@Test
	void sceneDescriptionOpenField() {
		String desc = SceneDescriptionBuilder.sceneDescription(SceneDescriptionBuilder.SceneKind.OPEN_FIELD);
		assertTrue(desc.contains("open field"), "OPEN_FIELD description: " + desc);
	}

	@Test
	void sceneDescriptionCave() {
		String desc = SceneDescriptionBuilder.sceneDescription(SceneDescriptionBuilder.SceneKind.CAVE);
		assertTrue(desc.contains("cavern"), "CAVE description: " + desc);
	}

	// === buildSurroundings() tests ===

	@Test
	void openFieldWithTreesAndFunctionalBlocks() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = List.of(
				new SceneDescriptionBuilder.BlockInfo("minecraft:dirt", 35, 0, -1, 0),
				new SceneDescriptionBuilder.BlockInfo("minecraft:oak_log", 33, 1, 0, 1),
				new SceneDescriptionBuilder.BlockInfo("minecraft:grass_block", 8, 0, 0, 1),
				new SceneDescriptionBuilder.BlockInfo("minecraft:brewing_stand", 1, 1, 0, 0)
		);
		String result = SceneDescriptionBuilder.buildSurroundings(
				SceneDescriptionBuilder.SceneKind.OPEN_FIELD, blocks);

		assertTrue(result.contains("open field"), "should mention open field: " + result);
		assertTrue(result.contains("oak"), "should mention oak trees: " + result);
		assertTrue(result.contains("brewing stand"), "should mention brewing stand: " + result);
		assertTrue(result.contains("arm's reach"), "should mention arm's reach: " + result);
	}

	@Test
	void deepUndergroundWithStone() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = List.of(
				new SceneDescriptionBuilder.BlockInfo("minecraft:stone", 50, 0, 0, 0),
				new SceneDescriptionBuilder.BlockInfo("minecraft:deepslate", 30, 0, -1, 0)
		);
		String result = SceneDescriptionBuilder.buildSurroundings(
				SceneDescriptionBuilder.SceneKind.DEEP_UNDERGROUND, blocks);

		assertTrue(result.contains("deep beneath"), "should mention deep underground: " + result);
	}

	@Test
	void insideWoodWithMultipleFunctionalBlocks() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = List.of(
				new SceneDescriptionBuilder.BlockInfo("minecraft:oak_planks", 40, 0, 0, 0),
				new SceneDescriptionBuilder.BlockInfo("minecraft:chest", 1, 1, 0, 0),
				new SceneDescriptionBuilder.BlockInfo("minecraft:crafting_table", 1, -1, 0, 0)
		);
		String result = SceneDescriptionBuilder.buildSurroundings(
				SceneDescriptionBuilder.SceneKind.INSIDE_WOOD_STRUCTURE, blocks);

		assertTrue(result.contains("wooden building"), "should mention wooden building: " + result);
		assertTrue(result.toLowerCase().contains("chest"), "should mention chest: " + result);
		assertTrue(result.toLowerCase().contains("crafting table"), "should mention crafting table: " + result);
		assertTrue(result.contains("are within arm's reach"), "multiple items should use 'are': " + result);
	}

	@Test
	void noFunctionalBlocksOmitsArmReach() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = List.of(
				new SceneDescriptionBuilder.BlockInfo("minecraft:dirt", 50, 0, -1, 0),
				new SceneDescriptionBuilder.BlockInfo("minecraft:grass_block", 20, 0, 0, 1)
		);
		String result = SceneDescriptionBuilder.buildSurroundings(
				SceneDescriptionBuilder.SceneKind.OPEN_FIELD, blocks);

		assertFalse(result.contains("arm's reach"), "no functional blocks = no arm's reach: " + result);
	}

	@Test
	void leavesOmittedTreesInferred() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = List.of(
				new SceneDescriptionBuilder.BlockInfo("minecraft:oak_log", 10, 1, 1, 0),
				new SceneDescriptionBuilder.BlockInfo("minecraft:oak_leaves", 40, 1, 2, 0)
		);
		String result = SceneDescriptionBuilder.buildSurroundings(
				SceneDescriptionBuilder.SceneKind.OPEN_FIELD, blocks);

		assertTrue(result.contains("oak"), "should infer oak trees: " + result);
		assertFalse(result.contains("leaves"), "should not mention leaves directly: " + result);
	}

	@Test
	void emptyBlocksGivesMinimalDescription() {
		String result = SceneDescriptionBuilder.buildSurroundings(
				SceneDescriptionBuilder.SceneKind.OPEN_FIELD, List.of());
		assertTrue(result.contains("open field"), "should use scene description: " + result);
		assertTrue(result.endsWith("."), "should end with period: " + result);
	}

	@Test
	void forestSceneWithTreesMentionsSpecificTypes() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = List.of(
				new SceneDescriptionBuilder.BlockInfo("minecraft:oak_log", 33, 1, 0, 1),
				new SceneDescriptionBuilder.BlockInfo("minecraft:birch_log", 12, -1, 0, 2)
		);
		String result = SceneDescriptionBuilder.buildSurroundings(
				SceneDescriptionBuilder.SceneKind.FOREST, blocks);

		assertTrue(result.contains("among the trees"), "should have forest base: " + result);
		assertTrue(result.toLowerCase().contains("oak"), "should specify oak: " + result);
		assertTrue(result.toLowerCase().contains("birch"), "should specify birch: " + result);
	}

	@Test
	void underTreeCanopyMentionsTreeTypes() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = List.of(
				new SceneDescriptionBuilder.BlockInfo("minecraft:spruce_log", 8, 1, 0, 1)
		);
		String result = SceneDescriptionBuilder.buildSurroundings(
				SceneDescriptionBuilder.SceneKind.UNDER_TREE_CANOPY, blocks);

		assertTrue(result.contains("canopy"), "should mention canopy: " + result);
		assertTrue(result.toLowerCase().contains("spruce"), "should mention tree type: " + result);
	}

	@Test
	void villageSquareWithFunctionalBlocks() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = List.of(
				new SceneDescriptionBuilder.BlockInfo("minecraft:lectern", 1, 1, 0, 0),
				new SceneDescriptionBuilder.BlockInfo("minecraft:composter", 1, -1, 0, 0)
		);
		String result = SceneDescriptionBuilder.buildSurroundings(
				SceneDescriptionBuilder.SceneKind.VILLAGE_SQUARE, blocks);

		assertTrue(result.contains("village"), "should mention village: " + result);
		assertTrue(result.toLowerCase().contains("lectern"), "should mention lectern: " + result);
		assertTrue(result.toLowerCase().contains("composter"), "should mention composter: " + result);
	}

	// === buildNotableBlocks() tests ===

	@Test
	void diamondOreWithDirection() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = List.of(
				new SceneDescriptionBuilder.BlockInfo("minecraft:diamond_ore", 2, 8, -3, 6)
		);
		String result = SceneDescriptionBuilder.buildNotableBlocks(blocks);

		assertTrue(result.contains("diamond ore"), "should contain diamond ore: " + result);
		// With yaw=0: dx=8(+X=left), dz=6(+Z=ahead) → ahead and to your left
		assertTrue(result.contains("left") || result.contains("ahead"),
				"should contain relative direction: " + result);
		assertTrue(result.contains("below"), "should contain below: " + result);
	}

	@Test
	void bedrockFilteredWhenHighCount() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = List.of(
				new SceneDescriptionBuilder.BlockInfo("minecraft:bedrock", 1089, 0, -4, 0)
		);
		String result = SceneDescriptionBuilder.buildNotableBlocks(blocks);

		assertEquals("", result, "should filter out bedrock with high count");
	}

	@Test
	void bedrockKeptWhenLowCount() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = List.of(
				new SceneDescriptionBuilder.BlockInfo("minecraft:bedrock", 3, 0, -4, 0)
		);
		String result = SceneDescriptionBuilder.buildNotableBlocks(blocks);

		assertTrue(result.contains("bedrock"), "should keep bedrock with low count: " + result);
	}

	@Test
	void sortedByDistanceAscending() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = List.of(
				new SceneDescriptionBuilder.BlockInfo("minecraft:diamond_ore", 1, 20, 0, 0),
				new SceneDescriptionBuilder.BlockInfo("minecraft:chest", 1, 3, 0, 0)
		);
		String result = SceneDescriptionBuilder.buildNotableBlocks(blocks);

		int chestIdx = result.toLowerCase().indexOf("chest");
		int diamondIdx = result.toLowerCase().indexOf("diamond ore");
		assertTrue(chestIdx < diamondIdx, "closer block should come first: " + result);
	}

	@Test
	void limitedToSixEntries() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = new java.util.ArrayList<>();
		for (int i = 1; i <= 10; i++) {
			// Each block has a unique ID so they won't merge
			blocks.add(new SceneDescriptionBuilder.BlockInfo("minecraft:block_" + i, 1, i * 3, 0, 0));
		}
		String result = SceneDescriptionBuilder.buildNotableBlocks(blocks);

		long count = result.chars().filter(c -> c == '\n').count();
		assertEquals(6, count, "should have exactly 6 entries: " + result);
	}

	@Test
	void emptyWhenNoNotableBlocks() {
		assertEquals("", SceneDescriptionBuilder.buildNotableBlocks(List.of()));
		assertEquals("", SceneDescriptionBuilder.buildNotableBlocks(null));
	}

	@Test
	void duplicateBlocksCollapsed() {
		// Two BlockInfo entries with same blockId should merge
		List<SceneDescriptionBuilder.BlockInfo> blocks = List.of(
				new SceneDescriptionBuilder.BlockInfo("minecraft:diamond_ore", 1, 8, 0, 6),
				new SceneDescriptionBuilder.BlockInfo("minecraft:diamond_ore", 1, 10, 0, 8)
		);
		String result = SceneDescriptionBuilder.buildNotableBlocks(blocks);

		// Should be one line, not two
		long lineCount = result.chars().filter(c -> c == '\n').count();
		assertEquals(1, lineCount, "duplicates should be collapsed: " + result);
		assertTrue(result.contains("Two"), "should say 'Two' for count 2: " + result);
		assertTrue(result.contains("diamond ore"), "should contain block name: " + result);
	}

	@Test
	void manyDuplicateBlocksUsesCountQualifier() {
		List<SceneDescriptionBuilder.BlockInfo> blocks = new java.util.ArrayList<>();
		for (int i = 0; i < 5; i++) {
			blocks.add(new SceneDescriptionBuilder.BlockInfo("minecraft:coal_ore", 1, 5 + i, 0, 3));
		}
		String result = SceneDescriptionBuilder.buildNotableBlocks(blocks);

		assertTrue(result.contains("A few"), "5 entries should use 'A few': " + result);
	}

	// === buildEntities() tests ===

	@Test
	void villagerWithProfessionAndDirection() {
		List<SceneDescriptionBuilder.EntityInfo> entities = List.of(
				new SceneDescriptionBuilder.EntityInfo("minecraft:villager", "librarian", 8, 0, 6)
		);
		String result = SceneDescriptionBuilder.buildEntities(entities);

		assertTrue(result.contains("librarian villager"), "should mention profession: " + result);
		// With yaw=0: dx=8(+X=left), dz=6(+Z=ahead) → ahead and to your left
		assertTrue(result.contains("left") || result.contains("ahead"),
				"should mention relative direction: " + result);
	}

	@Test
	void hostileWithDirection() {
		List<SceneDescriptionBuilder.EntityInfo> entities = List.of(
				new SceneDescriptionBuilder.EntityInfo("minecraft:zombie", null, -5, 0, 3)
		);
		String result = SceneDescriptionBuilder.buildEntities(entities);

		assertTrue(result.contains("zombie"), "should mention zombie: " + result);
		// dx=-5(-X=right), dz=3(+Z=ahead) → ahead and to your right
		assertTrue(result.contains("right") || result.contains("ahead"),
				"should mention relative direction: " + result);
	}

	@Test
	void passiveMobsGroupedWithCountQualifier() {
		List<SceneDescriptionBuilder.EntityInfo> entities = List.of(
				new SceneDescriptionBuilder.EntityInfo("minecraft:sheep", null, 5, 0, 3),
				new SceneDescriptionBuilder.EntityInfo("minecraft:sheep", null, 6, 0, 4),
				new SceneDescriptionBuilder.EntityInfo("minecraft:sheep", null, 7, 0, 2)
		);
		String result = SceneDescriptionBuilder.buildEntities(entities);

		assertTrue(result.contains("Three sheep"), "should say 'Three sheep' for count 3: " + result);
		// Should be a single line, not 3
		long lineCount = result.chars().filter(c -> c == '\n').count();
		assertEquals(1, lineCount, "should be grouped into 1 line: " + result);
	}

	@Test
	void singlePassiveMobNotGrouped() {
		List<SceneDescriptionBuilder.EntityInfo> entities = List.of(
				new SceneDescriptionBuilder.EntityInfo("minecraft:sheep", null, 4, 0, -3)
		);
		String result = SceneDescriptionBuilder.buildEntities(entities);

		assertTrue(result.contains("A sheep"), "should use singular: " + result);
		assertFalse(result.contains("Three") || result.contains("few"),
				"should not use count qualifier: " + result);
	}

	@Test
	void emptyWhenNoEntities() {
		assertEquals("", SceneDescriptionBuilder.buildEntities(List.of()));
		assertEquals("", SceneDescriptionBuilder.buildEntities(null));
	}

	@Test
	void mixedEntityTypes() {
		List<SceneDescriptionBuilder.EntityInfo> entities = List.of(
				new SceneDescriptionBuilder.EntityInfo("minecraft:villager", "farmer", 3, 0, -5),
				new SceneDescriptionBuilder.EntityInfo("minecraft:zombie", null, -10, 0, 0),
				new SceneDescriptionBuilder.EntityInfo("minecraft:cow", null, 8, 0, 4),
				new SceneDescriptionBuilder.EntityInfo("minecraft:cow", null, 9, 0, 3),
				new SceneDescriptionBuilder.EntityInfo("minecraft:cow", null, 7, 0, 5)
		);
		String result = SceneDescriptionBuilder.buildEntities(entities);

		assertTrue(result.contains("farmer villager"), "should list farmer: " + result);
		assertTrue(result.contains("zombie"), "should list zombie: " + result);
		assertTrue(result.contains("Three cow"), "should say 'Three cows' for 3 cows: " + result);
	}

	@Test
	void hostilesGroupedByType() {
		List<SceneDescriptionBuilder.EntityInfo> entities = List.of(
				new SceneDescriptionBuilder.EntityInfo("minecraft:zombie", null, 5, 0, 3),
				new SceneDescriptionBuilder.EntityInfo("minecraft:zombie", null, 6, 0, 4)
		);
		String result = SceneDescriptionBuilder.buildEntities(entities);

		assertTrue(result.contains("Two zombie"), "should group zombies: " + result);
		long lineCount = result.chars().filter(c -> c == '\n').count();
		assertEquals(1, lineCount, "should be grouped into 1 line: " + result);
	}

	// === prettyBlockName() test ===

	@Test
	void prettyBlockNameStripsPrefix() {
		assertEquals("deepslate diamond ore", SceneDescriptionBuilder.prettyBlockName("minecraft:deepslate_diamond_ore"));
		assertEquals("dirt", SceneDescriptionBuilder.prettyBlockName("minecraft:dirt"));
		assertEquals("unknown", SceneDescriptionBuilder.prettyBlockName(null));
	}
}
