package com.enderdragonanthro.client;

import com.enderdragonanthro.client.model.DragonFormModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The tail in the wind.
 *
 * Deliberately not the wingbeat. A beat is a one-shot fired by a keypress; this
 * runs on its own the whole time you are gliding, because a tail that only
 * moved when you tapped would read as bolted to the wings rather than pushed
 * around by the air.
 *
 * So there is no trigger here, only a weight. The clip is always at some phase;
 * what changes is how much of it is showing.
 *
 * <h2>Why a weight at all</h2>
 *
 * A looping clip cannot ease itself in. Every frame of the wag -- the first one
 * included -- carries the flight carriage as well as the wag, roughly eighty
 * degrees of tail curl on top of the pose the rig is drawn in. That is correct:
 * the whole body pitches prone in a glide, so a tail curled "down" in model
 * space is a tail streaming backwards in the world. But it means frame zero of
 * the clip sits five and a half blocks from where the tail was standing, and
 * switching the clip on at full strength moves it there between one frame and
 * the next.
 *
 * The loop itself is seamless -- last frame to first frame is exact. The jump
 * is at the other seam, the one between not playing and playing, which no
 * amount of care inside the clip can smooth. So it is smoothed from outside:
 * the weight ramps up over RAMP ticks when the glide starts and back down when
 * it ends, and the tail arrives at its flight carriage over a third of a second
 * instead of instantly.
 */
public final class DragonTail {
    /**
     * Ticks the tail takes to reach its flight carriage, and to leave it.
     *
     * Six is a third of a second, which is about how long it takes to go from
     * standing to properly gliding. Much shorter reads as a snap; much longer
     * and the tail is still catching up when you have already levelled out.
     */
    private static final int RAMP = 6;

    private static final Map<UUID, Float> WEIGHT = new HashMap<>();
    private static final Map<UUID, Float> WAS = new HashMap<>();

    private DragonTail() {
    }

    /**
     * Move every tail toward where it should be, once a tick.
     *
     * The previous value is kept as well as the current one so the render pass
     * can interpolate between them. Ramping in whole-tick steps and reading the
     * result at framerate would step six times over the ramp, and a six-step
     * fade on something moving five blocks is a stutter you can count.
     */
    public static void tick() {
        for (Player player : net.minecraft.client.Minecraft.getInstance().level == null
                ? java.util.List.<Player>of()
                : net.minecraft.client.Minecraft.getInstance().level.players()) {
            UUID id = player.getUUID();
            float now = WEIGHT.getOrDefault(id, 0.0F);
            WAS.put(id, now);
            float target = flying(player) ? 1.0F : 0.0F;
            WEIGHT.put(id, Mth.approach(now, target, 1.0F / RAMP));
        }
        // Players who have gone out of view stop being anyone's business.
        WEIGHT.keySet().retainAll(WAS.keySet());
    }

    /**
     * Whether the air is doing anything to this tail.
     *
     * Gliding rather than merely airborne: falling off a ledge is not flight,
     * and the carriage this blends into is a glide pose.
     */
    private static boolean flying(Player player) {
        return player.isFallFlying();
    }

    /** How much of the wag is showing, 0..1, smooth at any framerate. */
    public static float weight(Player player, float partialTick) {
        UUID id = player.getUUID();
        return Mth.lerp(partialTick, WAS.getOrDefault(id, 0.0F),
                WEIGHT.getOrDefault(id, 0.0F));
    }

    /**
     * Where in the wag this tail is, 0..1.
     *
     * Off the world clock rather than off a start time, because there is no
     * start: the wind was already blowing. Offset per player so a flight of
     * dragons does not wag in lockstep, which is the tell that turns four
     * creatures back into four copies of one.
     */
    public static float phase(Player player, float partialTick) {
        float t = (player.tickCount + partialTick) / DragonFormModel.WAG_TICKS;
        return Mth.positiveModulo(t + offset(player), 1.0F);
    }

    private static float offset(Player player) {
        return (player.getUUID().hashCode() & 0xFFFF) / 65536.0F;
    }
}
