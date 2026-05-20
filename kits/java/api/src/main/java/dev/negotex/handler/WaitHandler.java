package dev.negotex.handler;

import java.util.Map;

/**
 * Handler for Wait nodes — suspends and resumes a process instance.
 *
 * <p>{@link #onSuspend} is called when the envelope arrives and is parked in Valkey.
 * Implementations typically notify an external actor (send email, create task, etc.).
 *
 * <p>{@link #onResume} is called when the external completion arrives.
 * The returned map is merged into the envelope payload before forwarding downstream.
 */
public interface WaitHandler {
    void onSuspend(String taskId, Map<String, Object> context);
    Map<String, Object> onResume(String taskId, Map<String, Object> externalData);
}
