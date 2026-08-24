package com.enderdragonanthro.client.render;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.client.DragonHud;
import com.enderdragonanthro.client.DragonSightClient;
import com.enderdragonanthro.mixin.BossOverlayAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Where the doors are, as a hit indicator rather than a label in the world.
 *
 * A marker standing at each portal is a fine way to point at something you can
 * already see, and useless for the thing being asked: a portal is usually
 * behind you, or under a mountain, or a thousand blocks off. So it is drawn the
 * way vanilla draws the direction you were hit from — a mark on a ring around
 * the crosshair, turned to the bearing of the thing it is about. Spin on the
 * spot and the marks sweep around you.
 *
 * Two marks, because the two answer different questions. The eye is the
 * stronghold, which comes from the chunk generator rather than from loaded
 * blocks, so it points at one that has never been visited. The doorway is a
 * live nether portal, which is what keeps you from getting lost on the other
 * side — the Nether has no landmarks worth the name and every one of those
 * doorways is a way home.
 *
 * Only the nearest of each kind gets one. A stronghold is twelve frame blocks
 * and a portal is a wall of them; a dozen overlapping marks pointing at one
 * doorway would say less than a single mark does.
 */
public final class DragonSightCompass {
    /**
     * How far out from the crosshair the marks sit.
     *
     * Close enough to read without looking away from what you are flying at.
     * The first pass put them at 58, which is most of the way to the hotbar and
     * reads as decoration at the edge of vision; 17 puts them just clear of the
     * crosshair's own arms, so the whole indicator is one thing you look at.
     */
    private static final float RADIUS = 17.0F;
    private static final int MARK = 16;
    /** The label is a footnote to the mark, not a second thing to read. */
    private static final float LABEL_SCALE = 0.7F;
    /** Screen pixels between labels, before the scale is applied to the text. */
    private static final int LABEL_STEP = 8;
    /** Vanilla's first boss bar sits at 12 and each one steps 19 down. */
    private static final int BOSS_TOP = 12;
    private static final int BOSS_STEP = 19;
    private static final ResourceLocation END_MARK =
            EnderdragonAnthro.id("textures/gui/dragonsight/mark_end.png");
    private static final ResourceLocation GATE_MARK =
            EnderdragonAnthro.id("textures/gui/dragonsight/mark_gate.png");
    private static final int GATE_TINT = 0xFFB14CFF;
    private static final int END_TINT = 0xFF4CFFD9;

    private DragonSightCompass() {
    }

    public static void render(GuiGraphics graphics) {
        if (!DragonSightClient.isOpen()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        int line = 0;
        line = mark(graphics, player, nearest(player, DragonSightClient.end()),
                END_MARK, END_TINT, "stronghold", line);
        mark(graphics, player, nearest(player, DragonSightClient.gateways()),
                GATE_MARK, GATE_TINT, "portal", line);
    }

    /**
     * The first free row under the boss bars.
     *
     * The labels used to hang off the crosshair, which was fine until the ring
     * came in to 17 and they started sharing space with the marks. Up under the
     * bars is where Minecraft already puts standing information about the world
     * rather than about the moment, and it leaves the middle of the screen for
     * looking through.
     *
     * Counting the vanilla stack is not enough, and this landed the label
     * straight on top of a bar the first time it was tried. The bar a
     * transformed player sees is DragonHud's own, drawn with fill() at the same
     * y=12 vanilla starts at — it is not a BossEvent, so it is not in
     * BossHealthOverlay.events and the count came back zero while a bar was
     * plainly on screen. Ours takes a slot like any other.
     */
    private static int belowBossBars() {
        Minecraft client = Minecraft.getInstance();
        int bars = client.gui == null ? 0
                : ((BossOverlayAccessor) client.gui.getBossOverlay()).enderdragonanthro$events()
                        .size();
        if (client.player != null && DragonHud.isDragonForm(client.player)
                && !client.options.hideGui) {
            bars++;
        }
        return BOSS_TOP + bars * BOSS_STEP + 3;
    }

    private static BlockPos nearest(LocalPlayer player, List<BlockPos> positions) {
        BlockPos best = null;
        double closest = Double.MAX_VALUE;
        for (BlockPos pos : positions) {
            double gap = pos.distToCenterSqr(player.getX(), player.getY(), player.getZ());
            if (gap < closest) {
                closest = gap;
                best = pos;
            }
        }
        return best;
    }

    private static int mark(GuiGraphics graphics, LocalPlayer player, BlockPos target,
                            ResourceLocation texture, int tint, String name, int line) {
        if (target == null) {
            return line;
        }
        Vec3 at = Vec3.atCenterOf(target);

        // Bearing relative to where the player is looking. Minecraft's yaw is
        // measured from south and grows clockwise, so both the argument order
        // and the offset here are on its terms rather than trigonometry's.
        float toTarget = (float) (Mth.atan2(at.z - player.getZ(), at.x - player.getX())
                * 180.0 / Math.PI) - 90.0F;
        float bearing = Mth.wrapDegrees(toTarget - player.getYRot());

        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(bearing));
        pose.translate(0.0F, -RADIUS, 0.0F);
        // The mark turns with the bearing, so it reads as pointing outward
        // rather than as a sticker that happens to be over there.
        graphics.blit(texture, -MARK / 2, -MARK / 2, 0, 0.0F, 0.0F, MARK, MARK, MARK, MARK);
        pose.popPose();

        // The label stays upright rather than riding the mark, because text
        // rotated to a bearing is text nobody can read, and it sits up under
        // the boss bars rather than by the crosshair, because the ring is close
        // enough now that anything below it would be inside it. The coordinates
        // are here because a bearing tells you which way to set off and nothing
        // about where you are going.
        //
        // Drawn under a scale rather than at the font's own size: this is a
        // number you glance at once every few minutes.
        Font font = Minecraft.getInstance().font;
        int metres = (int) Math.round(at.distanceTo(player.position()));
        String label = name + "  " + target.getX() + ", " + target.getZ() + "  (" + metres + "m)";
        pose.pushPose();
        pose.translate(cx, belowBossBars() + line * LABEL_STEP, 0.0F);
        pose.scale(LABEL_SCALE, LABEL_SCALE, 1.0F);
        graphics.drawString(font, label, -font.width(label) / 2, 0, tint, true);
        pose.popPose();
        return line + 1;
    }
}
