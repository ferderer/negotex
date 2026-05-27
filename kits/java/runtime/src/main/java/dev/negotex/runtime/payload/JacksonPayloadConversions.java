package dev.negotex.runtime.payload;

import dev.negotex.error.HandlerError;
import dev.negotex.error.NegotexHandlerException;
import dev.negotex.payload.PayloadConversions;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Jackson-backed implementation of {@link PayloadConversions}.
 *
 * <p>Provided by the runtime and injected into all generated
 * {@link PayloadExtractor} and {@link PayloadInserter} instances
 * at startup via {@code ExtractorResolver}.
 *
 * <p>Handler authors never interact with this class directly.
 */
public final class JacksonPayloadConversions implements PayloadConversions {

    private final ObjectMapper mapper;

    public JacksonPayloadConversions() {
        this.mapper = JsonMapper.builder().build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convert(Object value, Class<T> targetType, String key) {
        if (value == null) {
            throw new NegotexHandlerException(HandlerError.PAYLOAD_EXTRACTION_FAILED)
                    .with("key", key)
                    .with("reason", "Required payload attribute is absent");
        }
        if (targetType.isInstance(value)) {
            return (T) value;
        }
        try {
            return mapper.convertValue(value, targetType);
        } catch (IllegalArgumentException e) {
            throw new NegotexHandlerException(HandlerError.PAYLOAD_EXTRACTION_FAILED, e)
                    .with("key", key)
                    .with("sourceType", value.getClass().getSimpleName())
                    .with("targetType", targetType.getSimpleName())
                    .with("cause", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> insert(String key, Object value,
                                      Map<String, Object> payload) {
        if (key.startsWith("_")) {
            throw new NegotexHandlerException(HandlerError.PAYLOAD_EXTRACTION_FAILED)
                    .with("key", key)
                    .with("reason", "Payload key uses reserved '_' prefix");
        }
        if (payload.containsKey(key)) {
            throw new NegotexHandlerException(HandlerError.PAYLOAD_EXTRACTION_FAILED)
                    .with("key", key)
                    .with("reason", "Payload attribute already exists — "
                            + "handler output must not overwrite existing attributes");
        }
        var enriched = new HashMap<>(payload);
        enriched.put(key, value);
        return Map.copyOf(enriched);
    }
}
