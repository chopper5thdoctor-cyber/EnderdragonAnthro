package com.enderdragonanthro.client;

import com.enderdragonanthro.ability.AbilityAction;
import com.enderdragonanthro.client.model.CanonDragonHeadModel;
import com.enderdragonanthro.client.model.DragonFormModel;
import com.enderdragonanthro.client.render.DragonFormLayer;
import com.enderdragonanthro.network.AbilityActionPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keybinds per DESIGN.md 4.2: R breath, G fireball, V buffet, C charge, Y transform. */
public class EnderdragonAnthroClient implements ClientModInitializer {
    private static final String CATEGORY = "category.enderdragonanthro";
    private final Map<KeyMapping, AbilityAction> keys = new LinkedHashMap<>();
    private final Map<AbilityAction, Boolean> rawWasDown = new EnumMap<>(AbilityAction.class);

    @Override
    public void onInitializeClient() {
        register("transform", GLFW.GLFW_KEY_Y, AbilityAction.TRANSFORM);
        register("breath", GLFW.GLFW_KEY_R, AbilityAction.BREATH);
        register("fireball", GLFW.GLFW_KEY_G, AbilityAction.FIREBALL);
        register("buffet", GLFW.GLFW_KEY_V, AbilityAction.BUFFET);
        register("charge", GLFW.GLFW_KEY_C, AbilityAction.CHARGE);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DragonHud.tick();
            if (client.player == null) {
                return;
            }
            boolean inGame = client.screen == null;
            long window = client.getWindow().getWindow();
            keys.forEach((key, action) -> {
                boolean clicked = false;
                while (key.consumeClick()) {
                    clicked = true;
                }
                // Raw fallback: KeyMapping routes each physical key to only ONE
                // mapping, so a hidden conflict can starve ours of clicks. GLFW
                // edge detection on the bound key cannot be starved.
                InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(key);
                boolean rawDown = inGame
                        && bound.getType() == InputConstants.Type.KEYSYM
                        && bound.getValue() != GLFW.GLFW_KEY_UNKNOWN
                        && InputConstants.isKeyDown(window, bound.getValue());
                boolean wasDown = rawWasDown.getOrDefault(action, false);
                rawWasDown.put(action, rawDown);

                if ((clicked || (rawDown && !wasDown)) && inGame) {
                    DragonHud.notePress(action);
                    ClientPlayNetworking.send(new AbilityActionPayload(action));
                }
            });
        });

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> DragonHud.render(graphics));

        EntityModelLayerRegistry.registerModelLayer(DragonFormModel.LAYER, DragonFormModel::createLayer);
        EntityModelLayerRegistry.registerModelLayer(CanonDragonHeadModel.LAYER, CanonDragonHeadModel::createLayer);
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (type, renderer, helper, context) -> {
                    if (renderer instanceof PlayerRenderer playerRenderer) {
                        helper.register(new DragonFormLayer(playerRenderer, context.getModelSet()));
                    }
                });
    }

    private void register(String name, int defaultKey, AbilityAction action) {
        KeyMapping mapping = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.enderdragonanthro." + name, defaultKey, CATEGORY));
        keys.put(mapping, action);
        DragonHud.addChip(mapping, action);
    }
}
