package com.enderdragonanthro.mixin;

import com.enderdragonanthro.client.DragonSightClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Health on every living thing, drawn as a name tag, because a name tag is
 * exactly the thing that already works.
 *
 * Three attempts went into this before someone asked the obvious question. A
 * world pass drew nothing. A feature layer drew it under the mob's feet, and
 * once that was fixed it billboarded inside the model's own yaw and turned
 * edge-on. Injecting at the tail of EntityRenderer.render looked right and drew
 * nothing at all — and that one has a real explanation worth keeping:
 *
 * <pre>
 * if (entity instanceof Leashable …) renderLeash(…);
 * if (!this.shouldShowName(entity)) return;   // ← offset 47, the early exit
 * this.renderNameTag(entity, entity.getDisplayName(), …);
 * </pre>
 *
 * {@code @At("TAIL")} injects at the LAST return, so a mob without a visible
 * name bailed out at the first one and never reached the code. Only things that
 * already had a name tag ever ran it, which is why a named shade showed
 * something and no ordinary mob did.
 *
 * Nothing here draws any more. shouldShowName is answered yes, and the name
 * that vanilla was going to render is answered with the health appended. Every
 * transform, every billboard, the backdrop, the see-through pass and the
 * 64-block cutoff are vanilla's own, running the same path that puts a player's
 * account name over their head. It cannot be positioned wrong, because nothing
 * here positions it.
 */
@Mixin(EntityRenderer.class)
public abstract class DragonSightHealthMixin {
    /** How far off a mob is still worth reading; vanilla cuts tags at 64. */
    private static final double RANGE = 48.0;
    /** Name tags are 0.025. While the sight is open, everything is read big. */
    private static final float MAGNIFY = 2.5F;

    /**
     * Whether this is something the sight should be reading.
     */
    private static boolean enderdragonanthro$marked(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        return DragonSightClient.isOpen()
                && client.player != null
                && entity instanceof LivingEntity
                && entity != client.player
                && entity.distanceToSqr(client.player) <= RANGE * RANGE;
    }

    @Shadow
    protected abstract boolean shouldShowName(Entity entity);

    /**
     * Answered at the call site, not on any of the methods.
     *
     * shouldShowName is overridden three deep — EntityRenderer declares it,
     * LivingEntityRenderer overrides it, and MobRenderer overrides it again —
     * and each override ANDs the one below with a condition a nameless mob
     * fails:
     *
     * <pre>
     * super.shouldShowName(entity) && (entity.shouldShowName()
     *         || entity.hasCustomName() && entity == dispatcher.crosshairPickEntity)
     * </pre>
     *
     * Answering true on any single one of them is multiplied away by the next.
     * A named shade passed because she satisfies the second clause on her own,
     * which is exactly why she worked and a sheep did not.
     *
     * EntityRenderer.render asks the question once, so the answer is given
     * there. That is immune to how many overrides sit between: whatever the
     * chain would have said is still consulted for everything the sight is not
     * looking at.
     */
    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;"
                            + "shouldShowName(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean enderdragonanthro$nameEverything(EntityRenderer<Entity> self, Entity entity) {
        return enderdragonanthro$marked(entity) || this.shouldShowName(entity);
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getDisplayName()"
                            + "Lnet/minecraft/network/chat/Component;"))
    private Component enderdragonanthro$withHealth(Entity entity) {
        Component name = entity.getDisplayName();
        if (!enderdragonanthro$marked(entity) || !(entity instanceof LivingEntity living)) {
            return name;
        }
        float health = living.getHealth();
        float max = living.getMaxHealth();
        // Green through red by what is left, so a glance reads as a threat
        // assessment rather than as a wall of numbers.
        float share = max > 0.0F ? Math.min(1.0F, Math.max(0.0F, health / max)) : 0.0F;
        ChatFormatting colour = share > 0.66F ? ChatFormatting.GREEN
                : share > 0.33F ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return Component.empty().append(name).append(
                Component.literal("  " + Math.round(health) + "/" + Math.round(max))
                        .withStyle(colour));
    }

    /**
     * Read at a glance across a field, not leant in to.
     *
     * Every tag grows while the sight is open, players included. That is the
     * honest reading of a vision mode: it is not that mobs are labelled, it is
     * that the dragon sees more.
     */
    @Redirect(method = "renderNameTag",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void enderdragonanthro$biggerTag(PoseStack poseStack, float x, float y, float z) {
        float factor = DragonSightClient.isOpen() ? MAGNIFY : 1.0F;
        poseStack.scale(x * factor, y * factor, z * factor);
    }
}
