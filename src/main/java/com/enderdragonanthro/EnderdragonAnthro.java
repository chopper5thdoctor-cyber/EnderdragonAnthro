package com.enderdragonanthro;

import com.enderdragonanthro.ability.AbilityAction;
import com.enderdragonanthro.ability.CrystalHealing;
import com.enderdragonanthro.ability.DragonAbilities;
import com.enderdragonanthro.ability.DragonFlight;
import com.enderdragonanthro.ability.DragonMinions;
import com.enderdragonanthro.ability.EndermanAffection;
import com.enderdragonanthro.ability.HomingCrystals;
import com.enderdragonanthro.boss.DragonBossBars;
import com.enderdragonanthro.config.DragonConfig;
import com.enderdragonanthro.block.ModBlocks;
import com.enderdragonanthro.item.ModItems;
import com.enderdragonanthro.command.ShadeCommand;
import com.enderdragonanthro.network.AbilityActionPayload;
import com.enderdragonanthro.network.DragonBurnPayload;
import com.enderdragonanthro.network.EndermanHappyPayload;
import com.enderdragonanthro.network.HomingCrystalPayload;
import com.enderdragonanthro.network.ShadeOrderPayload;
import com.enderdragonanthro.network.ShadeStatePayload;
import com.enderdragonanthro.particle.ModParticles;
import com.enderdragonanthro.transform.DragonChat;
import com.enderdragonanthro.transform.DragonFormManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnderdragonAnthro implements ModInitializer {
    public static final String MOD_ID = "enderdragonanthro";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        DragonConfig.load();

        PayloadTypeRegistry.playC2S().register(AbilityActionPayload.TYPE, AbilityActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ShadeOrderPayload.TYPE, ShadeOrderPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShadeStatePayload.TYPE, ShadeStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HomingCrystalPayload.TYPE, HomingCrystalPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EndermanHappyPayload.TYPE, EndermanHappyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DragonBurnPayload.TYPE, DragonBurnPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                com.enderdragonanthro.network.DragonSightPayload.TYPE,
                com.enderdragonanthro.network.DragonSightPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AbilityActionPayload.TYPE, (payload, context) -> {
            if (payload.action() == AbilityAction.TRANSFORM) {
                DragonFormManager.toggle(context.player());
            } else {
                DragonAbilities.trigger(context.player(), payload.action());
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(ShadeOrderPayload.TYPE, (payload, context) ->
                DragonMinions.applyOrder(context.player(), payload.slot(),
                        payload.order(), payload.quarry()));

        // Reapply the (transient) attribute modifiers and boss bar when a saved dragon logs in
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                DragonFormManager.onJoin(handler.getPlayer()));
        // The far side of a portal is built by PortalForcer at the only size it
        // knows, so a dragon that fits through a wide gate arrives at a narrow
        // one. Widened on arrival rather than by rewriting the generator, which
        // also fixes the portals that were already there and too small.
        // Being hit is what turns fear into a fight.
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE
                .register((entity, source, dealt, taken, blocked) -> {
                    if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer hitter
                            && DragonFormManager.isDragon(hitter) && entity != hitter) {
                        com.enderdragonanthro.ability.DragonPresence.provoke(entity, hitter);
                    }
                });
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents
                .AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                        com.enderdragonanthro.ability.NetherGate.widenArrival(player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            DragonFormManager.onLeave(handler.getPlayer());
            DragonMinions.onLeave(handler.getPlayer());   // let the held chunks close
        });

        // Dragon-ness survives death: copy the flag to the respawned player, then re-dress it
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                DragonFormManager.copyState(oldPlayer, newPlayer));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                DragonFormManager.onRespawn(newPlayer));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            DragonFormManager.tick(server);
            DragonAbilities.tick(server);
            DragonFlight.tick(server);
            com.enderdragonanthro.ability.DragonSight.tick(server);
            DragonMinions.tick(server);
            com.enderdragonanthro.ability.DragonPresence.tick(server);
            CrystalHealing.tick(server);
            HomingCrystals.tick(server);
            EndermanAffection.tick(server);
            DragonBossBars.tick(server);
        });

        // Crater punch: a left-click on a block while the toggle is armed blows
        // a 5x5x5 hole instead of starting a normal break.
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level.isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
                return InteractionResult.PASS;
            }
            if (!DragonFormManager.isDragon(sp) || !DragonAbilities.craterArmed(sp)) {
                return InteractionResult.PASS;
            }
            DragonAbilities.crater(sp, pos);
            return InteractionResult.SUCCESS;
        });

        DragonChat.register();
        ModBlocks.register();
        ModItems.register();
        ModParticles.init();
        ShadeCommand.register();

        LOGGER.info("Enderdragon Anthro initialized");
    }
}
