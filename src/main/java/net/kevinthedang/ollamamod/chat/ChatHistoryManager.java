package net.kevinthedang.ollamamod.chat;

import java.util.*;

public class ChatHistoryManager {

    private static final int MAX_MESSAGES = 50; // TODO: Determine appropriate limit based on system testing

    private final Map<UUID, Deque<ChatMessage>> historyByConversation = new java.util.concurrent.ConcurrentHashMap<>();

    public List<ChatMessage> getHistory(UUID conversationId) {
        Deque<ChatMessage> deque =
                historyByConversation.getOrDefault(conversationId, new ArrayDeque<>());
        return List.copyOf(deque);
    }

    public void append(UUID conversationId, ChatMessage message) {
        Deque<ChatMessage> deque =
                historyByConversation.computeIfAbsent(conversationId, id -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        deque.addLast(message);
        while (deque.size() > MAX_MESSAGES) {
            deque.removeFirst();
        }
    }

    public void clear(UUID conversationId) {
        historyByConversation.remove(conversationId);
    }
}
