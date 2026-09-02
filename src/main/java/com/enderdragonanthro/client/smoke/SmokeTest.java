package com.enderdragonanthro.client.smoke;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.ability.AbilityAction;
import com.enderdragonanthro.client.DragonHearts;
import com.enderdragonanthro.client.DragonHud;
import com.enderdragonanthro.ability.DragonIntent;
import com.enderdragonanthro.ability.ShadeIdentity;
import com.enderdragonanthro.client.DragonIntentClient;
import com.enderdragonanthro.client.DragonPickupClient;
import com.enderdragonanthro.client.DragonSightClient;
import com.enderdragonanthro.client.PickupButton;
import com.enderdragonanthro.client.model.DragonFormModel;
import com.enderdragonanthro.client.model.ShadeModel;
import com.enderdragonanthro.client.render.ShadeLayer;
import com.enderdragonanthro.network.AbilityActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A real client, playing.
 *
 * The third and last harness, and the one that reaches what the other two
 * cannot. The build proves it compiles. audit_injections.py proves the hooks
 * attached. GameTest proves the world changes. None of them can answer the
 * questions that have actually cost this project the most rounds, because all
 * three of those are answered by a screen:
 *
 * <ul>
 *   <li>does the texture we named actually exist — the aim indicators, the
 *       pick-up icons, the shade glow sheets;
 *   <li>does a keypress survive the round trip to the server and come back as
 *       a client-side flag the HUD can read;
 *   <li>does the widget we added to the inventory actually appear.
 * </ul>
 *
 * It drives a real LocalPlayer in a real integrated server: creates a flat
 * world, waits for it to settle, sends the payloads a keypress would send, and
 * looks at what came back.
 *
 * <pre>
 *     ./gradlew runSmoke
 * </pre>
 *
 * Off unless {@code -Denderdragonanthro.smoke=true}, so an ordinary runClient
 * is untouched. Exits non-zero if anything failed, and writes the same report
 * to build/smoke/report.txt.
 */
public final class SmokeTest {
    public static final String FLAG = "enderdragonanthro.smoke";
    /**
     * Whether to go as far as loading a world.
     *
     * Split from the main flag because the two halves have very different
     * costs. The checks that need no world -- textures resolving, layers
     * baking, the baked clips carrying something -- run at the title screen in
     * seconds and are exactly what a CI job wants.
     *
     * Loading a world needs the client to complete a login, and a login asks
     * Mojang for a chat-signing key. On a machine with no route out that call
     * blocks the render thread for five minutes and the harness looks wedged;
     * it is how this was first written, and it is why the two are separate
     * tasks rather than one that sometimes hangs.
     */
    public static final String WORLD_FLAG = "enderdragonanthro.smoke.world";

    /** Ticks to let the world finish loading before touching anything. */
    private static final int SETTLE = 100;
    /** Ticks to wait for a round trip. Generous: the server is in-process. */
    private static final int ROUND_TRIP = 20;

    private static final List<String> PASSED = new ArrayList<>();
    private static final List<String> FAILED = new ArrayList<>();
    private static final List<String> SKIPPED = new ArrayList<>();

    private static boolean asked;
    private static int ticks;
    private static int step;
    private static int waited;

