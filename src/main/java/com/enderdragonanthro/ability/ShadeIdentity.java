package com.enderdragonanthro.ability;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.LivingEntity;

/**
 * Who the court are, and how to pick one out of a crowd of endermen.
 *
 * The server knows a shade by its scoreboard tags, which is exactly why the
 * client cannot: tags live in the entity's NBT and are never synced. The
 * renderer still has to answer "is this one of ours, and which one" every
 * frame, so it asks the one thing that is synced and already carries the
 * answer — the custom name. A shade is named for its slot and styled in that
 * slot's colour, and a name tag cannot colour anything, so the pair together
 * is a good deal harder to counterfeit than the name alone.
 */
public final class ShadeIdentity {
    private ShadeIdentity() {
    }

    /**
     * The four of them, in order of summoning.
     *
     * Jean is a French name, so the court is French too, and all four are women:
     * -elle, -anne, -inne, each the old name carried into a feminine French
     * ending rather than four strangers. Keshaire was the one that still read
     * as either.
     */
    public static final String[] NAMES = {"Vaëlle", "Keshanne", "Nyrelle", "Orrinne"};

    /**
     * One colour each, and everything they say is spoken in it.
     *
     * These are the exact accents their outfits are painted with — the same
     * four values make_shade_texture.py puts on the cloth — rather than the
     * nearest of Minecraft's sixteen. That matters now the court are four
     * different people to look at: a name in #E02B2B beside a shade wearing
     * #E02B2B reads as the same person, and RED at #FF5555 beside her does not
     * quite. Chat takes an RGB colour perfectly well; there was never a reason
     * to round.
     *
     * Change one and change the paint, or they drift apart with nothing to say
     * so — which is why the four numbers appear once and everything else asks.
     */
    public static final int[] TINT_RGB = {0xE02B2B, 0x2B6BE0, 0x2BC45A, 0xE08A1E};

    /**
     * What they used to be.
     *
     * A shade is recognised by the colour of her name — see {@link #slotOf} —
     * so sharpening the palette would have made every shade already standing in
     * a saved world unrecognisable: not a shade any more, just an enderman with
     * an odd name, no rig and no face. They are still accepted, so the court
     * you already have survives the change and drifts to the new colour the
     * next time anything renames them.
     */
    private static final int[] LEGACY_RGB = new int[4];

    static {
        ChatFormatting[] was = {ChatFormatting.RED, ChatFormatting.BLUE,
                                ChatFormatting.GREEN, ChatFormatting.GOLD};
        for (int slot = 0; slot < was.length; slot++) {
            Integer rgb = was[slot].getColor();
            LEGACY_RGB[slot] = rgb == null ? -1 : rgb;
        }
    }

    /** Her colour, for anything that draws or writes in it. */
    public static TextColor tint(int slot) {
        return TextColor.fromRgb(TINT_RGB[slot]);
    }

    /** Her name, in her colour. */
    public static Component named(int slot) {
        return Component.literal(NAMES[slot]).withStyle(s -> s.withColor(tint(slot)));
    }

    /**
     * Which of the four this is, or -1 for an ordinary enderman.
     *
     * Matches on the name AND its colour: the duty is appended after the name
     * in the duty's own colour, so only the leading run is compared, and the
     * root style is the slot's tint.
     */
    public static int slotOf(LivingEntity entity) {
        Component name = entity.getCustomName();
        if (name == null) {
            return -1;
        }
        TextColor colour = name.getStyle().getColor();
        if (colour == null) {
            return -1;
        }
        String text = name.getString();
        for (int slot = 0; slot < NAMES.length; slot++) {
            if (!text.startsWith(NAMES[slot])) {
                continue;
            }
            if (colour.getValue() == TINT_RGB[slot] || colour.getValue() == LEGACY_RGB[slot]) {
                return slot;
            }
        }
        return -1;
    }
}
