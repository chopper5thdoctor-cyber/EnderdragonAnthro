package com.enderdragonanthro.client;

import com.enderdragonanthro.network.ShadeStatePayload;
import net.minecraft.client.Minecraft;

/**
 * Client-side landing point for the court roster. The command key asks the
 * server for it; every change the server makes afterwards is pushed here, so
 * an open screen keeps up on its own.
 */
public final class ShadeCourtClient {
    private ShadeCourtClient() {
    }

    public static void accept(ShadeStatePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof ShadeCommandScreen screen) {
            screen.refresh(payload);
        } else if (payload.open()) {
            client.setScreen(new ShadeCommandScreen(payload));
        }
    }
}
