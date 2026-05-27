package dev.negotex.test;

import dev.negotex.payload.PayloadOutput;
import dev.negotex.payload.PayloadKey;

@PayloadOutput
public record RiskResult(
        @PayloadKey("riskBand")  String riskBand,
        @PayloadKey("approved")  boolean approved
) {}
