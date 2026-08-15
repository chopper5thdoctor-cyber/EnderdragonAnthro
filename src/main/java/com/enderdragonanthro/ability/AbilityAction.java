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
    BOOST(20, "Boost (Hold)"),
    SUMMON(200, "Summon"),
    // opens a screen rather than firing anything, so no cooldown
    COMMAND(0, "Court");

    public final int cooldownTicks;
    /** Shown above the key chip on the HUD. */
    public final String label;

    AbilityAction(int cooldownTicks, String label) {
        this.cooldownTicks = cooldownTicks;
        this.label = label;
    }
}
