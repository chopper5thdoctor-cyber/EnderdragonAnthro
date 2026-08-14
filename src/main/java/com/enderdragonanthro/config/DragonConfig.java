package com.enderdragonanthro.config;

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
 * Two separate things, which is why they are two settings. The eight blocks is
 * the canon Ender Dragon's hitbox and sets how tall you actually are. The trim
 * is a fixed 18 percent squash applied so the skull tops out level with that
 * hitbox — the rig is 136 model units to the skull where a player model is 32,
 * so it is a taller build than the box it lives in, at any size. Turning the
 * trim off gives the model exactly the proportions it was drawn at and lets the
 * head overshoot, the way the canon dragon's own does.
 */
public final class DragonConfig {
    /** Vanilla caps the scale attribute at 16, and a player is 1.8 blocks. */
    private static final double MAX_HEIGHT = 1.8 * 16.0;
    private static final double MIN_HEIGHT = 1.8;

    /** The shape written to and read from disk. */
    private static final class Values {
        double heightBlocks = 8.0;
        boolean trueProportions = false;
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
        EnderdragonAnthro.LOGGER.info("Dragon form: {} blocks tall, {} proportions",
                values.heightBlocks, values.trueProportions ? "authored" : "trimmed to hitbox");
    }
}
