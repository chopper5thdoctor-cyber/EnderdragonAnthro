package com.enderdragonanthro.ability;

/**
 * Every client-initiated action, sent over one C2S payload.
 * Cooldowns (ticks) mirror DESIGN.md section 4.2.
 */
public enum AbilityAction {
    TRANSFORM(0),
    BREATH(80),
    FIREBALL(60),
    BUFFET(100),
    CHARGE(80);

    public final int cooldownTicks;

    AbilityAction(int cooldownTicks) {
        this.cooldownTicks = cooldownTicks;
    }
}
