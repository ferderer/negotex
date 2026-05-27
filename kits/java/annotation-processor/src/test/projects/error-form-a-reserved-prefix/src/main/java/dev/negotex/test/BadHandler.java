package dev.negotex.test;

import dev.negotex.payload.PayloadKey;

public class BadHandler {
    public String handle(@PayloadKey("_internal") String data) {
        return data;
    }
}
