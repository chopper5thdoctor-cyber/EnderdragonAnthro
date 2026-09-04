package com.enderdragonanthro.ability;

/**
 * Every client-initiated action, sent over one C2S payload.
 * Cooldowns are in ticks (20 = 1 second).
 */
public enum AbilityAction {
    TRANSFORM(0, "Form"),
    BREATH(80, "Breath"),
    FIREBALL(60, "Fireball"),
    BUFFET(100, "Buffet", false, true),
    CHARGE(80, "Charge"),
    CRATER(0, "Intent: "),
    // evasive jump is the escape button, so it is the expensive one
    EVADE(1200, "Evade"),
    WARP(200, "Warp"),
    RETURN(100, "Home"),
    GLIDE(0, "Glide", false, true),
    // Boost lost its key: tapping space already sends GLIDE, and GLIDE
    // carries a boost, so the key was doing what space did. DragonFlight.boost
    // is still called -- from there -- it just is not an action any more.
    SIGHT(0, "Dragonsight"),
    // five ticks between shots, so a held key reads as a stream
    DRAGONFIRE(5, "Fire (Hold)", true, false),
    SUMMON(200, "Summon"),
    // opens a screen rather than firing anything, so no cooldown
    COMMAND(0, "Court");

    public final int cooldownTicks;
    /** Shown above the key chip on the HUD. */
    public final String label;
    /**
     * Whether holding the key keeps firing it, once per cooldown.
     *
     * Only Dragonfire, and its label says so. Everything else is
     * edge-triggered: holding Fireball down should not empty the sky.
     */
    public final boolean holdable;
    /**
     * Whether this action is, physically, a stroke of the wings.
     *
     * Data rather than a line in each of the places that acts on it, and that
     * is the whole reason it exists. Buffet is a six-block wing sweep that
     * throws everything nearby off its feet, and for its entire life the wings
     * did not move while it happened -- on any screen, including the presser's
     * own. Glide had the beat and Buffet did not, because the beat was written
     * into Glide's case in a switch and nobody thought to write a second one.
     *
     * Read by both ends: DragonAbilities.trigger sends the beat to everyone
     * else watching, and the keypress handler starts it locally on the frame
     * the key goes down. Both ask the flag, so the two cannot disagree, and
     * marking a new ability as a wingbeat is one word rather than two edits in
     * different source trees.
     */
    public final boolean flapsWings;

    AbilityAction(int cooldownTicks, String label) {
        this(cooldownTicks, label, false, false);
    }

    AbilityAction(int cooldownTicks, String label, boolean holdable, boolean flapsWings) {
        this.cooldownTicks = cooldownTicks;
        this.label = label;
        this.holdable = holdable;
        this.flapsWings = flapsWings;
    }
}
