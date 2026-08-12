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
    TELEPORT(60, "Blink"),
    GLIDE(0, "Glide"),
    BOOST(20, "Boost"),
    SUMMON(200, "Summon"),
    COMMAND(10, "Order");

    public final int cooldownTicks;
    /** Shown above the key chip on the HUD. */
    public final String label;

    AbilityAction(int cooldownTicks, String label) {
        this.cooldownTicks = cooldownTicks;
        this.label = label;
    }
}
