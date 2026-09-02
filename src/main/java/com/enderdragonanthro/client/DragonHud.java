package com.enderdragonanthro.client;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.ability.AbilityAction;
import com.enderdragonanthro.ability.DragonIntent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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

    /**
     * Whether an ability has come back, by the client's own reckoning.
     *
     * The same clock the cooldown sweep on the chip is drawn from, so a held
     * key re-fires exactly when the chip finishes filling rather than on a
     * timer of its own that could drift away from what the player is looking
     * at.
     */
    public static boolean ready(AbilityAction action) {
        Long at = READY_AT.get(action);
        return at == null || clientTicks >= at;
    }

    /** The scale modifier is synced to the client, so it doubles as the form flag. */
    public static boolean isDragonForm(net.minecraft.world.entity.player.Player player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        return scale != null && scale.getModifier(EnderdragonAnthro.id("dragon_scale")) != null;
    }

    /** Width of a key cap. */
    private static final int KEY_W = 20;
    /** Between a cap and its own label. */
    private static final int GAP = 4;
    /** Between the two cap columns. */
    private static final int COLUMN_GAP = 3;
    /** From the screen edge to the outermost text. */
    private static final int MARGIN = 6;

    /**
     * Two columns of caps, labels facing outward.
     *
     * Thirteen abilities in one list ran the full height of the screen and
     * still fell off the bottom. Splitting them in two halves that, and putting
     * each column's labels on its own outward side keeps the caps together in
     * the middle of the block instead of leaving a ragged text gutter down the
     * centre.
     *
     * The stagger is not arranged. Each column is centred on the screen's
     * middle independently, and thirteen splits six and seven, so the taller
     * column starts half a row higher and its caps sit between the other's.
     * Nothing computes an offset; the offset is what centring two columns of
     * different lengths does.
     */
    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !isDragonForm(mc.player)) {
            return;
        }
        renderOwnBossBar(graphics, mc.player, mc);

        List<Map.Entry<KeyMapping, AbilityAction>> chips = new ArrayList<>(CHIPS.entrySet());
        if (chips.isEmpty()) {
            return;
        }
        int rowH = CHIP_H + 2;
        int leftCount = chips.size() / 2;

        // The left column's labels run right-to-left, so the caps can only be
        // placed once the widest of them is known.
        int widest = 0;
        for (int i = 0; i < leftCount; i++) {
            widest = Math.max(widest, labelWidth(mc, chips.get(i).getValue()));
        }
        int xLeft = MARGIN + widest + GAP;
        int xRight = xLeft + KEY_W + COLUMN_GAP;
        int middle = graphics.guiHeight() / 2;

        for (int i = 0; i < chips.size(); i++) {
            boolean onLeft = i < leftCount;
            int count = onLeft ? leftCount : chips.size() - leftCount;
            int index = onLeft ? i : i - leftCount;
            chip(graphics, mc, chips.get(i), onLeft ? xLeft : xRight,
                    middle - count * rowH / 2 + index * rowH, onLeft);
        }
    }

    /** One cap, and its label on whichever side the column faces. */
    private static void chip(GuiGraphics graphics, Minecraft mc,
                             Map.Entry<KeyMapping, AbilityAction> entry,
                             int x, int y, boolean onLeft) {
        AbilityAction action = entry.getValue();
        long sincePress = clientTicks - PRESSED_AT.getOrDefault(action, Long.MIN_VALUE / 2);
        boolean lit = (clientTicks - sincePress >= 0 && sincePress < GLOW_TICKS)
                || TOGGLED.getOrDefault(action, false);

        graphics.fill(x, y, x + KEY_W, y + CHIP_H, lit ? BG_GLOW : BG);
        drawBorder(graphics, x, y, KEY_W, lit ? BORDER_GLOW : BORDER);

        long readyAt = READY_AT.getOrDefault(action, 0L);
        if (readyAt > clientTicks && action.cooldownTicks > 0) {
            float left = (float) (readyAt - clientTicks) / action.cooldownTicks;
            graphics.fill(x + 1, y + 1, x + KEY_W - 1,
                    y + 1 + (int) (left * (CHIP_H - 2)), COOLDOWN_SHADE);
        }
        graphics.drawCenteredString(mc.font, keyLabel(entry.getKey()),
                x + KEY_W / 2, y + (CHIP_H - 8) / 2, lit ? TEXT_GLOW : TEXT);

        int textY = y + (CHIP_H - 8) / 2;
        int textX = onLeft ? x - GAP - labelWidth(mc, action) : x + KEY_W + GAP;
        int after = graphics.drawString(mc.font, action.label, textX, textY,
                lit ? LABEL_ON : LABEL, true);
        if (action == AbilityAction.CRATER) {
            // The one chip that says what it is set to rather than what it
            // does. Drawn as a second string in the stop's own colour --
            // green, amber, then the dragon's violet -- so the state reads
            // at a glance without looking at the ability list at all.
            // drawString returns where it stopped, so the two pieces meet
            // whatever the font does with the first one.
            graphics.drawString(mc.font, intentLabel(), after + 1, textY,
                    DragonIntentClient.get().rgb(), true);
        }
    }

    /**
     * How wide this chip's text is, all of it.
     *
     * The Intent chip draws two strings, and the second one changes as you turn
     * the dial. In the left column the whole thing is right-aligned, so getting
     * this wrong does not clip the label -- it slides the cap. Measuring both
     * pieces is what keeps the two columns from shuffling sideways every time
     * somebody presses B.
     */
    private static int labelWidth(Minecraft mc, AbilityAction action) {
        int width = mc.font.width(action.label);
        if (action == AbilityAction.CRATER) {
            width += 1 + mc.font.width(intentLabel());
        }
        return width;
    }

    private static Component intentLabel() {
        DragonIntent intent = DragonIntentClient.get();
        return Component.literal(intent.label())
                .withStyle(style -> style.withBold(intent.bold()));
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
