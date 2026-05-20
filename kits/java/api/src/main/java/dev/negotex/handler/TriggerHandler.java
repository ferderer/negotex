package dev.negotex.handler;

import java.util.Map;

/**
 * Handler for Trigger nodes — creates the initial payload from an external event.
 *
 * <p>The node processor computes {@code _hash} and wraps the returned map in an
 * Envelope. Implementations must not set {@code _hash} themselves.
 */
@FunctionalInterface
public interface TriggerHandler {
    Map<String, Object> createPayload(Object externalEvent);
}
