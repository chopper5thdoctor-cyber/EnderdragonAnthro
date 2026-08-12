package com.enderdragonanthro.client;

import com.enderdragonanthro.ability.AbilityAction;
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

/** Keybinds, double-tap glide, and the ability HUD. */
public class EnderdragonAnthroClient implements ClientModInitializer {
    private static final String CATEGORY = "category.enderdragonanthro";
    /** Two jump presses within this many client ticks starts a glide. */
    private static final int DOUBLE_TAP_WINDOW = 8;

    private final Map<KeyMapping, AbilityAction> keys = new LinkedHashMap<>();
    private final Map<AbilityAction, Boolean> rawWasDown = new EnumMap<>(AbilityAction.class);
    private boolean jumpWasDown;
    private int lastJumpTick = -100;
    private int clientTick;

    @Override
    public void onInitializeClient() {
        register("transform", GLFW.GLFW_KEY_Y, AbilityAction.TRANSFORM);
        register("breath", GLFW.GLFW_KEY_R, AbilityAction.BREATH);
        register("fireball", GLFW.GLFW_KEY_G, AbilityAction.FIREBALL);
        register("buffet", GLFW.GLFW_KEY_V, AbilityAction.BUFFET);
        register("charge", GLFW.GLFW_KEY_C, AbilityAction.CHARGE);
        register("crater", GLFW.GLFW_KEY_B, AbilityAction.CRATER);
        register("evade", GLFW.GLFW_KEY_X, AbilityAction.EVADE);
        register("warp", GLFW.GLFW_KEY_K, AbilityAction.WARP);
        register("home", GLFW.GLFW_KEY_J, AbilityAction.RETURN);
        register("boost", GLFW.GLFW_KEY_H, AbilityAction.BOOST);
        register("summon", GLFW.GLFW_KEY_Z, AbilityAction.SUMMON);
        register("select", GLFW.GLFW_KEY_N, AbilityAction.SELECT);
        register("command", GLFW.GLFW_KEY_M, AbilityAction.COMMAND);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DragonHud.tick();
            clientTick++;
            if (client.player == null) {
                return;
            }
            boolean inGame = client.screen == null;
            long window = client.getWindow().getWindow();

            // double-tap jump -> start the glide (only while airborne)
            boolean jumpDown = client.options.keyJump.isDown();
            if (inGame && jumpDown && !jumpWasDown) {
                if (clientTick - lastJumpTick <= DOUBLE_TAP_WINDOW
                        && !client.player.onGround()
                        && DragonHud.isDragonForm(client.player)) {
                    ClientPlayNetworking.send(new AbilityActionPayload(AbilityAction.GLIDE));
                    lastJumpTick = -100;
                } else {
                    lastJumpTick = clientTick;
                }
            }
            jumpWasDown = jumpDown;

            keys.forEach((key, action) -> {
                boolean clicked = false;
                while (key.consumeClick()) {
                    clicked = true;
                }
                // Raw fallback: KeyMapping routes a physical key to only ONE
                // mapping, so a hidden conflict can starve ours of clicks.
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
