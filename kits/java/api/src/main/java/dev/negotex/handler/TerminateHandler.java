package dev.negotex.handler;

import java.util.Map;

/**
 * Optional handler for Terminate nodes — cleanup hook on process completion.
 *
 * <p>Called before the END audit event is written and Valkey state is deleted.
 */
@FunctionalInterface
public interface TerminateHandler {
    void onTerminate(Map<String, Object> finalPayload);
}
