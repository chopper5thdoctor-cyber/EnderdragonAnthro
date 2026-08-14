package com.enderdragonanthro.config;

import com.enderdragonanthro.DragonRig;
import com.enderdragonanthro.EnderdragonAnthro;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config/enderdragonanthro.json — how big the dragon is, and whether the model
 * is trimmed to fit its hitbox.
 *
 * Two separate things, which is why they are two settings.
 *
 * heightBlocks is the hitbox — how tall you actually are, and what every
 * size-derived stat comes off. Eight is the canon Ender Dragon.
 *
 * trueProportions decides whether the model is drawn as authored. It defaults
 * to on, because the alternative silently cancels the artist's work: the trim
 * is RENDER_SCALE = 1.8 * 16 / SKULL_HEIGHT, and with the rig's own height in
 * the denominator, ANY rig draws at exactly the hitbox height. Lengthen the
 * legs and the dragon does not get taller — the proportions shift inside the
 * same eight blocks and nothing else changes. Off, the model fills its hitbox
 * exactly and rig height is normalised away; on, the rig's real height is what
 * you see and the head rides about 18 percent above the hitbox, the way the
 * canon dragon's own model overshoots its.
 */
public final class DragonConfig {
    /** Vanilla caps the scale attribute at 16, and a player is 1.8 blocks. */
    private static final double MAX_HEIGHT = 1.8 * 16.0;
    private static final double MIN_HEIGHT = 1.8;

    /** The shape written to and read from disk. */
    private static final class Values {
        double heightBlocks = 8.0;
        boolean trueProportions = false;
        double eyeHeightRatio = 0.0;
    }

    private static Values values = new Values();

    private DragonConfig() {
    }

    /** How tall the form stands, in blocks. Drives the hitbox and every size-derived stat. */
    public static double heightBlocks() {
        return values.heightBlocks;
    }

    /** The scale attribute that gets you there. */
    public static double scaleFactor() {
        return values.heightBlocks / 1.8;
    }

    /** Everything tuned for the canon eight blocks scales by this. */
    public static double sizeRatio() {
        return values.heightBlocks / 8.0;
    }

    /** True: draw the model as authored. False: trim it to fill the hitbox. */
    public static boolean trueProportions() {
        return values.trueProportions;
    }

    /** The scale the model is actually drawn at, under the current setting. */
    public static float renderScale() {
        return values.trueProportions ? DragonRig.TRUE_SCALE : DragonRig.RENDER_SCALE;
    }

    /**
     * Where the eye sits, as a fraction of the hitbox height.
     *
     * Zero — the default — puts it on the model's painted eyes, measured off
     * the emissive sheet at conversion time. Vanilla's 1.62 / 1.8 = 0.9 is
     * fixed and refers to nothing about the model, which is why moving the head
     * in Blockbench never moved the camera; this follows the paint instead.
     *
     * Set a number to override it. Above 1.0 puts the eye outside the hitbox,
     * which is legal and is what a head that overshoots its box wants — it is
     * also where picking through your own ceiling begins, so it is allowed
     * rather than clamped away.
     */
    public static double eyeHeightRatio() {
        double set = values.eyeHeightRatio;
        double ratio = set > 0.0 ? set : DragonRig.eyeRatio(renderScale());
        return Math.max(0.1, Math.min(1.4, ratio));
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir()
                .resolve(EnderdragonAnthro.MOD_ID + ".json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                Values read = gson.fromJson(reader, Values.class);
                if (read != null) {
                    values = read;
                }
            } catch (IOException | RuntimeException e) {
                EnderdragonAnthro.LOGGER.warn("Could not read {}, using defaults", path, e);
                values = new Values();
            }
        }
        double clamped = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, values.heightBlocks));
        if (clamped != values.heightBlocks) {
            EnderdragonAnthro.LOGGER.warn(
                    "heightBlocks {} is outside {}..{} (vanilla caps the scale attribute at 16); using {}",
                    values.heightBlocks, MIN_HEIGHT, MAX_HEIGHT, clamped);
            values.heightBlocks = clamped;
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            gson.toJson(values, writer);          // write it back so the knobs are discoverable
        } catch (IOException e) {
            EnderdragonAnthro.LOGGER.warn("Could not write {}", path, e);
        }
        EnderdragonAnthro.LOGGER.info(
                "Dragon form: {} blocks tall, {} proportions, eye at {} of height ({})",
                values.heightBlocks, values.trueProportions ? "authored" : "trimmed to hitbox",
                String.format("%.4f", eyeHeightRatio()),
                values.eyeHeightRatio > 0.0 ? "set by hand" : "following the painted eyes");
    }
}
