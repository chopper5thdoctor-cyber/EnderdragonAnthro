package com.enderdragonanthro;

import com.enderdragonanthro.config.DragonConfig;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Where things are ON a dragon, for anything that has to point at one.
 *
 * Common rather than client, because the two ends both need the answer and had
 * better agree: the server picks the block a crystal beams at, and the client
 * re-derives the exact point from the same player's interpolated position. When
 * they disagree the beam jumps between the two every few frames.
 *
 * It was {@code getBbHeight() * 0.5} written out in three places — twice here
 * and once on the server — which is both the drift and the wrong answer. Half
 * of an eight-block hitbox is the pelvis. The heart is measured off the rig, so
 * moving the chest in Blockbench moves the beam.
 */
public final class DragonAnatomy {
    private DragonAnatomy() {
    }

    /**
     * Whether this player is transformed, by the one signal both sides have.
     *
     * The scale modifier is an attribute, and attributes are synced, so this is
     * the same answer on the server and on every client watching. The server's
     * own {@code DragonFormManager.isDragon} reads a flag that exists only
     * there; this is the version anybody can ask.
     */
    public static boolean isDragonForm(Player player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        return scale != null && scale.getModifier(EnderdragonAnthro.id("dragon_scale")) != null;
    }

    /**
     * Height of the heart above the feet, in blocks.
     *
     * A fraction of the hitbox rather than a fixed number of blocks, so it
     * survives the height being configured; {@link DragonRig#heartRatio} takes
     * the render scale because a model drawn at true proportions overshoots its
     * hitbox and its chest rides higher up it.
     *
     * Anything not in dragon form gets the middle of its body, which is what
     * everything got before and is right for a human — and for the real ender
     * dragon, whose beam this same code draws during the vanilla fight.
     */
    public static double heartHeight(Player player) {
        double ratio = isDragonForm(player)
                ? DragonRig.heartRatio(DragonConfig.renderScale())
                : 0.5;
        return player.getBbHeight() * ratio;
    }

    /** The world point a beam should land on: the heart, not the pelvis. */
    public static Vec3 heart(Player player) {
        return player.position().add(0.0, heartHeight(player), 0.0);
    }

    /** The same point, interpolated, for anything drawing at frame rate. */
    public static Vec3 heart(Player player, float partialTick) {
        return player.getPosition(partialTick).add(0.0, heartHeight(player), 0.0);
    }
}
