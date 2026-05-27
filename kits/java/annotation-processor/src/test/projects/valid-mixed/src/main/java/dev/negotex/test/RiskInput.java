package dev.negotex.test;

import dev.negotex.payload.PayloadInput;
import dev.negotex.payload.PayloadKey;

@PayloadInput
public record RiskInput(
        @PayloadKey("application") String application,
        @PayloadKey("creditScore") int creditScore
) {}
