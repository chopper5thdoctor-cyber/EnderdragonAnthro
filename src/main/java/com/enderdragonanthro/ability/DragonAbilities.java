package com.enderdragonanthro.ability;

import com.enderdragonanthro.transform.DragonFormManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side ability execution. Damage numbers are the canon dragon's:
 * head/charge 6/10/15 and wing 3/5/7 by difficulty.
 */
public final class DragonAbilities {
    private static final Map<UUID, Map<AbilityAction, Long>> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_CHARGES = new HashMap<>();
    private static final Set<UUID> CRATER_ARMED = new HashSet<>();

    /** How far the breath will look for ground before giving up. */
    private static final double BREATH_REACH = 100.0;
    /** How long the exhale stays drawn, in ticks. */
    private static final int JET_TICKS = 10;
    /** How wide the jet counts as, for what it catches. */
    private static final double JET_RADIUS = 1.5;
    private static final List<Jet> JETS = new ArrayList<>();
    /** Evasive jump always lands at least this far away. */
    private static final double EVADE_MIN_DISTANCE = 1000.0;
    /** Player warp only considers people beyond this. */
    private static final double WARP_MIN_DISTANCE = 100.0;

    private DragonAbilities() {
    }

    public static boolean craterArmed(ServerPlayer player) {
        return CRATER_ARMED.contains(player.getUUID());
    }

    public static void trigger(ServerPlayer player, AbilityAction action) {
        if (!DragonFormManager.isDragon(player)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        Map<AbilityAction, Long> cds = COOLDOWNS.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
        long readyAt = cds.getOrDefault(action, 0L);
        if (now < readyAt) {
            player.displayClientMessage(Component.translatable(
                    "message.enderdragonanthro.cooldown", (readyAt - now + 19) / 20), true);
            return;
        }
        cds.put(action, now + action.cooldownTicks);

        switch (action) {
            case BREATH -> breath(player);
            case FIREBALL -> fireball(player);
            case BUFFET -> wingBuffet(player);
            case CHARGE -> beginCharge(player);
            case CRATER -> toggleCrater(player);
            case EVADE -> evade(player);
            case WARP -> warpToPlayer(player);
            case RETURN -> HomingCrystals.returnHome(player);
            case GLIDE -> DragonFlight.start(player);
            case BOOST -> DragonFlight.boost(player);
            case SUMMON -> DragonMinions.summon(player);
            case SELECT -> DragonMinions.select(player);
            case COMMAND -> DragonMinions.openCommandUi(player);
            case TRANSFORM -> {
            }
        }
    }

    public static void tick(MinecraftServer server) {
        drawJets(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (DragonFormManager.isDragon(player) && player.isSprinting()) {
                chargeContactDamage(player);
            }
        }
        Iterator<Map.Entry<UUID, Integer>> it = ACTIVE_CHARGES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || entry.getValue() <= 0 || !DragonFormManager.isDragon(player)) {
                it.remove();
                continue;
            }
            int left = entry.getValue();
            entry.setValue(left - 1);
            if (left > 4) {
                Vec3 look = player.getLookAngle();
                player.setDeltaMovement(look.x * 1.8, look.y * 0.9 + 0.1, look.z * 1.8);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
            chargeContactDamage(player);
        }
    }

