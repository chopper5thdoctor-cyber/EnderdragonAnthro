package com.enderdragonanthro.client;

import com.enderdragonanthro.EnderdragonAnthro;
import net.minecraft.resources.ResourceLocation;

/**
 * Which of vanilla's heart sprites a dragon replaces, and with what.
 *
 * A table rather than a branch inside DragonHeartsMixin, which is where it
 * started. Mixin classes do not exist at runtime — they are dissolved into
 * their target — so anything written inside one is unreachable to every harness
 * this project has. The mapping is the part most likely to be wrong (a name
 * misremembered, a sprite that never got drawn) and it was the part nothing
 * could look at. Out here the smoke run asks it the same question the game
 * asks it, one name at a time.
 *
 * <p>The mixin keeps the decision about <em>when</em> to swap. That genuinely
 * needs the game: it depends on whether the player on this client is currently
 * a dragon.
 */
public final class DragonHearts {
    private static final String VANILLA = "hud/heart/";

    private DragonHearts() {
    }

    /**
     * The mod's sprite for one of vanilla's heart names, or null for the ones
     * it does not replace.
     *
     * Poisoned, withered, frozen and absorbing are deliberately absent: a
     * dragon being poisoned should still read as poisoned rather than as one
     * more shade of purple. Anything not named here falls through to vanilla's
     * sprite rather than vanishing, which is why this returns null instead of
     * guessing at a path that may not be in the jar.
     */
    public static ResourceLocation swap(ResourceLocation sprite) {
        String path = sprite.getPath();
        if (!sprite.getNamespace().equals("minecraft") || !path.startsWith(VANILLA)) {
            return null;
        }
        return switch (path.substring(VANILLA.length())) {
            case "container", "container_blinking", "full", "full_blinking",
                 "half", "half_blinking",
                 // A hardcore heart is a separate drawing rather than a tint of
                 // the ordinary one, because the veins are the point of it. A
                 // world you get one life in should not say so in the same
                 // sprite as a world you get infinite ones in.
                 "hardcore_full", "hardcore_full_blinking",
                 "hardcore_half", "hardcore_half_blinking" ->
                    EnderdragonAnthro.id(path);
            // Vanilla names a hardcore container and then ships the ordinary
            // one under it — both pairs are byte-identical in 1.21.1's jar. The
            // veins are in the heart, not in the socket it sits in. So these
            // two names land on the one file rather than on a copy of it, which
            // is what vanilla is doing underneath anyway.
            case "container_hardcore" -> EnderdragonAnthro.id(VANILLA + "container");
            case "container_hardcore_blinking" ->
                    EnderdragonAnthro.id(VANILLA + "container_blinking");
            default -> null;
        };
    }

    /**
     * Every name a dragon is expected to answer for, for the harness to walk.
     *
     * Kept beside the switch so the two are read together. A name that gets
     * added to one and not the other is exactly the drift this list exists to
     * make impossible.
     */
    public static final String[] REPLACED = {
        "container", "container_blinking", "full", "full_blinking",
        "half", "half_blinking",
        "container_hardcore", "container_hardcore_blinking",
        "hardcore_full", "hardcore_full_blinking",
        "hardcore_half", "hardcore_half_blinking",
    };

    /** The vanilla id for one of those names, as the game would ask for it. */
    public static ResourceLocation vanilla(String name) {
        return ResourceLocation.withDefaultNamespace(VANILLA + name);
    }
}
