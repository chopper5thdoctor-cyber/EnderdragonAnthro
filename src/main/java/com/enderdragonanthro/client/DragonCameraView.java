package com.enderdragonanthro.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * A clean window to point a camera at.
 *
 * The second client exists to look at the first one, and neither state it can
 * normally be in is any use for that. In game, the ability list, the boss bar
 * and the hotbar sit over the shot. In the pause menu the mouse is free — which
 * is what you need to reach the other window — but there is a Game Menu across
 * the middle of the screen.
 *
 * This is the third state: no HUD, no menu, and the mouse released, so the
 * window can be left alone while you play on the other one.
 *
 * ## The mouse is released every tick, not once
 *
 * Releasing it once is not enough and this is the whole reason the tick exists.
 * With no screen open, Minecraft grabs the cursor back the moment the window is
 * clicked — which is exactly what happens when you click back onto it after
 * using the other one, and the view would lurch as the grab turns the player.
 * Re-releasing each tick makes the state actually stick.
 *
 * ## Escape is the way out, and deliberately the only one
 *
 * Asked for as a "stuck state", which is right for recording: nothing you do to
 * the other window can knock this one out of shot. Escape restores the HUD to
 * whatever it was before — not to on, because F1 may well have been the reason
 * it was off.
 */
public final class DragonCameraView {
    private static boolean on;
    private static boolean hidGuiBefore;
    /** So holding Escape does not toggle once a tick. */
    private static boolean escapeWasDown;

    private DragonCameraView() {
    }

    public static boolean active() {
        return on;
    }

    public static void enter(Minecraft client) {
        if (on) {
            return;
        }
        on = true;
        hidGuiBefore = client.options.hideGui;
        client.options.hideGui = true;
        // Closing the screen is what grabs the mouse, so the release has to come
        // after it rather than before.
        client.setScreen(null);
        client.mouseHandler.releaseMouse();
        escapeWasDown = true;        // whatever opened the menu may still be held
    }

    public static void leave(Minecraft client) {
        if (!on) {
            return;
        }
        on = false;
        client.options.hideGui = hidGuiBefore;
        client.mouseHandler.grabMouse();
    }

    /** Hold the state open, and watch for the one key that ends it. */
    public static void tick(Minecraft client) {
        if (!on) {
            escapeWasDown = false;
            return;
        }
        // Left the world while in camera view: there is nothing to look at and
        // a title screen with no cursor is a genuinely stuck game.
        if (client.level == null || client.player == null) {
            leave(client);
            return;
        }
        boolean escape = InputConstants.isKeyDown(
                client.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE);
        if (escape && !escapeWasDown) {
            leave(client);
            return;
        }
        escapeWasDown = escape;

        if (client.screen != null) {
            client.setScreen(null);
        }
        if (client.mouseHandler.isMouseGrabbed()) {
            client.mouseHandler.releaseMouse();
        }
    }
}
