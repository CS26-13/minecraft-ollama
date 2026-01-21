package net.kevinthedang.ollamamod.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * For M3: implementation that guarantees >=3 injected facts per turn.
 *
 * This does NOT query Minecraft APIs yet (coords/biome/etc). It only uses the
 * information already available in VillagerBrain.Context + history size.
 */
public class BasicWorldContextTool implements WorldContextTool {

    @Override
    public WorldFactBundle collect(VillagerBrain.Context ctx, List<ChatMessage> history, String playerMessage) {
        List<WorldFact> facts = new ArrayList<>();

        // Fact 1: world
        facts.add(new WorldFact(
                "World: " + safe(ctx.worldName()),
                "context.worldName",
                0.95,
                30_000
        ));

        // Fact 2: villager identity
        facts.add(new WorldFact(
                "Villager: " + safe(ctx.villagerName()),
                "context.villagerName",
                0.95,
                30_000
        ));

        // Fact 3: villager profession
        facts.add(new WorldFact(
                "Profession: " + safe(ctx.villagerProfession()),
                "context.villagerProfession",
                0.95,
                30_000
        ));

        // Additional cheap, useful facts (helps grounding + debugging)
        facts.add(new WorldFact(
                "Conversation ID: " + ctx.conversationId(),
                "context.conversationId",
                1.0,
                30_000
        ));

        facts.add(new WorldFact(
                "Turn count (messages so far): " + (history == null ? 0 : history.size()),
                "history.size",
                0.8,
                5_000
        ));

        facts.add(new WorldFact(
                "Timestamp (UTC): " + Instant.now(),
                "system.clock",
                0.7,
                5_000
        ));

        return new WorldFactBundle(facts);
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "Unknown" : s;
    }
}
