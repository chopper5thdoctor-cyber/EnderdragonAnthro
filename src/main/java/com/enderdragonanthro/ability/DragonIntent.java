package com.enderdragonanthro.ability;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 *   <li><b>Restrained</b> — the numbers the form shipped with. A claw is 10,
 *       which is a stone sword. Nothing breaks that you did not mean to break.
 *   <li><b>Boss</b> — the weight measured against the Warden. A claw is 35, and
 *       an empty hand takes a seven-block sphere out of the world.
 *   <li><b>Enderdragon</b> — the same, and the punch detonates, and flight
 *       stops asking permission from the terrain.
 * </ul>
 *
 * The empty-hand rule is what makes Boss liveable as the default. A punch is
 * something you throw with a fist; holding a pickaxe means you are mining, and
 * mining should mine. Without it, every left click anywhere in the world would
 * be a crater, and there would be no way to place a torch without redecorating.
 */
public enum DragonIntent {
    /** As the form shipped: canon head-hit, 1 -> 10. */
    RESTRAINED("Restrained", ChatFormatting.GRAY, 9.0, false, false, false),
    /** Warden weight. The punch lands, but only bare-handed, and it does not burst. */
    BOSS("Boss", ChatFormatting.LIGHT_PURPLE, 34.0, true, false, false),
    /** All of it: the punch detonates and the sky stops mattering. */
    ENDERDRAGON("Enderdragon", ChatFormatting.DARK_PURPLE, 34.0, true, true, true);

    private final String label;
    private final ChatFormatting tint;
    private final double attackBonus;
    private final boolean craters;
    private final boolean detonates;
    private final boolean tunnels;

    DragonIntent(String label, ChatFormatting tint, double attackBonus,
                 boolean craters, boolean detonates, boolean tunnels) {
        this.label = label;
        this.tint = tint;
        this.attackBonus = attackBonus;
        this.craters = craters;
        this.detonates = detonates;
        this.tunnels = tunnels;
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
        return this != RESTRAINED;
    }

    // ------------------------------------------------------------------ state

    /**
     * Boss by default, which is only safe because of the empty-hand rule.
     *
     * It is what the form is — the powerscale is written against these numbers
     * — and starting a dragon at Restrained would mean the answer to "why am I
     * hitting for ten" is a keypress nobody was told about.
     */
    private static final DragonIntent DEFAULT = BOSS;

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

        player.displayClientMessage(Component.literal("Intent: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(next.label).withStyle(next.tint)), true);
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.7F,
                0.7F + 0.25F * next.ordinal());
    }

    /** Push the current stop's claw into the attribute. Safe to call any time. */
    public static void refresh(ServerPlayer player) {
        DragonFormManager.setAttackBonus(player, of(player).attackBonus());
    }

    /** A player who logs out stops being anyone's problem. */
    public static void forget(Player player) {
        HELD.remove(player.getUUID());
    }
}
