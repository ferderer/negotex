package dev.negotex.test;

import dev.negotex.payload.PayloadInput;
import dev.negotex.payload.PayloadKey;

@PayloadInput
public record BadInput(
        @PayloadKey("application") String first,
        @PayloadKey("application") String second
) {}
