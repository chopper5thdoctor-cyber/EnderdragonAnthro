package com.enderdragonanthro.client;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.ability.AbilityAction;
import com.enderdragonanthro.ability.DragonIntent;
import com.enderdragonanthro.mixin.BossOverlayAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        // Before the early return, and deliberately: the bars are the one thing
        // here a player who is NOT a dragon still has to see, because the whole
        // point of somebody else's bar is that somebody else is looking at it.
        renderBossBars(graphics, mc.player, mc);
        if (!isDragonForm(mc.player)) {
            return;
        }

        List<Map.Entry<KeyMapping, AbilityAction>> all = new ArrayList<>(CHIPS.entrySet());
        if (all.isEmpty()) {
            return;
        }
        int rowH = CHIP_H + 2;
        int leftCount = all.size() / 2;

        // The shortest names go left, because the left column is the one that
        // costs something: its labels are right-aligned, so the widest of them
        // is exactly how far the whole block is pushed off the screen edge.
        // Dragonsight and Fire (Hold) on the left shoved the caps a third of
        // the way across the screen for no reason -- on the right they simply
        // run outward into space nothing else wants.
        //
        // Sorted on the RESERVED width, not the drawn one, and stably, so the
        // order within a column stays the order the keys were registered in.
        List<Map.Entry<KeyMapping, AbilityAction>> byLength = new ArrayList<>(all);
        byLength.sort(Comparator.comparingInt(e -> reserveWidth(mc, e.getValue())));
        Set<AbilityAction> onTheLeft = new HashSet<>();
        for (int i = 0; i < leftCount; i++) {
            onTheLeft.add(byLength.get(i).getValue());
        }
        List<Map.Entry<KeyMapping, AbilityAction>> chips = new ArrayList<>(all.size());
        for (Map.Entry<KeyMapping, AbilityAction> entry : all) {
            if (onTheLeft.contains(entry.getValue())) {
                chips.add(entry);
            }
        }
        for (Map.Entry<KeyMapping, AbilityAction> entry : all) {
            if (!onTheLeft.contains(entry.getValue())) {
                chips.add(entry);
            }
        }

        // The left column's labels run right-to-left, so the caps can only be
        // placed once the widest of them is known.
        int widest = 0;
        for (int i = 0; i < leftCount; i++) {
            widest = Math.max(widest, reserveWidth(mc, chips.get(i).getValue()));
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

    /**
     * The most this chip could ever need, whatever the dial says.
     *
     * Layout uses this and drawing uses {@link #labelWidth}, and the difference
     * between them is the whole point. The Intent chip's text changes width
     * when you press B -- "Passive" is not "Alert" -- so laying out from the
     * drawn width would move the caps, and on a column that is right-aligned
     * that means the entire block hops sideways mid-press.
     *
     * Measuring the longest stop instead pins the geometry. It also decides
     * which column the chip goes in, and deciding THAT from the drawn width
     * would be worse still: a chip could change columns on a keypress.
     */
    private static int reserveWidth(Minecraft mc, AbilityAction action) {
        if (action != AbilityAction.CRATER) {
            return mc.font.width(action.label);
        }
        int stop = 0;
        for (DragonIntent intent : DragonIntent.values()) {
            stop = Math.max(stop, mc.font.width(Component.literal(intent.label())
                    .withStyle(style -> style.withBold(intent.bold()))));
        }
        return mc.font.width(action.label) + 1 + stop;
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

    /** Height of a bar, border included. */
    private static final int BAR_H = 6;
    /** A full-width bar: the same 182 vanilla uses. */
    private static final int BAR_W = 182;
    /**
     * How far above a wide bar its name is written.
     *
     * Vanilla's number. {@code BossHealthOverlay} draws the bar at y and the
     * name at y-9, which means our first bar's name lands exactly in the gap
     * vanilla leaves under its own last one — the two stacks keep the same
     * rhythm rather than one of them having its own idea of spacing.
     */
    private static final int NAME_H = 9;
    /**
     * One row to the next: the bar, and three clear pixels.
     *
     * Tighter than vanilla's 19 because none of our rows needs a name written
     * above it — the wide one borrows the gap left by the stack above, and the
     * compact ones put the name inline. Tight is the point: "multiple SPOV
     * bars, tightly packed" is what was asked for.
     */
    private static final int ROW_STEP = 9;
    /** Between a compact bar's name and its bar. */
    private static final int NAME_GAP = 4;
    /**
     * How far off a dragon can be and still get a bar: as far as you can see.
     *
     * A flat 96 blocks was arbitrary and read as one — a dragon plainly visible
     * on the horizon with no bar under a big render distance, and a bar for one
     * you could not see at all under a small one. Tying it to the render
     * distance makes the rule the obvious one instead: it comes into view, it
     * gets a bar, and turning your chunks up buys you more warning.
     *
     * {@code getEffectiveRenderDistance} is already the smaller of your setting
     * and the server's, so this cannot promise more than the server will send.
     * It cannot promise less either: entities arrive on the client only inside
     * the server's tracking range, so a dragon further out is not in
     * {@code players()} to be measured in the first place. This is the visible
     * half of a limit that exists whether or not it is written down.
     */
    private static double viewRange(Minecraft mc) {
        return mc.options.getEffectiveRenderDistance() * 16.0;
    }
    /** Vanilla's first boss bar, and the top of its stack. */
    private static final int VANILLA_TOP = 12;
    /** Vanilla's spacing between boss bars, from BossHealthOverlay. */
    private static final int VANILLA_STEP = 19;

    /** How many real boss events vanilla is drawing above us right now. */
    private static int vanillaBars(Minecraft mc) {
        return mc.gui == null ? 0
                : ((BossOverlayAccessor) mc.gui.getBossOverlay()).enderdragonanthro$events().size();
    }

    /**
     * The top edge of every bar we are about to draw, in order.
     *
     * The whole geometry of the stack, and the only place it is decided. Three
     * callers used to each do a version of this arithmetic -- the renderer, the
     * compass looking for the first free row, and vanilla's own overlay, which
     * did not know we existed -- and all three disagreed. The screenshot that
     * started this had two bars on the same pixels.
     *
     * Starting under vanilla rather than at 12 is the other half of the fix.
     * Deleting our {@code ServerBossEvent} stopped us colliding with ourselves;
     * a real wither or a real ender dragon still puts a {@code BossEvent} in
     * that space, and a bar drawn at a fixed 12 would land on it.
     *
     * Pure, static, and public so the harness can check it without needing a
     * world, a second player and somebody to look at a screenshot.
     */
    public static int[] bossRowTops(int vanillaBars, int rows) {
        int[] tops = new int[Math.max(rows, 0)];
        int y = VANILLA_TOP + vanillaBars * VANILLA_STEP;
        for (int i = 0; i < tops.length; i++) {
            tops[i] = y;
            y += ROW_STEP;
        }
        return tops;
    }

    /** Height of a bar, for anything laying out under the stack. */
    public static final int BOSS_BAR_H = BAR_H;

    /**
     * The y just below our lowest bar; the top of the stack when we draw none.
     *
     * Reads the same {@link #bossRowTops} the renderer lays out from, so the
     * two cannot drift apart. The compass hangs off this, and the last time it
     * did its own counting it landed a label on top of a bar.
     */
    public static int bossStackBottom() {
        Minecraft mc = Minecraft.getInstance();
        int rows = 0;
        if (mc.player != null && !mc.options.hideGui) {
            rows = (isDragonForm(mc.player) ? 1 : 0) + dragonsInView(mc, mc.player).size();
        }
        int[] tops = bossRowTops(vanillaBars(mc), rows);
        // The bottom of the LAST bar, not the top of the row that would come
        // after it: with one bar up and nothing vanilla, y=18.
        return tops.length == 0 ? VANILLA_TOP + vanillaBars(mc) * VANILLA_STEP
                : tops[tops.length - 1] + BAR_H;
    }

    /**
     * Every dragon on screen, yours first and anyone else's under it.
     *
     * ## Why there can be more than one
     *
     * Under normal play there is exactly one dragon and this is a single bar.
     * The form is a keypress away today, and even once it is gated it will be
     * gated per player rather than globally -- so creative mode, or a piston
     * contraption clever enough to trip whatever the eventual cost is, can put
     * two of them in the same world. Recorded because it is the sort of thing
     * that gets called impossible right up until a screenshot arrives, and the
     * screenshot that prompted this had two bars drawn exactly on top of each
     * other, both illegible.
     *
     * ## Two layouts, on purpose
     *
     * Yours is the wide one with its name centred above it, because it is about
     * you and there is only ever one of it. Everybody else's is a compact row --
     * name on the left, bar on the right -- because there can be any number of
     * those and they have to stack without eating the screen.
     */
    private static void renderBossBars(GuiGraphics graphics, LocalPlayer player, Minecraft mc) {
        boolean mine = isDragonForm(player);
        List<Player> others = dragonsInView(mc, player);
        int[] tops = bossRowTops(vanillaBars(mc), (mine ? 1 : 0) + others.size());
        if (tops.length == 0) {
            return;
        }
        int row = 0;
        if (mine) {
            drawWideBar(graphics, mc, tops[row++]);
        }
        if (others.isEmpty()) {
            return;
        }
        // Widest name first, so every compact bar starts at the same x and the
        // column of them reads as one thing rather than as a ragged staircase.
        int gutter = 0;
        for (Player other : others) {
            gutter = Math.max(gutter, mc.font.width(other.getGameProfile().getName()));
        }
        for (Player other : others) {
            drawCompactBar(graphics, mc, other, tops[row++], gutter);
        }
    }

    /**
     * The other dragons close enough to draw a bar for.
     *
     * Read straight off the other player: health is synched entity data and the
     * scale modifier is an attribute, so both are already on the client. This
     * used to be a {@code ServerBossEvent} per dragon, which meant VANILLA drew
     * it -- at vanilla's fixed top-of-screen position, on top of the bar this
     * class was already drawing there. Doing both here is what makes them agree
     * about where they are, and it deletes a piece of static server state at
     * the same time.
     */
    private static List<Player> dragonsInView(Minecraft mc, Player player) {
        List<Player> others = new ArrayList<>();
        if (mc.level == null) {
            return others;
        }
        // A client holds exactly one level, so being in this list is already the
        // same-dimension test the server-side version had to make by hand.
        //
        // Measured flat. Render distance is a count of chunks around you and
        // says nothing about height, and the case that matters most here is a
        // dragon a long way UP — straight-line distance would drop the bar for
        // one hanging directly overhead in plain sight.
        double range = viewRange(mc);
        double limit = range * range;
        for (Player other : mc.level.players()) {
            double dx = other.getX() - player.getX();
            double dz = other.getZ() - player.getZ();
            if (other != player && isDragonForm(other) && dx * dx + dz * dz < limit) {
                others.add(other);
            }
        }
        // players() is in whatever order people joined and left in, which is not
        // stable across a relog. Sorting by name means a given player keeps their
        // row instead of swapping with somebody else mid-recording.
        others.sort(Comparator.comparing(other -> other.getGameProfile().getName()));
        return others;
    }

    /** Yours: name centred over a full-width bar, as the real fight draws it. */
    private static void drawWideBar(GuiGraphics graphics, Minecraft mc, int y) {
        int x = (graphics.guiWidth() - BAR_W) / 2;
        graphics.drawCenteredString(mc.font, "Ender Dragon",
                graphics.guiWidth() / 2, y - NAME_H, 0xFFE079FA);
        bar(graphics, x, y, BAR_W, health(mc.player));
    }

    /** Somebody else's: name left, bar right, one row tall. */
    private static void drawCompactBar(GuiGraphics graphics, Minecraft mc,
                                       Player dragon, int y, int gutter) {
        int width = BAR_W - gutter - NAME_GAP;
        int x = (graphics.guiWidth() - BAR_W) / 2;
        graphics.drawString(mc.font, dragon.getGameProfile().getName(), x, y - 1,
                0xFFE079FA, true);
        bar(graphics, x + gutter + NAME_GAP, y, width, health(dragon));
    }

    private static float health(Player player) {
        return player.getMaxHealth() <= 0.0F ? 0.0F
                : Mth.clamp(player.getHealth() / player.getMaxHealth(), 0.0F, 1.0F);
    }

    private static void bar(GuiGraphics graphics, int x, int y, int width, float fraction) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + BAR_H, BORDER);
        graphics.fill(x, y, x + width, y + BAR_H - 1, 0xFF141414);
        int filled = (int) (fraction * width);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + BAR_H - 1, 0xFFCC00FA);
            graphics.fill(x, y, x + filled, y + 2, 0xFFE079FA);
        }
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int w, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + CHIP_H - 1, x + w, y + CHIP_H, color);
        graphics.fill(x, y, x + 1, y + CHIP_H, color);
        graphics.fill(x + w - 1, y, x + w, y + CHIP_H, color);
    }
}
