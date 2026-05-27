package dev.negotex.test;

import dev.negotex.handler.TaskHandler;

/**
 * Form B handler: uses @PayloadInput record for input and @PayloadOutput record for output.
 */
public class CreditCheckHandler implements TaskHandler<CreditCheckInput, CreditCheckResult> {

    public CreditCheckResult handle(CreditCheckInput input) {
        int score = input.application().length() * 10;
        return new CreditCheckResult(score, score > 700 ? "LOW" : "HIGH", score > 600);
    }
}
