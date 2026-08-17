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

    /** One colour each, and everything they say is spoken in it. */
    public static final ChatFormatting[] TINTS = {
            ChatFormatting.RED, ChatFormatting.BLUE,
            ChatFormatting.GREEN, ChatFormatting.GOLD};

    /** TINTS as packed RGB, resolved once — slotOf runs per entity per frame. */
    private static final int[] TINT_RGB = new int[TINTS.length];

    static {
        for (int slot = 0; slot < TINTS.length; slot++) {
            Integer rgb = TINTS[slot].getColor();
            TINT_RGB[slot] = rgb == null ? -1 : rgb;
        }
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
            if (colour.getValue() == TINT_RGB[slot] && text.startsWith(NAMES[slot])) {
                return slot;
            }
        }
        return -1;
    }
}
