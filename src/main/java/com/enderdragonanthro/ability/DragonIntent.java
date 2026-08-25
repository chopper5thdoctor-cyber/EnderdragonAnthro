package com.enderdragonanthro.ability;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.enderdragonanthro.network.DragonIntentPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How much of it you are letting out.
 *
 * This was a toggle called Explosive Intent, and a toggle was the wrong shape
 * for it: the thing being switched was three separate decisions welded
 * together — how hard you hit, whether your fist takes the ground with it, and
 * whether flight stops caring about walls. A dragon walking through a village
 * wants the first one off and none of the rest; a dragon in a fight wants the
 * first two and not the third.
 *
 * So it is a dial with three stops, cycled with the same key.
 *
 * <ul>
 *   <li><b>Passive</b> — the numbers the form shipped with. A claw is 10,
 *       which is a stone sword. Nothing breaks that you did not mean to break.
 *   <li><b>Alert</b> — the weight measured against the Warden. A claw is 35, and
 *       an empty hand takes a seven-block sphere out of the world.
 *   <li><b>Hostile</b> — the same, and the punch detonates, and flight stops
 *       asking permission from the terrain.
 * </ul>
 *
 * Named as a ladder rather than as three settings, and coloured like one: green
 * to amber to the dragon's own violet, which is the one thing on the HUD drawn
 * in the colour of the creature rather than in the interface's palette. Hostile
 * is also the only bold word on the screen. It should not be possible to glance
 * at the HUD and not know which of these you are in.
 *
 * The empty-hand rule is what makes the upper two stops liveable at all. A
 * punch is something you throw with a fist; holding a pickaxe means you are
 * mining, and mining should mine. Without it, every left click anywhere in the
 * world would be a crater, and there would be no way to place a torch without
 * redecorating.
 */
public enum DragonIntent {
    /** As the form shipped: canon head-hit, 1 -> 10. Nothing breaks by accident. */
    PASSIVE("Passive", ChatFormatting.GREEN, 0xFF54D14A, false, 9.0, false, false, false),
    /** Warden weight. The punch lands, but only bare-handed, and it does not burst. */
    ALERT("Alert", ChatFormatting.GOLD, 0xFFF0A020, false, 34.0, true, false, false),
    /** All of it: the punch detonates and the sky stops mattering. */
    HOSTILE("Hostile", ChatFormatting.LIGHT_PURPLE, 0xFFE079FA, true, 34.0, true, true, true);

    private final String label;
    private final ChatFormatting tint;
    private final int rgb;
    private final boolean bold;
    private final double attackBonus;
    private final boolean craters;
    private final boolean detonates;
    private final boolean tunnels;

    DragonIntent(String label, ChatFormatting tint, int rgb, boolean bold, double attackBonus,
                 boolean craters, boolean detonates, boolean tunnels) {
        this.label = label;
        this.tint = tint;
        this.rgb = rgb;
        this.bold = bold;
        this.attackBonus = attackBonus;
        this.craters = craters;
        this.detonates = detonates;
        this.tunnels = tunnels;
    }

    /** What it is called on screen. */
    public String label() {
        return this.label;
    }

    /**
     * The HUD's colour, as ARGB.
     *
     * Separate from the chat formatting because the two draw through different
     * paths -- a chip label is drawn with an int and a chat component with a
     * style -- and because the HUD wants the dragon's own #E079FA rather than
     * the sixteen-colour approximation of it.
     */
    public int rgb() {
        return this.rgb;
    }

    /** Only Hostile. One bold word on the whole screen, and it means this. */
    public boolean bold() {
        return this.bold;
    }

    /** What a bare claw gains over a bare fist at this stop. */
    public double attackBonus() {
        return this.attackBonus;
    }

    /** Whether a bare-handed left click opens the ground. */
    public boolean craters() {
        return this.craters;
    }

    /** Whether that punch also throws a blast. */
    public boolean detonates() {
        return this.detonates;
    }

