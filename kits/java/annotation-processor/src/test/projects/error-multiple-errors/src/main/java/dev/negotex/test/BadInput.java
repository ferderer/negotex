package dev.negotex.test;

import dev.negotex.payload.PayloadInput;
import dev.negotex.payload.PayloadKey;

// noAnnotation has no @PayloadKey       → "must be annotated with @PayloadKey"
// first and second share key "amount"   → "Duplicate @PayloadKey value 'amount'"
// Both errors must appear in the same compilation pass.
@PayloadInput
public record BadInput(
        String noAnnotation,
        @PayloadKey("amount") String first,
        @PayloadKey("amount") String second
) {}
