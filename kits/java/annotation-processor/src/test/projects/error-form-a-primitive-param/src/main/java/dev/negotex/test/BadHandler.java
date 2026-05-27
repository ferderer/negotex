package dev.negotex.test;

import dev.negotex.payload.PayloadKey;

public class BadHandler {
    public Object handle(@PayloadKey("count") int count) {
        return count;
    }
}
