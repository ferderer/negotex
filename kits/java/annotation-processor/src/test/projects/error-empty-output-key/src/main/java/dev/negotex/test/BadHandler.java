package dev.negotex.test;

import dev.negotex.payload.PayloadOutput;

public class BadHandler {

    @PayloadOutput("")
    public String handle(String input) {
        return input;
    }
}
