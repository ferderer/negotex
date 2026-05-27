package dev.negotex.test;

import dev.negotex.payload.PayloadInput;
import dev.negotex.payload.PayloadKey;

/**
 * Form B input: @PayloadInput record with multiple @PayloadKey components.
 * The annotation processor generates CreditCheckInputExtractor.
 */
@PayloadInput
public record CreditCheckInput(
        @PayloadKey("application")     String application,
        @PayloadKey("customerProfile") String customerProfile
) {}
