package com.enderdragonanthro.client;

import com.enderdragonanthro.ability.AbilityAction;
import com.enderdragonanthro.network.AbilityActionPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keybinds per DESIGN.md 4.2: R breath, G fireball, V buffet, C charge, Y transform. */
public class EnderdragonAnthroClient implements ClientModInitializer {
    private static final String CATEGORY = "category.enderdragonanthro";
    private final Map<KeyMapping, AbilityAction> keys = new LinkedHashMap<>();

    @Override
    public void onInitializeClient() {
        register("transform", GLFW.GLFW_KEY_Y, AbilityAction.TRANSFORM);
        register("breath", GLFW.GLFW_KEY_R, AbilityAction.BREATH);
        register("fireball", GLFW.GLFW_KEY_G, AbilityAction.FIREBALL);
        register("buffet", GLFW.GLFW_KEY_V, AbilityAction.BUFFET);
        register("charge", GLFW.GLFW_KEY_C, AbilityAction.CHARGE);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }
            keys.forEach((key, action) -> {
                while (key.consumeClick()) {
                    ClientPlayNetworking.send(new AbilityActionPayload(action));
                }
            });
        });
    }

    private void register(String name, int defaultKey, AbilityAction action) {
        KeyMapping mapping = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.enderdragonanthro." + name, defaultKey, CATEGORY));
        keys.put(mapping, action);
    }
}
