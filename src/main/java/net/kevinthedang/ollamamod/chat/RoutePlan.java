package net.kevinthedang.ollamamod.chat;

/**
 * In later milestones, this can expand to memory/retrieval depth, tool timeouts, etc.
 */
public record RoutePlan(
        boolean useWorld,
        boolean useMemory,
        boolean useRetriever,
        int depth
) {
    public static RoutePlan phase1Default() {
        // M3 and M4: world facts only.
        return new RoutePlan(true, false, false, 0);
    }
}
