package com.enderdragonanthro.ability;

/**
 * Every client-initiated action, sent over one C2S payload.
 * Cooldowns are in ticks (20 = 1 second).
 */
public enum AbilityAction {
    TRANSFORM(0, "Form"),
    BREATH(80, "Breath"),
    FIREBALL(60, "Fireball"),
    BUFFET(100, "Buffet"),
    CHARGE(80, "Charge"),
    CRATER(0, "Crater"),
    // evasive jump is the escape button, so it is the expensive one
    EVADE(1200, "Evade"),
    WARP(200, "Warp"),
    RETURN(100, "Home"),
    GLIDE(0, "Glide"),
    BOOST(20, "Boost (Hold)", true),
    SUMMON(200, "Summon"),
    // opens a screen rather than firing anything, so no cooldown
    COMMAND(0, "Court");

    public final int cooldownTicks;
    /** Shown above the key chip on the HUD. */
    public final String label;
    /**
     * Whether holding the key keeps firing it, once per cooldown.
     *
     * Only Boost, and its label has said so all along. Everything else is
     * edge-triggered: holding Fireball down should not empty the sky.
     */
    public final boolean holdable;

    AbilityAction(int cooldownTicks, String label) {
        this(cooldownTicks, label, false);
    }

    AbilityAction(int cooldownTicks, String label, boolean holdable) {
        this.cooldownTicks = cooldownTicks;
        this.label = label;
        this.holdable = holdable;
    }
}
