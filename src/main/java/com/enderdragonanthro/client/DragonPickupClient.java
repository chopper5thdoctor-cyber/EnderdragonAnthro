package com.enderdragonanthro.client;

/**
 * The client's copy of "do I stoop", so the button can draw itself.
 *
 * The server owns the answer — it is the side that decides whether an item is
 * collected — and this is only what it last said. Nothing here changes
 * anything; clicking sends a request and the button redraws when the reply
 * arrives, which is a round trip nobody will notice and one less way for the
 * two sides to disagree.
 */
public final class DragonPickupClient {
    private static boolean on = true;

    private DragonPickupClient() {
    }

    public static boolean picksUp() {
        return on;
    }

    public static void set(boolean value) {
        on = value;
    }
}
