package com.enderdragonanthro.client.render;

import com.enderdragonanthro.client.DragonSightClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Where the doors are, as a hit indicator rather than a label in the world.
 *
 * The first version of this stood a piece of text at each portal and drew it
 * through terrain. That is a fine way to mark something you can point at, and
 * useless for the thing actually being asked: a portal is usually behind you,
 * or under a mountain, or three hundred blocks away, and a marker you have to
 * already be facing to see is not a sense.
 *
 * So it is drawn the way vanilla draws the direction you were hit from — a
 * wedge on a ring around the crosshair, turned to the bearing of the thing it
 * is about. Spin on the spot and the wedges sweep around you. That reads as a
 * direction whether or not there is any line of sight, which is the point.
 *
 * Only the nearest of each kind gets one. A stronghold is twelve frame blocks
 * and a nether portal is a wall of them; twenty overlapping wedges pointing at
 * the same doorway would say less than one.
 */
public final class DragonSightCompass {
    /** How far out from the crosshair the wedges sit. */
    private static final float RADIUS = 54.0F;
    private static final int GATEWAY = 0xFFB14CFF;
    private static final int END = 0xFF4CFFD9;

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
        wedge(graphics, player, nearest(player, DragonSightClient.gateways()), GATEWAY, "gate");
        wedge(graphics, player, nearest(player, DragonSightClient.end()), END, "end");
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

    private static void wedge(GuiGraphics graphics, LocalPlayer player, BlockPos target,
                              int colour, String name) {
        if (target == null) {
            return;
        }
        Vec3 at = Vec3.atCenterOf(target);
        double dx = at.x - player.getX();
        double dz = at.z - player.getZ();

        // Bearing relative to where the player is looking. Minecraft's yaw is
        // measured from south and grows clockwise, so the -z of "north" and the
        // atan2 argument order both have to be taken on its terms rather than
        // on trigonometry's.
        float toTarget = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float bearing = Mth.wrapDegrees(toTarget - player.getYRot());

        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0F);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(bearing));
        pose.translate(0.0F, -RADIUS, 0.0F);
        // A wedge: three stacked bars, widest at the base, so it reads as an
        // arrowhead pointing outward without needing a texture.
        graphics.fill(-6, 4, 6, 6, colour);
        graphics.fill(-4, 2, 4, 4, colour);
        graphics.fill(-2, 0, 2, 2, colour);
        pose.popPose();

        // The distance sits upright beside the crosshair rather than riding the
        // wedge, because text rotated to a bearing is text nobody can read.
        Font font = Minecraft.getInstance().font;
        int metres = (int) Math.round(at.distanceTo(player.position()));
        String label = name + " " + metres + "m";
        int y = cy + (name.equals("gate") ? 22 : 32);
        graphics.drawString(font, label, cx - font.width(label) / 2, y, colour, true);
    }
}
