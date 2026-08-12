package com.enderdragonanthro.client;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.ability.AbilityAction;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ability list, shown only while transformed: a column down the left edge,
 * each ability's key chip with its name beside it. Chips light up with the
 * canon eye-glow purple when pressed and drain a shade as the ability
 * recharges. Palette per DESIGN.md section 2.
 */
public final class DragonHud {
    private static final int CHIP_H = 18;
    private static final int GLOW_TICKS = 8;

    private static final int BG = 0xD0141414;
    private static final int BG_GLOW = 0xF03A0F45;
    private static final int BORDER = 0xFF474747;
    private static final int BORDER_GLOW = 0xFFE079FA;
    private static final int TEXT = 0xFF8A8A8A;
    private static final int TEXT_GLOW = 0xFFF3C5FF;
    private static final int LABEL = 0xFF9A93A5;
    private static final int LABEL_ON = 0xFFE079FA;
    private static final int COOLDOWN_SHADE = 0xB0000000;

    private static final Map<KeyMapping, AbilityAction> CHIPS = new LinkedHashMap<>();
    private static final Map<AbilityAction, Long> PRESSED_AT = new EnumMap<>(AbilityAction.class);
    private static final Map<AbilityAction, Long> READY_AT = new EnumMap<>(AbilityAction.class);
    private static final Map<AbilityAction, Boolean> TOGGLED = new EnumMap<>(AbilityAction.class);
    private static long clientTicks;

    private DragonHud() {
    }

    public static void addChip(KeyMapping key, AbilityAction action) {
        CHIPS.put(key, action);
    }

    public static void tick() {
        clientTicks++;
    }

    public static void notePress(AbilityAction action) {
        PRESSED_AT.put(action, clientTicks);
        if (action == AbilityAction.CRATER) {
            TOGGLED.merge(action, true, (a, b) -> !a);
        }
        if (action.cooldownTicks > 0) {
            READY_AT.put(action, clientTicks + action.cooldownTicks);
        }
    }

    /** The scale modifier is synced to the client, so it doubles as the form flag. */
    public static boolean isDragonForm(net.minecraft.world.entity.player.Player player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        return scale != null && scale.getModifier(EnderdragonAnthro.id("dragon_scale")) != null;
    }

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !isDragonForm(mc.player)) {
            return;
        }
        renderOwnBossBar(graphics, mc.player, mc);

        // A vertical column down the left edge: the hotbar owns the bottom of
        // the screen, and thirteen abilities never fit across it anyway.
        int rowH = CHIP_H + 2;
        int total = CHIPS.size() * rowH;
        int x = 6;
        int y = Math.max(4, (graphics.guiHeight() - total) / 2);

        for (Map.Entry<KeyMapping, AbilityAction> entry : CHIPS.entrySet()) {
            AbilityAction action = entry.getValue();
            long sincePress = clientTicks - PRESSED_AT.getOrDefault(action, Long.MIN_VALUE / 2);
            boolean lit = (clientTicks - sincePress >= 0 && sincePress < GLOW_TICKS)
                    || TOGGLED.getOrDefault(action, false);

            int keyW = 20;
            graphics.fill(x, y, x + keyW, y + CHIP_H, lit ? BG_GLOW : BG);
            drawBorder(graphics, x, y, keyW, lit ? BORDER_GLOW : BORDER);

            long readyAt = READY_AT.getOrDefault(action, 0L);
            if (readyAt > clientTicks && action.cooldownTicks > 0) {
                float left = (float) (readyAt - clientTicks) / action.cooldownTicks;
                graphics.fill(x + 1, y + 1, x + keyW - 1,
                        y + 1 + (int) (left * (CHIP_H - 2)), COOLDOWN_SHADE);
            }
            graphics.drawCenteredString(mc.font, keyLabel(entry.getKey()),
                    x + keyW / 2, y + (CHIP_H - 8) / 2, lit ? TEXT_GLOW : TEXT);
            graphics.drawString(mc.font, action.label, x + keyW + 4,
                    y + (CHIP_H - 8) / 2, lit ? LABEL_ON : LABEL, true);
            y += rowH;
        }
    }

    private static String keyLabel(KeyMapping key) {
        String s = key.getTranslatedKeyMessage().getString().toUpperCase();
        return s.length() > 5 ? s.substring(0, 5) : s;
    }

    /** Your own dragon boss bar, palette-styled, top centre like the real fight. */
    private static void renderOwnBossBar(GuiGraphics graphics, LocalPlayer player, Minecraft mc) {
        int barWidth = 182;
        int x = (graphics.guiWidth() - barWidth) / 2;
        int y = 12;
        float fraction = Mth.clamp(player.getHealth() / player.getMaxHealth(), 0.0F, 1.0F);

        graphics.fill(x - 1, y - 1, x + barWidth + 1, y + 6, BORDER);
        graphics.fill(x, y, x + barWidth, y + 5, 0xFF141414);
        int filled = (int) (fraction * barWidth);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + 5, 0xFFCC00FA);
            graphics.fill(x, y, x + filled, y + 2, 0xFFE079FA);
        }
        graphics.drawCenteredString(mc.font, "Ender Dragon",
                graphics.guiWidth() / 2, y - 11, 0xFFE079FA);
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int w, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + CHIP_H - 1, x + w, y + CHIP_H, color);
        graphics.fill(x, y, x + 1, y + CHIP_H, color);
        graphics.fill(x + w - 1, y, x + w, y + CHIP_H, color);
    }
}
