package com.enderdragonanthro.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
 *
 * Losing focus used to end it anyway, by the back door: vanilla opens the pause
 * menu half a second after the window is clicked away from, and clicking away is
 * the normal thing to do here. {@code CameraViewPauseMixin} stops that at the
 * source; the {@code setScreen(null)} in {@link #tick} stays as a belt, for
 * anything else that puts a screen up.
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

    /** The ability chips' palette, so the hint reads as part of the same HUD. */
    private static final int BG = 0xD0141414;
    private static final int BORDER = 0xFF474747;
    private static final int TEXT = 0xFFE079FA;
    private static final String HINT = "Esc to end Camera View";

    /**
     * The one thing drawn in camera view, and only while the window has focus.
     *
     * A label is the whole point — the state is deliberately inescapable except
     * by one key, and a state like that has to say which key. Not a widget you
     * can click: there is no screen open to route a click to, and a second way
     * out would undo the "nothing can knock this window out of shot" that the
     * mode was asked for.
     *
     * Hidden while the window is unfocused, which is the entire time you are
     * actually recording — you are playing on the other client, this one is the
     * camera, and a caption burned into the shot would defeat the mode. The rule
     * is not a guess about when you want to read it either: Escape is a key
     * event, an unfocused window receives none, so the hint is on screen exactly
     * when the key it names would work.
     */
    public static void render(GuiGraphics graphics, Minecraft client) {
        if (!on || !client.isWindowActive()) {
            return;
        }
        int width = client.font.width(HINT);
        int x = (graphics.guiWidth() - width) / 2;
        // Where the hotbar would be. Nothing else is drawn in this mode, and it
        // is the one strip of screen a shot is already composed around.
        int y = graphics.guiHeight() - 26;
        graphics.fill(x - 5, y - 5, x + width + 5, y + 13, BG);
        graphics.fill(x - 5, y - 5, x + width + 5, y - 4, BORDER);
        graphics.fill(x - 5, y + 12, x + width + 5, y + 13, BORDER);
        graphics.fill(x - 5, y - 5, x - 4, y + 13, BORDER);
        graphics.fill(x + width + 4, y - 5, x + width + 5, y + 13, BORDER);
        graphics.drawString(client.font, HINT, x, y, TEXT, true);
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
