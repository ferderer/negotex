package dev.negotex.test;

import dev.negotex.payload.PayloadKey;

public class BadHandler {
    public String handle(@PayloadKey("input") String first, String second) {
        return first + second;
    }
}
