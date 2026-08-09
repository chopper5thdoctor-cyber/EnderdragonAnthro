package com.enderdragonanthro;

import com.enderdragonanthro.ability.AbilityAction;
import com.enderdragonanthro.ability.DragonAbilities;
import com.enderdragonanthro.boss.DragonBossBars;
import com.enderdragonanthro.network.AbilityActionPayload;
import com.enderdragonanthro.transform.DragonFormManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
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
        PayloadTypeRegistry.playC2S().register(AbilityActionPayload.TYPE, AbilityActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AbilityActionPayload.TYPE, (payload, context) -> {
            if (payload.action() == AbilityAction.TRANSFORM) {
                DragonFormManager.toggle(context.player());
            } else {
                DragonAbilities.trigger(context.player(), payload.action());
            }
        });

        // Reapply the (transient) attribute modifiers and boss bar when a saved dragon logs in
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                DragonFormManager.onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                DragonFormManager.onLeave(handler.getPlayer()));

        // Dragon-ness survives death: copy the flag to the respawned player, then re-dress it
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                DragonFormManager.copyState(oldPlayer, newPlayer));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                DragonFormManager.onRespawn(newPlayer));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            DragonAbilities.tick(server);
            DragonBossBars.tick(server);
        });

        LOGGER.info("Enderdragon Anthro initialized");
    }
}
