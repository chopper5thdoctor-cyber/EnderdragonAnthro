package com.enderdragonanthro.ability;

/**
 * Every client-initiated action, sent over one C2S payload.
 * Cooldowns (ticks) mirror DESIGN.md section 4.2.
 */
public enum AbilityAction {
    TRANSFORM(0),
    BREATH(160),
    FIREBALL(120),
    BUFFET(200),
    CHARGE(160);

    public final int cooldownTicks;

    AbilityAction(int cooldownTicks) {
        this.cooldownTicks = cooldownTicks;
    }
}
