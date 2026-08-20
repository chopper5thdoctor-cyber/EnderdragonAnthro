package com.enderdragonanthro.client;

import com.enderdragonanthro.ability.AbilityAction;
import com.enderdragonanthro.client.model.DragonFormModel;
import com.enderdragonanthro.client.model.ShadeModel;
import com.enderdragonanthro.client.render.DragonFormLayer;
import com.enderdragonanthro.client.render.ShadeLayer;
import com.enderdragonanthro.network.AbilityActionPayload;
import com.enderdragonanthro.client.particle.PurpleHeartParticle;
import com.enderdragonanthro.client.render.EndermanHappyLayer;
import com.enderdragonanthro.network.EndermanHappyPayload;
import com.enderdragonanthro.network.HomingCrystalPayload;
import com.enderdragonanthro.network.ShadeStatePayload;
import com.enderdragonanthro.particle.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.renderer.entity.EndermanRenderer;
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
        register("sight", GLFW.GLFW_KEY_H, AbilityAction.SIGHT);
        register("summon", GLFW.GLFW_KEY_Z, AbilityAction.SUMMON);
        register("command", GLFW.GLFW_KEY_M, AbilityAction.COMMAND);
        register("dragonfire", GLFW.GLFW_KEY_N, AbilityAction.DRAGONFIRE);

        // The court screen is server-driven: the command key asks for the
        // roster, and every later change is pushed to keep an open screen live.
        ClientPlayNetworking.registerGlobalReceiver(ShadeStatePayload.TYPE,
                (payload, context) -> ShadeCourtClient.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(HomingCrystalPayload.TYPE,
                (payload, context) -> HomingCrystalsClient.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(EndermanHappyPayload.TYPE,
                (payload, context) -> EndermanHappyClient.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(
                com.enderdragonanthro.network.DragonBurnPayload.TYPE,
                (payload, context) -> DragonBurnClient.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(
                com.enderdragonanthro.network.DragonSightPayload.TYPE,
                (payload, context) -> DragonSightClient.accept(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
                .register((handler, client) -> {
                    HomingCrystalsClient.clear();
                    EndermanHappyClient.clear();
                    DragonWings.clear();
                    DragonBurnClient.clear();
                    CrystalBeamAim.clear();
                    DragonSightClient.clear();
                });

        // Without this the block draws on the SOLID layer, where alpha is not
        // read at all and every transparent texel comes out opaque black -- a
        // black slab with flames along the bottom of it.
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.enderdragonanthro.block.ModBlocks.DRAGON_FIRE,
                net.minecraft.client.renderer.RenderType.cutout());

        ParticleFactoryRegistry.getInstance().register(
                ModParticles.PURPLE_HEART, PurpleHeartParticle.Provider::new);
        // Vanilla's flame behaviour, this mod's sprite: it drifts and fades
        // exactly as a flame should, and none of that was worth rewriting.
        ParticleFactoryRegistry.getInstance().register(
                ModParticles.DRAGON_FLAME, net.minecraft.client.particle.FlameParticle.Provider::new);
        // Vanilla's smoke behaviour, minus the grey it multiplies onto its own
        // white masks -- see DragonSmokeParticle, which exists for that alone.
        ParticleFactoryRegistry.getInstance().register(
                ModParticles.DRAGON_SMOKE,
                com.enderdragonanthro.client.particle.DragonSmokeParticle.Provider::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DragonHud.tick();
            DragonWings.tick();
            DragonBurnClient.tick();
            EndermanHappyClient.tick();
            clientTick++;
            if (client.player == null) {
                return;
            }
            boolean inGame = client.screen == null;
            long window = client.getWindow().getWindow();

            // double-tap jump -> start the glide (only while airborne)
            boolean jumpDown = client.options.keyJump.isDown();
            if (inGame && jumpDown && !jumpWasDown) {
                boolean dragon = DragonHud.isDragonForm(client.player);
                if (dragon && client.player.isFallFlying()) {
                    // Already flying: every tap is a beat, not every second
                    // tap. Waiting for a pair meant a boost roughly every
                    // thirteen ticks however fast you hit the key, which is
                    // why spamming space did not hold speed. The double tap
                    // is how a glide STARTS; it has no business also being
                    // how a glide is sustained.
                    DragonWings.beat(client.player);
                    ClientPlayNetworking.send(new AbilityActionPayload(AbilityAction.GLIDE));
                    lastJumpTick = -100;
                } else if (clientTick - lastJumpTick <= DOUBLE_TAP_WINDOW
                        && !client.player.onGround()
                        && dragon) {
                    // GLIDE carries a free boost, so tapping space is a boost
                    // in its own right and had every business flapping. The
                    // beat is the same one the Boost key starts, which is what
                    // keeps it uninterruptible: DragonWings.beat refuses to
                    // restart a stroke already in flight, so tapping faster
                    // than a wingbeat lasts queues nothing and snaps nothing --
                    // the wing finishes, and the next tap after that flaps.
                    DragonWings.beat(client.player);
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

                // A holdable ability re-fires while the key is down, once its
                // cooldown is up; everything else fires on the press alone.
                // consumeClick can report a held key more than once, so an edge
                // is not enough on its own to keep Boost from flooding.
                boolean fire = action.holdable
                        ? (rawDown && DragonHud.ready(action)) || (clicked && !wasDown)
                        : clicked || (rawDown && !wasDown);
                if (fire && inGame) {
                    DragonHud.notePress(action);
                    ClientPlayNetworking.send(new AbilityActionPayload(action));
                }
            });
        });

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            DragonHud.render(graphics);
            com.enderdragonanthro.client.render.DragonSightCompass.render(graphics);
        });


        EntityModelLayerRegistry.registerModelLayer(DragonFormModel.LAYER, DragonFormModel::createLayer);
        EntityModelLayerRegistry.registerModelLayer(EndermanHappyLayer.LAYER,
                EndermanHappyLayer::createLayer);
        EntityModelLayerRegistry.registerModelLayer(ShadeModel.LAYER,
                ShadeModel::createBodyLayer);
        EntityModelLayerRegistry.registerModelLayer(ShadeLayer.FACE_LAYER,
                ShadeLayer::createFaceLayer);
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (type, renderer, helper, context) -> {
                    if (renderer instanceof PlayerRenderer playerRenderer) {
                        helper.register(new DragonFormLayer(playerRenderer, context.getModelSet()));
                    }
                    if (renderer instanceof EndermanRenderer endermanRenderer) {
                        // The shade goes on first: it is the body, and the ^^ of
                        // a pleased enderman belongs over a face, not under one.
                        helper.register(new ShadeLayer(endermanRenderer, context.getModelSet()));
                        helper.register(new EndermanHappyLayer(endermanRenderer, context.getModelSet()));
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
