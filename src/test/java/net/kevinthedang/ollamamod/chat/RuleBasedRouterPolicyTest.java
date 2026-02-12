package net.kevinthedang.ollamamod.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RuleBasedRouterPolicyTest {

	private final RuleBasedRouterPolicy router = new RuleBasedRouterPolicy();
	private final VillagerBrain.Context ctx = new VillagerBrain.Context(
			UUID.randomUUID(), "TestVillager", "farmer", "TestWorld");

	// Memory is always enabled regardless of message content
	@Test
	public void againTriggersMemory() {
		RoutePlan plan = router.plan(ctx, List.of(), "can you show me the order again?");
		assertTrue(plan.useMemory(), "memory should always be enabled");
	}

	// Memory is always enabled regardless of message content
	@Test
	public void earlierTriggersMemory() {
		RoutePlan plan = router.plan(ctx, List.of(), "what did you say earlier?");
		assertTrue(plan.useMemory(), "memory should always be enabled");
	}

	// Memory is always enabled regardless of message content
	@Test
	public void youToldMeTriggersMemory() {
		RoutePlan plan = router.plan(ctx, List.of(), "you told me something about diamonds");
		assertTrue(plan.useMemory(), "memory should always be enabled");
	}

	// Simple greetings should always have memory but no retriever
	@Test
	public void simpleGreetingAlwaysHasMemory() {
		RoutePlan plan = router.plan(ctx, List.of(), "hello");
		assertFalse(plan.useRetriever(), "greeting should not trigger retriever");
		assertTrue(plan.useMemory(), "greeting should still have memory");
	}

	// Villager ends with "?", player says "yes" → useRetriever=true
	@Test
	public void villagerQuestionFollowUp() {
		List<ChatMessage> history = List.of(
				new ChatMessage(ChatRole.PLAYER, "I want to go mining"),
				new ChatMessage(ChatRole.VILLAGER, "Do you need help finding diamonds?")
		);
		RoutePlan plan = router.plan(ctx, history, "yes please");

		assertTrue(plan.useRetriever(), "should trigger retriever after villager question");
		assertTrue(plan.useMemory(), "should always have memory");
	}

	// Villager ends with "?", but player says "thanks" (fast-path keyword) → no retriever
	@Test
	public void villagerQuestionFastPathOverride() {
		List<ChatMessage> history = List.of(
				new ChatMessage(ChatRole.PLAYER, "tell me about iron"),
				new ChatMessage(ChatRole.VILLAGER, "Would you like to know more?")
		);
		RoutePlan plan = router.plan(ctx, history, "thanks");

		assertFalse(plan.useRetriever(), "fast-path should override villager question follow-up");
		assertTrue(plan.useMemory(), "should always have memory");
	}

	// Vague follow-up after retriever history should enable both useRetriever and useMemory
	@Test
	public void vagueFollowUpEnablesBothRetrieverAndMemory() {
		List<ChatMessage> history = List.of(
				new ChatMessage(ChatRole.PLAYER, "how do I craft a stone pickaxe?"),
				new ChatMessage(ChatRole.VILLAGER, "You need 3 cobblestone and 2 sticks.")
		);
		RoutePlan plan = router.plan(ctx, history, "ok show me");

		assertTrue(plan.useRetriever(), "follow-up after retriever history should enable retriever");
		assertTrue(plan.useMemory(), "follow-up after retriever history should enable memory");
	}

	// Augmented query should contain the prior substantive message
	@Test
	public void augmentedQueryContainsPriorSubstantiveMessage() {
		List<ChatMessage> history = List.of(
				new ChatMessage(ChatRole.PLAYER, "how about a stone pickaxe?"),
				new ChatMessage(ChatRole.VILLAGER, "Sure! You need cobblestone and sticks.")
		);
		RoutePlan plan = router.plan(ctx, history, "show me again");

		assertNotNull(plan.augmentedQuery(), "augmented query should be set for vague follow-up");
		assertTrue(plan.augmentedQuery().contains("stone pickaxe"),
				"augmented query should contain prior topic");
		assertTrue(plan.augmentedQuery().contains("show me again"),
				"augmented query should contain current message");
	}

	// Explicit retriever queries should not get augmentation
	@Test
	public void explicitQueryGetsNoAugmentation() {
		List<ChatMessage> history = List.of(
				new ChatMessage(ChatRole.PLAYER, "how do I craft a diamond sword?"),
				new ChatMessage(ChatRole.VILLAGER, "You need 2 diamonds and a stick.")
		);
		RoutePlan plan = router.plan(ctx, history, "how do I craft an iron pickaxe?");

		assertTrue(plan.useRetriever(), "explicit retriever question should enable retriever");
		assertNull(plan.augmentedQuery(), "explicit query should not be augmented");
	}

	// Fast-path keywords should skip retrieval even with retriever history
	@Test
	public void fastPathOverridesRetrieverHistory() {
		List<ChatMessage> history = List.of(
				new ChatMessage(ChatRole.PLAYER, "how do I craft a pickaxe?"),
				new ChatMessage(ChatRole.VILLAGER, "You need sticks and material.")
		);
		RoutePlan plan = router.plan(ctx, history, "hello again");

		assertFalse(plan.useRetriever(), "fast-path should override retriever even with history");
	}

	// buildAugmentedQuery should return null when no history
	@Test
	public void buildAugmentedQueryReturnsNullWithoutHistory() {
		String result = router.buildAugmentedQuery("show me", List.of());
		assertNull(result, "should return null with empty history");
	}

	// buildAugmentedQuery should find the most recent retriever message
	@Test
	public void buildAugmentedQueryFindsRecentRetrieverMessage() {
		List<ChatMessage> history = List.of(
				new ChatMessage(ChatRole.PLAYER, "how do I make a cake?"),
				new ChatMessage(ChatRole.VILLAGER, "You need wheat, sugar, eggs, and milk."),
				new ChatMessage(ChatRole.PLAYER, "how do I craft cookies?"),
				new ChatMessage(ChatRole.VILLAGER, "Cookies need wheat and cocoa beans.")
		);
		String result = router.buildAugmentedQuery("tell me more", history);

		assertNotNull(result);
		assertTrue(result.contains("cookies"), "should find the most recent retriever message");
		assertTrue(result.contains("tell me more"), "should include the current message");
	}
}
