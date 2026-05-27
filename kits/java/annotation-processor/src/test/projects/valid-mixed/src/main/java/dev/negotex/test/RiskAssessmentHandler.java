package dev.negotex.test;

import dev.negotex.handler.TaskHandler;

/** Form B handler. */
public class RiskAssessmentHandler implements TaskHandler<RiskInput, RiskResult> {
    public RiskResult handle(RiskInput input) {
        return new RiskResult(input.creditScore() > 700 ? "LOW" : "HIGH", input.creditScore() > 600);
    }
}