    /**
     * Exhale at the ground you are looking at.
     *
     * A giant's mouth sits ~7 blocks up, so a shallow look angle used to strand
     * the cloud in mid-air. The ray is now traced against terrain first; if it
     * reaches {@link #BREATH_REACH} without touching anything, the endpoint is
     * dropped straight down onto whatever is beneath it. Either way the breath
     * lands on ground rather than hanging in the sky.
     */
    private static void breath(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 mouth = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 far = mouth.add(look.scale(BREATH_REACH));

        BlockHitResult hit = level.clip(new ClipContext(mouth, far,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 landing;
        if (hit.getType() == HitResult.Type.BLOCK) {
            landing = hit.getLocation();
        } else {
            // nothing along the ray: fall from the far end to the ground below it
            BlockHitResult down = level.clip(new ClipContext(far,
                    far.subtract(0.0, level.getHeight(), 0.0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            landing = down.getType() == HitResult.Type.BLOCK ? down.getLocation()
                    : new Vec3(far.x, level.getMinBuildHeight() + 1, far.z);
        }

        // a short trail of clouds up to the landing point, then the pool itself
        double span = mouth.distanceTo(landing);
        int steps = (int) Math.min(6, Math.max(1, span / 6.0));
        for (int i = 1; i <= steps; i++) {
            Vec3 at = mouth.add(landing.subtract(mouth).scale((double) i / steps));
            spawnCloud(level, player, at, i == steps ? 4.0F : 2.0F);
        }
        scorch(level, player, mouth, landing);
        JETS.add(new Jet(player.getUUID(), level.dimension(), mouth, landing,
                level.getGameTime() + JET_TICKS));
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    /**
     * Everything standing in the jet, hurt exactly as the pool would hurt it.
     *
     * The same MobEffectInstance the cloud carries, rather than a damage figure
     * of this mod's own, so the answer to "what does dragon's breath do to this"
     * is vanilla's answer -- undead are healed by it here too, because they are
     * healed by it standing in the pool.
     *
     * Once, at the moment of the exhale. The jet stays lit for half a second
     * afterwards, but a beam that reapplied instant damage every tick it was
     * drawn would be twenty times the ability anyone asked for.
     */
    private static void scorch(ServerLevel level, ServerPlayer player, Vec3 mouth, Vec3 landing) {
        Vec3 along = landing.subtract(mouth);
        double span = along.length();
        if (span < 1.0e-3) {
            return;
        }
        Vec3 dir = along.scale(1.0 / span);
        Set<UUID> struck = new HashSet<>();
        for (double d = 0.0; d <= span; d += 1.0) {
            Vec3 at = mouth.add(dir.scale(d));
            AABB slice = new AABB(at, at).inflate(JET_RADIUS);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, slice,
                    e -> e != player && e.isAlive() && !DragonMinions.isOwnedBy(player, e))) {
                if (struck.add(target.getUUID())) {
                    target.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1), player);
                }
            }
        }
    }

    /** One exhale, still being drawn. */
    private record Jet(UUID owner, ResourceKey<Level> dimension, Vec3 mouth, Vec3 landing,
                       long until) {
    }

    /**
     * Draw the jets that are still burning.
     *
     * The ray was always there -- it is how the landing point is chosen -- but
     * nothing ever drew it, so the breath appeared at the far end with nothing
     * connecting it to the dragon. This lays dragon's breath along the same
     * segment the raytrace used, so what you see is literally what was traced.
     */
    private static void drawJets(MinecraftServer server) {
        JETS.removeIf(jet -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(jet.owner());
            ServerLevel level = owner == null ? null : owner.serverLevel();
            if (level == null || !level.dimension().equals(jet.dimension())) {
                return true;
            }
            Vec3 along = jet.landing().subtract(jet.mouth());
            double span = along.length();
            for (double d = 0.0; d < span; d += 0.6) {
                Vec3 at = jet.mouth().add(along.scale(d / span));
                // Thickening with distance, the way a jet spreads: tight at the
                // mouth, broad where it meets the ground.
                double spread = 0.08 + 0.35 * (d / Math.max(span, 1.0e-3));
                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                        at.x, at.y, at.z, 1, spread, spread, spread, 0.01);
            }
            return level.getGameTime() >= jet.until();
        });
    }

