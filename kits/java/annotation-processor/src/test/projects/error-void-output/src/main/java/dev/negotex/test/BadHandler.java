package dev.negotex.test;

import dev.negotex.payload.PayloadOutput;

public class BadHandler {

    @PayloadOutput("result")
    public void handle(String input) {}
}
