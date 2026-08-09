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
 * Ability key row, shown only while transformed. Chips light up with the
 * canon eye-glow purple when pressed and dim while the ability recharges.
 * All colors come from the sampled palette in DESIGN.md section 2.
 */
public final class DragonHud {
    private static final int CHIP = 20;
    private static final int GAP = 4;
    private static final int GLOW_TICKS = 8;

    // canon palette: scales, horn gray, eye core, eye bloom
    private static final int BG = 0xD0141414;
    private static final int BG_GLOW = 0xF03A0F45;
    private static final int BORDER = 0xFF474747;
    private static final int BORDER_GLOW = 0xFFE079FA;
    private static final int TEXT = 0xFF8A8A8A;
    private static final int TEXT_GLOW = 0xFFF3C5FF;
    private static final int COOLDOWN_SHADE = 0xB0000000;

    private static final Map<KeyMapping, AbilityAction> CHIPS = new LinkedHashMap<>();
    private static final Map<AbilityAction, Long> PRESSED_AT = new EnumMap<>(AbilityAction.class);
    private static final Map<AbilityAction, Long> READY_AT = new EnumMap<>(AbilityAction.class);
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
        if (action.cooldownTicks > 0) {
            READY_AT.put(action, clientTicks + action.cooldownTicks);
        }
    }

    /** The scale modifier is synced to the client, so it doubles as the form flag. */
    private static boolean isDragon(LocalPlayer player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        return scale != null && scale.getModifier(EnderdragonAnthro.id("dragon_scale")) != null;
    }

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !isDragon(mc.player)) {
            return;
        }
        int x = 8;
        int y = graphics.guiHeight() - CHIP - 8;

        for (Map.Entry<KeyMapping, AbilityAction> entry : CHIPS.entrySet()) {
            AbilityAction action = entry.getValue();
            long sincePress = clientTicks - PRESSED_AT.getOrDefault(action, Long.MIN_VALUE / 2);
            float glow = Mth.clamp(1.0F - (float) sincePress / GLOW_TICKS, 0.0F, 1.0F);
            boolean glowing = glow > 0.0F;

            graphics.fill(x, y, x + CHIP, y + CHIP, glowing ? BG_GLOW : BG);
            drawBorder(graphics, x, y, glowing ? BORDER_GLOW : BORDER);

            // recharge shade drains downward as the ability comes back
            long readyAt = READY_AT.getOrDefault(action, 0L);
            if (readyAt > clientTicks && action.cooldownTicks > 0) {
                float left = (float) (readyAt - clientTicks) / action.cooldownTicks;
                int shade = (int) (left * (CHIP - 2));
                graphics.fill(x + 1, y + 1, x + CHIP - 1, y + 1 + shade, COOLDOWN_SHADE);
            }

            String label = entry.getKey().getTranslatedKeyMessage().getString().toUpperCase();
            if (label.length() > 3) {
                label = label.substring(0, 3);
            }
            graphics.drawCenteredString(mc.font, label,
                    x + CHIP / 2, y + (CHIP - 8) / 2, glowing ? TEXT_GLOW : TEXT);

            x += CHIP + GAP;
        }
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + CHIP, y + 1, color);
        graphics.fill(x, y + CHIP - 1, x + CHIP, y + CHIP, color);
        graphics.fill(x, y, x + 1, y + CHIP, color);
        graphics.fill(x + CHIP - 1, y, x + CHIP, y + CHIP, color);
    }
}
