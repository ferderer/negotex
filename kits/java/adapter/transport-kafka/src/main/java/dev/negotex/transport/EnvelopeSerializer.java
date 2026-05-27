package dev.negotex.transport;

import dev.negotex.envelope.Envelope;
import dev.negotex.error.NegotexException;
import dev.negotex.error.NegotexRuntimeError;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serialises and deserialises {@link Envelope} instances to/from JSON bytes
 * for Kafka transport.
 *
 * <p>Uses a dedicated {@link ObjectMapper} with deterministic serialisation
 * settings — map keys are sorted, timestamps are written as ISO-8601 strings.
 * Deterministic output is required for hash chain integrity (ADR-028):
 * the same envelope content must always produce the same bytes.
 *
 * <p>The envelope format is part of the Negotex protocol (protocol/envelope-schema.json).
 * Changes to serialisation settings are breaking changes and require a protocol
 * version bump.
 */
@Component
public class EnvelopeSerializer {

    private final ObjectMapper mapper;

    public EnvelopeSerializer() {
        this.mapper = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            // Sorted map keys — deterministic payload serialisation
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    }

    /**
     * Serialise an envelope to JSON bytes for Kafka transport.
     *
     * @param envelope the envelope to serialise
     * @return UTF-8 JSON bytes
     * @throws NegotexException with {@link NegotexRuntimeError#ENVELOPE_SERIALISATION_FAILED}
     *                          if serialisation fails (should never happen in practice)
     */
    public byte[] serialise(Envelope envelope) {
        try {
            return mapper.writeValueAsBytes(envelope);
        }
        catch (JacksonException e) {
            throw new NegotexException(NegotexRuntimeError.ENVELOPE_SERIALISATION_FAILED, e)
                .with("envelopeId", envelope.envelopeId())
                .with("cause", e.getMessage());
        }
    }

    /**
     * Deserialise an envelope from Kafka message bytes.
     *
     * @param bytes UTF-8 JSON bytes from Kafka
     * @return the deserialised envelope
     * @throws NegotexException with {@link NegotexRuntimeError#ENVELOPE_DESERIALISATION_FAILED}
     *                          if the bytes cannot be parsed as an Envelope
     */
    public Envelope deserialise(byte[] bytes) {
        try {
            return mapper.readValue(bytes, Envelope.class);
        }
        catch (JacksonException e) {
            throw new NegotexException(NegotexRuntimeError.ENVELOPE_DESERIALISATION_FAILED, e)
                .with("cause", e.getMessage());
        }
    }
}
