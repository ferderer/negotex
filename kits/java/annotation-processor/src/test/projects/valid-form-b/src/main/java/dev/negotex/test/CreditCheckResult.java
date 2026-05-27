package dev.negotex.test;

import dev.negotex.payload.PayloadOutput;
import dev.negotex.payload.PayloadKey;

/**
 * Form B output: @PayloadOutput record with multiple @PayloadKey components.
 * The annotation processor generates CreditCheckResultInserter.
 */
@PayloadOutput
public record CreditCheckResult(
        @PayloadKey("creditScore") int score,
        @PayloadKey("riskBand")    String riskBand,
        @PayloadKey("approved")    boolean approved
) {}