    private SmokeTest() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(FLAG);
    }

    private static boolean wantsWorld() {
        return Boolean.getBoolean(WORLD_FLAG);
    }

    private static int heartbeat;

    /** Driven from the client tick, because everything here needs the game thread. */
    public static void tick(Minecraft client) {
        // A harness that is waiting and a harness that is wedged look identical
        // from outside, and telling them apart cost two runs of five minutes
        // each. It says which every five seconds.
        if (++heartbeat % 100 == 0) {
            EnderdragonAnthro.LOGGER.info("[smoke] waiting: level={} player={} screen={} step={}",
                    client.level != null, client.player != null,
                    client.screen == null ? "none" : client.screen.getClass().getSimpleName(),
                    step);
        }
        if (client.level == null) {
            // The overlay, not the timer, is what says the resources are in.
            // Minecraft puts the title screen up FIRST and the loading overlay
            // over the top of it, so `screen != null` is true from very early
            // on and the five-second settle was racing the reload rather than
            // waiting for it. On a real GPU the reload wins and the harness
            // never noticed; under software rendering it does not, and hearts()
            // asked the GUI atlas for a sprite before the atlas existed --
            // "Tried to lookup sprite, but atlas is not initialized", which
            // reads as a bug in the mod and is a bug in the harness.
            if (client.getOverlay() != null || client.screen == null || ++ticks < SETTLE) {
                return;
            }
            if (!asked) {
                asked = true;
                // Everything that needs no world, first and always, so a run
                // that cannot load one still produces a real answer.
                textures();
                models(client);
                clips();
                if (!wantsWorld()) {
                    EnderdragonAnthro.LOGGER.info(
                            "[smoke] skipping the round trips: -D{} is not set", WORLD_FLAG);
                    skipped();
                    finish(client);
                    return;
                }
                createWorld(client);
            }
            return;
        }
        if (client.player == null || ++ticks < SETTLE * 2) {
            return;
        }
        if (waited > 0) {
            waited--;
            return;
        }
        run(client, step++);
    }

    private static void createWorld(Minecraft client) {
        EnderdragonAnthro.LOGGER.info("[smoke] creating a flat world to play in");
        LevelSettings settings = new LevelSettings("edanthro-smoke", GameType.CREATIVE,
                false, Difficulty.NORMAL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel("edanthro-smoke", settings,
                WorldOptions.defaultWithRandomSeed(),
                registries -> registries.registryOrThrow(
                                net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                client.screen);
    }

    /**
     * One check per step, so anything needing a round trip can simply wait.
     *
     * A switch rather than a list of lambdas because half the steps are "send
     * this and come back later", which reads badly as data and fine as a
     * sequence.
     */
    private static void run(Minecraft client, int at) {
        switch (at) {
            case 0, 1, 2 -> {
                // done at the title screen, before the world existed
            }
            case 3 -> {
                ClientPlayNetworking.send(new AbilityActionPayload(AbilityAction.TRANSFORM));
                waited = ROUND_TRIP;
            }
            case 4 -> check("transform reaches the client",
                    DragonHud.isDragonForm(client.player),
                    "sent TRANSFORM and the client still says human");
            case 5 -> {
                ClientPlayNetworking.send(new AbilityActionPayload(AbilityAction.SIGHT));
                waited = ROUND_TRIP;
            }
            case 6 -> check("Dragonsight reaches the client", DragonSightClient.isOpen(),
                    "sent SIGHT and DragonSightClient never opened");
            case 7 -> {
                ClientPlayNetworking.send(new AbilityActionPayload(AbilityAction.CRATER));
                waited = ROUND_TRIP;
            }
            // One stop up from wherever a dragon starts, rather than a stop
            // named outright -- the check is that the keypress made the round
            // trip and moved the chip, not which stop it happens to land on.
            case 8 -> check("the Intent dial reaches the client",
                    DragonIntentClient.get() != DragonIntent.defaultStop(),
                    "cycled once and the HUD still says "
                            + DragonIntentClient.get().label());
            case 9 -> inventoryButton(client);
            case 10 -> check("the pick-up switch starts on", DragonPickupClient.picksUp(),
                    "the client was never told which way the switch is");
            case 11 -> shot(client);
            case 12 -> shotLanded(client);
            default -> finish(client);
        }
    }

    /**
     * A frame of the real HUD, saved to disk.
     *
     * Every other check here answers a yes/no question, and layout is not one
     * of those. "The ability list is two columns with the labels facing
     * outward" cannot be asserted without describing the arrangement twice and
     * checking that the two descriptions agree, which proves nothing about what
     * is on screen. A picture does, and by this step the harness is standing in
     * a world in dragon form with the HUD up, which is the only hard part.
     *
     * Deliberately not a pass or a fail: a screenshot cannot be wrong, only
     * looked at. It is recorded as a skip carrying the path, so a run that
     * produced one says so and a run that could not says why.
     */
    private static void shot(Minecraft client) {
        if (client.getMainRenderTarget() == null) {
            SKIPPED.add("HUD screenshot -- there is no render target to read");
            return;
        }
        Screenshot.grab(client.gameDirectory, "hud.png", client.getMainRenderTarget(),
                message -> EnderdragonAnthro.LOGGER.info("[smoke] {}", message.getString()));
        // grab() hands the pixels to the IO pool and returns immediately, so
        // finishing on the next step raced it and left a zero-byte file behind.
        // Waited out and then checked, because "the file exists" and "the file
        // has a picture in it" turned out to be different questions.
        waited = SETTLE;
    }

    /** Did the picture actually land? */
    private static void shotLanded(Minecraft client) {
        File file = new File(new File(client.gameDirectory, "screenshots"), "hud.png");
        if (file.isFile() && file.length() > 0) {
            SKIPPED.add("HUD screenshot -- " + file.length() + " bytes at "
                    + file.getAbsolutePath());
        } else {
            SKIPPED.add("HUD screenshot -- nothing was written to "
                    + file.getAbsolutePath());
        }
    }

    /** Every sheet the mod names, actually present. */
    private static void textures() {
        String[] named = {
            "textures/entity/dragon_form.png", "textures/entity/dragon_form_eyes.png",
            "textures/entity/shade.png", "textures/entity/shade_glow.png",
            "textures/entity/shade_pet.png", "textures/entity/shade_pet_glow.png",
            "textures/entity/shade_angry.png", "textures/entity/shade_angry_glow.png",
            "textures/entity/shade_dizzy.png", "textures/entity/shade_dizzy_glow.png",
            "textures/gui/dragonsight/mark_end.png", "textures/gui/dragonsight/mark_gate.png",
            "textures/gui/pickup_on.png", "textures/gui/pickup_off.png",
        };
        for (String path : named) {
            ResourceLocation id = EnderdragonAnthro.id(path);
            check("texture " + path,
                    Minecraft.getInstance().getResourceManager().getResource(id).isPresent(),
                    "named by the code and not in the jar");
        }
        hearts();
    }

    /**
     * The hearts vanilla will ask us for, present and stitched.
     *
     * Two questions rather than one, because a heart is not loaded the way the
     * entity sheets above are. blitSprite does not read a file — it reads the
     * GUI atlas, which is stitched once at startup from everything under
     * textures/gui/sprites. A sprite can therefore be in the jar, be named
     * correctly, and still come out of getSprite as vanilla's missingno, and
     * the visible result of that is a heart bar full of black-and-magenta
     * checks. So the file is checked, and then the atlas is asked whether it
     * kept it.
     *
     * The twelve are every name Gui.HeartType hands to renderHeart for a player
     * who is not poisoned, withered, frozen or absorbing — in both an ordinary
     * world and a hardcore one. container_hardcore is in the list even though no
     * file of that name is shipped: the point of the check is that the mapping's
     * answer for it resolves, and its answer is the ordinary container.
     */
    private static void hearts() {
        Minecraft client = Minecraft.getInstance();
        for (String name : DragonHearts.REPLACED) {
            ResourceLocation ours = DragonHearts.swap(DragonHearts.vanilla(name));
            if (ours == null) {
                check("heart " + name + " is ours", false,
                        "the mapping does not answer for it, so vanilla's red one draws");
                continue;
            }
            check("heart " + name + " is stitched into the GUI atlas",
                    client.getGuiSprites().getSprite(ours).contents().name().equals(ours),
                    "the atlas has no " + ours + ", so it would draw as missingno");
        }
    }

    /** Every layer bakes. A rig that throws here is a rig nobody can render. */
    private static void models(Minecraft client) {
        try {
            client.getEntityModels().bakeLayer(DragonFormModel.LAYER);
            client.getEntityModels().bakeLayer(ShadeModel.LAYER);
            client.getEntityModels().bakeLayer(ShadeLayer.FACE_LAYER);
            check("every model layer bakes", true, "");
        } catch (RuntimeException e) {
            check("every model layer bakes", false, e.toString());
        }
    }

    /** The baked animations came through the converter with something in them. */
    private static void clips() {
        check("the wingbeat has a length", DragonFormModel.BEAT_TICKS > 0,
                "BEAT_TICKS is " + DragonFormModel.BEAT_TICKS);
        check("the tail wag has a length", DragonFormModel.WAG_TICKS > 0,
                "WAG_TICKS is " + DragonFormModel.WAG_TICKS);
        // A shade's clips are allowed to be absent -- one nobody has drawn bakes
        // to an empty table and plays as nothing, which is correct. What is not
        // correct is a clip with keyframes and no length, or a length and no
        // keyframes: either means the bake half-happened, and both draw as a
        // shade that stands rigid with no error anywhere.
        // Every shade, not just the first: they animate from their own clips
        // now, falling back to the court's default, and a slot whose fallback
        // failed to wire up would be a shade that simply stands still.
        for (int slot = 0; slot < ShadeIdentity.NAMES.length; slot++) {
            for (ShadeModel.Clip clip : ShadeModel.Clip.values()) {
                boolean timed = ShadeModel.ticks(slot, clip) > 0;
                boolean keyed = false;
                for (float[] track : ShadeModel.clip(slot, clip)) {
                    keyed |= track != null;
                }
                check(ShadeIdentity.NAMES[slot] + "'s " + clip + " clip is whole",
                        timed == keyed,
                        timed ? "it has a length and no keyframes"
                              : "it has keyframes and no length");
            }
        }
    }

    /**
     * The pick-up switch is on the inventory screen.
     *
     * Opened for real rather than reasoned about, because the widget is added
     * from a mixin on init() and the only honest way to know it is there is to
     * open the screen and look.
     */
    private static void inventoryButton(Minecraft client) {
        client.setScreen(new InventoryScreen(client.player));
        boolean found = client.screen != null && client.screen.children().stream()
                .anyMatch(child -> child instanceof PickupButton);
        // KNOWN GAP, and the reason this fails rather than being made to pass:
        // InventoryScreen.init() in creative mode does not build an inventory
        // screen at all, it calls setScreen(new CreativeModeInventoryScreen)
        // and returns. InventoryPickupMixin targets InventoryScreen, so in
        // creative there is no pick-up switch anywhere. In survival there is,
        // which is why this was never noticed by playing.
        //
        // The harness makes its world CREATIVE, so this check is currently a
        // true report about creative and not about the mixin. Turning it green
        // needs a decision -- either put the switch on the creative screen too,
        // or make the smoke world survival -- and neither is the harness's to
        // make. A target nobody exercised is not a pass.
        String screen = client.screen == null ? "none"
                : client.screen.getClass().getSimpleName();
        check("the pick-up switch is on the inventory", found,
                "opened the inventory in dragon form and got " + screen
                + " with no PickupButton"
                + (screen.startsWith("CreativeMode")
                        ? " -- creative swaps the screen, and the mixin only"
                          + " covers InventoryScreen"
                        : ""));
        client.setScreen(null);
    }

    /** The world-dependent checks, named so a report says what it did not do. */
    private static void skipped() {
        for (String what : new String[] {
            "transform reaches the client", "Dragonsight reaches the client",
            "the Intent dial reaches the client",
            "the pick-up switch is on the inventory", "the pick-up switch starts on"}) {
            SKIPPED.add(what);
            EnderdragonAnthro.LOGGER.info("[smoke] SKIP {}", what);
        }
    }

    private static void check(String what, boolean ok, String why) {
        (ok ? PASSED : FAILED).add(ok ? what : what + " -- " + why);
        EnderdragonAnthro.LOGGER.info("[smoke] {} {}", ok ? "PASS" : "FAIL", what);
    }

    private static void finish(Minecraft client) {
        StringBuilder report = new StringBuilder();
        report.append(PASSED.size()).append(" passed, ")
                .append(FAILED.size()).append(" failed, ")
                .append(SKIPPED.size()).append(" skipped\n\n");
        PASSED.forEach(line -> report.append("  PASS  ").append(line).append('\n'));
        FAILED.forEach(line -> report.append("  FAIL  ").append(line).append('\n'));
        SKIPPED.forEach(line -> report.append("  SKIP  ").append(line).append('\n'));

        EnderdragonAnthro.LOGGER.info("[smoke] ==== {} passed, {} failed, {} skipped ====",
                PASSED.size(), FAILED.size(), SKIPPED.size());
        FAILED.forEach(line -> EnderdragonAnthro.LOGGER.error("[smoke]   {}", line));

        try {
            Path out = Path.of("report.txt");
            Files.writeString(out, report.toString());
            EnderdragonAnthro.LOGGER.info("[smoke] written to {}", out.toAbsolutePath());
        } catch (IOException e) {
            EnderdragonAnthro.LOGGER.error("[smoke] could not write the report", e);
        }
        // Blunt on purpose: the point of this harness is a process exit code
        // that a build can read.
        client.stop();
        Runtime.getRuntime().halt(FAILED.isEmpty() ? 0 : 1);
    }
}
