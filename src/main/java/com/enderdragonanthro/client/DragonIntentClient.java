package com.enderdragonanthro.client;

import com.enderdragonanthro.ability.DragonIntent;

/**
 * The client's copy of which stop the dial is on.
 *
 * Read by the HUD once a frame and written only by the payload, so the chip can
 * never claim a stop the server is not actually on. The enum itself is common
 * code, so both sides name the stops the same way and there is no table of
 * strings to keep in step.
 */
public final class DragonIntentClient {
    private static DragonIntent held = DragonIntent.ALERT;

    private DragonIntentClient() {
    }

    public static DragonIntent get() {
        return held;
    }

    public static void set(DragonIntent intent) {
        held = intent;
    }
}