    /** Whether flight bores through terrain and outruns a stone dropped beside it. */
    public boolean tunnels() {
        return this.tunnels;
    }

    /**
     * Whether the abilities are at boss weight.
     *
     * One question rather than a number per stop, because the two upper stops
     * differ in what the fist and the wings do, not in what the breath is worth.
     */
    public boolean heavy() {
        return this != PASSIVE;
    }

    // ------------------------------------------------------------------ state

    /**
     * Passive by default. You turn it up; it does not start turned up.
     *
     * This was Alert, on the argument that Alert is what the form is and that
     * starting at Passive makes "why am I only hitting for ten" a keypress
     * nobody was told about. The chip answers that now — it names the stop on
     * screen, in the stop's own colour, from the moment you transform — so the
     * question the old default was guarding against no longer has to be
     * guessed at.
     *
     * What is left is the cost of being wrong in each direction, and it is not
     * symmetrical. Waking up at Passive and wanting more costs one keypress.
     * Waking up at Alert costs whatever you happened to be standing next to,
     * and the empty-hand rule does not help when your hands are empty, which
     * they are most of the time you are walking around.
     */
    private static final DragonIntent DEFAULT = PASSIVE;

    /**
     * The stop a dragon starts on.
     *
     * Exposed so the client's copy starts on the same one. Those were two
     * separate literals naming the same stop, which is fine right up until one
     * of them changes — and then the HUD opens claiming a stop the server is
     * not on, for exactly as long as it takes you to press the key once.
     */
    public static DragonIntent defaultStop() {
        return DEFAULT;
    }

    private static final Map<UUID, DragonIntent> HELD = new HashMap<>();

    public static DragonIntent of(Player player) {
        return HELD.getOrDefault(player.getUUID(), DEFAULT);
    }

    /** Shorthand for the two questions asked from more than one place. */
    public static boolean heavy(Player player) {
        return of(player).heavy();
    }

    public static boolean tunnels(Player player) {
        return of(player).tunnels();
    }

    /**
     * Up one stop, wrapping.
     *
     * The attack attribute has to be rewritten here rather than read on demand,
     * because damage comes off ATTACK_DAMAGE and the game asks that attribute
     * rather than asking us.
     */
    public static void cycle(ServerPlayer player) {
        if (!DragonFormManager.isDragon(player)) {
            return;
        }
        DragonIntent[] all = values();
        DragonIntent next = all[(of(player).ordinal() + 1) % all.length];
        HELD.put(player.getUUID(), next);
        refresh(player);
        tell(player);

        player.displayClientMessage(Component.literal("Intent: ")
                .withStyle(ChatFormatting.GRAY).append(next.name(true)), true);
        // Rising with the ladder, so the ear knows which way you went even when
        // the message has already faded.
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.7F,
                0.7F + 0.25F * next.ordinal());
    }

    /**
     * The stop's name, styled.
     *
     * {@code loud} is what separates the notifier from anything else that wants
     * to say the word: the action bar shouts Hostile in bold and a passing
     * mention of it does not.
     */
    public Component name(boolean loud) {
        Component named = Component.literal(this.label).withStyle(this.tint);
        return loud && this.bold
                ? named.copy().withStyle(ChatFormatting.BOLD) : named;
    }

    /** Tell the client which stop this is, so the HUD can draw it. */
    public static void tell(ServerPlayer player) {
        ServerPlayNetworking.send(player,
                new DragonIntentPayload(of(player).ordinal()));
    }

    /** Push the current stop's claw into the attribute. Safe to call any time. */
    public static void refresh(ServerPlayer player) {
        DragonFormManager.setAttackBonus(player, of(player).attackBonus());
    }

    /** By ordinal, for the payload. Out of range means the default. */
    public static DragonIntent byOrdinal(int index) {
        DragonIntent[] all = values();
        return index >= 0 && index < all.length ? all[index] : DEFAULT;
    }

    /** A player who logs out stops being anyone's problem. */
    public static void forget(Player player) {
        HELD.remove(player.getUUID());
    }
}
