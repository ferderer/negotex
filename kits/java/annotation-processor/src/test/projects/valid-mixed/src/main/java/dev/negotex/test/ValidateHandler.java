package dev.negotex.test;

import dev.negotex.handler.TaskHandler;
import dev.negotex.payload.PayloadKey;
import dev.negotex.payload.PayloadOutput;

/** Form A handler — runs alongside the Form B handler in the same compilation unit. */
public class ValidateHandler implements TaskHandler<String, Boolean> {

    @PayloadOutput("valid")
    public Boolean handle(@PayloadKey("application") String application) {
        return application != null && !application.isBlank();
    }
}