    private static void spawnCloud(ServerLevel level, ServerPlayer owner, Vec3 at, float radius) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
        cloud.setOwner(owner);
        cloud.setParticle(ParticleTypes.DRAGON_BREATH);
        cloud.setRadius(radius);
        cloud.setDuration(120);
        cloud.setRadiusOnUse(-0.5F);
        cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1));
        level.addFreshEntity(cloud);
    }

    private static void fireball(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        DragonFireball ball = new DragonFireball(level, player, look);
        ball.setPos(player.getEyePosition().add(look.scale(2.0)));
        level.addFreshEntity(ball);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT,
                SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    private static void wingBuffet(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        AABB area = player.getBoundingBox().inflate(6.0, 3.0, 6.0);
        float damage = byDifficulty(level.getDifficulty(), 3.0F, 5.0F, 7.0F);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive() && !DragonMinions.isOwnedBy(player, e))) {
            target.hurt(level.damageSources().playerAttack(player), damage);
            Vec3 away = target.position().subtract(player.position()).normalize();
            target.setDeltaMovement(away.x * 2.0, 0.8, away.z * 2.0);
            target.hurtMarked = true;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP,
                SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    private static void beginCharge(ServerPlayer player) {
        ACTIVE_CHARGES.put(player.getUUID(), 12);
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5F, 1.4F);
    }

    private static void chargeContactDamage(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        float damage = byDifficulty(level.getDifficulty(), 6.0F, 10.0F, 15.0F);
        AABB path = player.getBoundingBox().expandTowards(player.getDeltaMovement()).inflate(1.0);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, path,
                e -> e != player && e.isAlive() && !DragonMinions.isOwnedBy(player, e))) {
            if (target.hurt(level.damageSources().playerAttack(player), damage)) {
                Vec3 away = target.position().subtract(player.position()).normalize();
                target.setDeltaMovement(away.x * 3.0, 1.0, away.z * 3.0);
                target.hurtMarked = true;
            }
        }
    }

    private static void toggleCrater(ServerPlayer player) {
        boolean on = !CRATER_ARMED.remove(player.getUUID());
        if (on) {
            CRATER_ARMED.add(player.getUUID());
        }
        player.displayClientMessage(Component.literal(
                on ? "Crater punch ARMED" : "Crater punch off")
                .withStyle(on ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), true);
    }

    /**
     * Evasive jump: somewhere random, at least a thousand blocks out.
     *
     * This is the panic button, so it carries the longest cooldown in the kit.
     * A handful of bearings are tried and the first that can hold the dragon
     * wins; failing that, nothing happens rather than dumping you inside rock.
     */
    private static void evade(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double dist = EVADE_MIN_DISTANCE + level.random.nextDouble() * 512.0;
            double x = player.getX() + Math.cos(angle) * dist;
            double z = player.getZ() + Math.sin(angle) * dist;
            if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(x, 64, z))) {
                continue;
            }
            Vec3 dest = openSky(player, level, x, z);
            if (dest != null) {
                jump(player, dest);
                player.displayClientMessage(Component.literal(
                        String.format("Gone. %,d blocks.", (int) dist))
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
                return;
            }
        }
        player.displayClientMessage(Component.literal("The void offers no footing.")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    /** Jump to a random player who is not already close by. */
    private static void warpToPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other == player || other.level() != level) {
                continue;
            }
            if (other.distanceToSqr(player) > WARP_MIN_DISTANCE * WARP_MIN_DISTANCE) {
                candidates.add(other);
            }
        }
        if (candidates.isEmpty()) {
            player.displayClientMessage(Component.literal("No one is far enough to reach for.")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        ServerPlayer target = candidates.get(level.random.nextInt(candidates.size()));
        Vec3 beside = target.position().add(
                (level.random.nextDouble() - 0.5) * 6.0, 0.0, (level.random.nextDouble() - 0.5) * 6.0);
        Vec3 dest = fits(player, level, beside) ? beside : nearestFit(player, level, beside);
        if (dest == null) {
            player.displayClientMessage(Component.literal("There is no room beside them.")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        jump(player, dest);
        player.displayClientMessage(Component.literal("You step to " + target.getName().getString() + ".")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void jump(ServerPlayer player, Vec3 dest) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 0.6F);
        player.teleportTo(dest.x, dest.y, dest.z);
        player.connection.resetPosition();
        player.resetFallDistance();
        DragonFlight.clear(player);
        // The court comes with you. Without this an evade strands them a
        // thousand blocks back, and a stranded shade is how you end up with
        // two of the same name.
        DragonMinions.recall(player);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    /** How much clear air an evasive landing needs around it. */
    private static final int CLEARANCE = 15;
    /** Full cube checks are the expensive part, so a column only gets a few. */
    private static final int CUBE_BUDGET = 6;

    /**
     * Find honest open ground in a column.
     *
     * The old heightmap lookup is what buried people: {@code Level.getHeight}
     * answers {@code minBuildHeight} — under the bedrock — for a chunk that is
     * not loaded yet, and an evasive jump lands a thousand blocks out in
     * terrain nobody has visited. So the chunk is forced in first, and then the
     * surface is only a starting guess: the landing site has to be a solid
     * block with a {@value #CLEARANCE}-cube of air over it, under real sky
     * where the dimension has any, or the search walks on down the column.
     */
    private static Vec3 openSky(ServerPlayer player, ServerLevel level, double x, double z) {
        BlockPos column = BlockPos.containing(x, 0, z);
        level.getChunk(column);                        // force generation, then ask
        int floor = level.getMinBuildHeight();
        int top = Math.min(level.getMaxBuildHeight() - CLEARANCE - 1,
                level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).getY());
        boolean skies = level.dimensionType().hasSkyLight();
        int budget = CUBE_BUDGET;

        // Downward from the surface: the first candidate is nearly always the
        // right one, and each rejection costs three block lookups, not 3375.
        for (int y = top; y > floor && budget > 0; y--) {
            BlockPos ground = new BlockPos(column.getX(), y, column.getZ());
            if (level.getBlockState(ground).isAir()
                    || !level.getBlockState(ground.above()).isAir()) {
                continue;                              // want a solid top surface
            }
            if (skies && level.getBrightness(LightLayer.SKY, ground.above()) < 12) {
                continue;                              // roofed over: probably a cave
            }
            budget--;
            if (!clearCube(level, ground)) {
                continue;
            }
            Vec3 spot = new Vec3(column.getX() + 0.5, y + 1, column.getZ() + 0.5);
            return fits(player, level, spot) ? spot : nearestFit(player, level, spot);
        }
        return null;
    }

    /** A {@value #CLEARANCE}-cube of air sitting on {@code ground}. */
    private static boolean clearCube(ServerLevel level, BlockPos ground) {
        int half = CLEARANCE / 2;
        // straight up first: a ceiling is the usual reason a spot fails
        for (int dy = 1; dy <= CLEARANCE; dy++) {
            if (!level.getBlockState(ground.above(dy)).isAir()) {
                return false;
            }
        }
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = 1; dy <= CLEARANCE; dy++) {
                    if (!level.getBlockState(ground.offset(dx, dy, dz)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Somewhere at or near {@code near} that will hold the dragon, or null. */
    public static Vec3 findFooting(ServerPlayer player, ServerLevel level, Vec3 near) {
        return fits(player, level, near) ? settle(player, level, near)
                : nearestFit(player, level, near);
    }

    /** Does the player's full bounding box sit clear of terrain at {@code at}? */
    private static boolean fits(ServerPlayer player, ServerLevel level, Vec3 at) {
        AABB box = player.getBoundingBox().move(at.subtract(player.position()));
        return level.noCollision(player, box) && level.getWorldBorder().isWithinBounds(box);
    }

    /** Spiral outward from the aim point looking for room, then settle onto ground. */
    private static Vec3 nearestFit(ServerPlayer player, ServerLevel level, Vec3 aim) {
        double height = player.getBbHeight();
        for (int radius = 1; radius <= 8; radius++) {
            for (int i = 0; i < 12; i++) {
                double angle = (Math.PI * 2 / 12) * i;
                double x = aim.x + Math.cos(angle) * radius * 2.0;
                double z = aim.z + Math.sin(angle) * radius * 2.0;
                for (int dy = 0; dy <= 6; dy++) {
                    for (int sign : new int[]{1, -1}) {
                        Vec3 candidate = new Vec3(x, aim.y + dy * sign, z);
                        if (candidate.y < level.getMinBuildHeight()
                                || candidate.y + height > level.getMaxBuildHeight()) {
                            continue;
                        }
                        if (fits(player, level, candidate)) {
                            return settle(player, level, candidate);
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Drop the candidate onto the first ground below it, so you never hover. */
    private static Vec3 settle(ServerPlayer player, ServerLevel level, Vec3 at) {
        Vec3 cur = at;
        for (int i = 0; i < 64; i++) {
            Vec3 below = cur.subtract(0.0, 1.0, 0.0);
            if (below.y < level.getMinBuildHeight() || !fits(player, level, below)) {
                return cur;
            }
            cur = below;
        }
        return cur;
    }

    /**
     * The 5x5x5 crater, used by the left-click hook when the toggle is armed.
     *
     * Obsidian and end stone hold — those are the canon dragon's own limits and
     * they are what keeps the End fight standing. Bedrock does NOT: breaking
     * the floor out from under yourself is a choice you are allowed to make.
     * Everything comes back silk-touch, so the block itself drops.
     */
    public static void crater(ServerPlayer player, BlockPos centre) {
        ServerLevel level = player.serverLevel();
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-2, -2, -2), centre.offset(2, 2, 2))) {
            var state = level.getBlockState(pos);
            if (state.isAir() || holds(state)) {
                continue;
            }
            BlockPos at = pos.immutable();
            var item = state.getBlock().asItem();
            level.removeBlock(at, false);
            if (item != net.minecraft.world.item.Items.AIR) {
                net.minecraft.world.entity.item.ItemEntity drop =
                        new net.minecraft.world.entity.item.ItemEntity(level,
                                at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                                new net.minecraft.world.item.ItemStack(item));
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }
            broken++;
        }
        if (broken > 0) {
            level.playSound(null, centre, SoundEvents.ENDER_DRAGON_FLAP,
                    SoundSource.PLAYERS, 1.2F, 0.5F);
            level.sendParticles(ParticleTypes.EXPLOSION, centre.getX() + 0.5,
                    centre.getY() + 0.5, centre.getZ() + 0.5, 6, 1.5, 1.5, 1.5, 0.0);
        }
    }

    /** Blocks a dragon cannot break, bedrock deliberately excluded. */
    private static boolean holds(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)
                || state.is(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN)
                || state.is(net.minecraft.world.level.block.Blocks.END_STONE)
                || state.is(net.minecraft.world.level.block.Blocks.END_STONE_BRICKS)
                || state.is(net.minecraft.world.level.block.Blocks.BARRIER)
                || state.is(net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME)
                || state.is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                || state.is(net.minecraft.world.level.block.Blocks.END_GATEWAY);
    }

    private static float byDifficulty(Difficulty difficulty, float easy, float normal, float hard) {
        return switch (difficulty) {
            case PEACEFUL -> 0.0F;
            case EASY -> easy;
            case NORMAL -> normal;
            case HARD -> hard;
        };
    }
}
