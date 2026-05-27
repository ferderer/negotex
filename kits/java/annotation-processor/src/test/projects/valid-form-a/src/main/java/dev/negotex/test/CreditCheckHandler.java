package dev.negotex.test;

import dev.negotex.handler.TaskHandler;
import dev.negotex.payload.PayloadKey;
import dev.negotex.payload.PayloadOutput;

/**
 * Form A example: @PayloadKey on the handle() parameter, @PayloadOutput on the method.
 * The annotation processor generates:
 *   - StringExtractor  (extracts payload key "application" as String)
 *   - IntegerInserter  (inserts return value under key "creditScore")
 */
public class CreditCheckHandler implements TaskHandler<String, Integer> {

    @PayloadOutput("creditScore")
    public Integer handle(@PayloadKey("application") String application) {
        return application.length() > 10 ? 750 : 500;
    }
}
