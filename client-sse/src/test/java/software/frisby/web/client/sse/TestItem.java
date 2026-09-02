package software.frisby.web.client.sse;

/**
 * Minimal JSON-serializable payload used by {@link ClientSseTypedDispatchTest} to exercise
 * typed {@code Class<T>} deserialization against {@code SseTestResource}'s
 * {@code payload=object} JSON output ({@code {"value":"event-N"}}).
 */
record TestItem(String value) {
}

