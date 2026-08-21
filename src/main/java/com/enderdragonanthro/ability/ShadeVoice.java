package com.enderdragonanthro.ability;

import net.minecraft.util.RandomSource;

/**
 * What the court says, and how.
 *
 * The shades address Jean as a sovereign, because that is what they are: not
 * summoned help but a retinue that chose this. Every duty has ten answers and
 * one is picked at random, so a shade told the same thing twice does not
 * answer the same way twice — four of them replying in chorus was the thing
 * that made them read as spawned mobs rather than as a court.
 *
 * Where a line names the quarry it carries {@code %s}; everything else is
 * literal. The dragon is never addressed by name, only by title.
 *
 * The court is women; the dragon is a man, for now. So the shades keep their
 * own feminine register and the honorifics addressed to Jean are his — liege,
 * lord, sire, grace, and the two that are neither (majesty, your highness).
 * If Jean ever changes, it is those honorifics that move and not the voice
 * they are spoken in, and there is exactly one "him" to go with them.
 */
public final class ShadeVoice {
    private ShadeVoice() {
    }

    /** Raising a gate: matter-of-fact, because it is a service. */
    private static final String[] PORTAL = {
        "\"A way through, majesty.\"",
        "\"The frame is yours. Mind the step.\"",
        "\"Cut wide enough for wings.\"",
        "\"It will hold. I made it hold.\"",
    };

    private static final String[] DEFEND = {
        "\"At your side, my liege.\"",
        "\"None reach you but through me, your highness.\"",
        "\"I hold this ground, and gladly.\"",
        "\"Your shadow has teeth tonight, majesty.\"",
        "\"Ask, and it is done. Until then I watch.\"",
        "\"Let them come, sire. I am not afraid of them.\"",
        "\"Where you stand, I stand, my liege.\"",
        "\"Rest if you will. I do not tire.\"",
        "\"Your highness need never look behind him.\"",
        "\"The watch is mine. Sleep, my lord.\"",
    };

    private static final String[] ATTACK = {
        "\"Name them and they are ended, my liege.\"",
        "\"Your enemies are my errand, majesty.\"",
        "\"It dies where it stands. I promise you.\"",
        "\"As you will it, my lord.\"",
        "\"I go, your highness. It does not.\"",
        "\"Only point, my liege. I need nothing more.\"",
        "\"Gladly, majesty. It has been too long.\"",
        "\"Whatever you look upon, sire, I hunt.\"",
        "\"Consider it finished, your grace.\"",
        "\"Let me show them what serves you.\"",
    };

    private static final String[] COLLECT = {
        "\"%s, my liege. I will find it for you.\"",
        "\"I go for %s, your highness.\"",
        "\"%s shall be in your hands before long, majesty.\"",
        "\"The world keeps %s from you. Not for long, my lord.\"",
        "\"I know where %s hides, my liege.\"",
        "\"As you ask — %s, and swiftly.\"",
        "\"%s. Nothing will delay me, your highness.\"",
        "\"I will strip the stone of %s for you, majesty.\"",
        "\"Wait for me, your grace. I return with %s.\"",
        "\"%s it is. The dark keeps no secrets from us, my liege.\"",
    };

    private static final String[] CRYSTAL = {
        "\"I will raise a crystal for you, my liege.\"",
        "\"Your domain wants light, majesty. Let me set it.\"",
        "\"A crystal, your highness, and I will guard it myself.\"",
        "\"The forge is mine to work, my lord.\"",
        "\"It will stand as long as I do, my liege.\"",
        "\"I make what you should never have to, majesty.\"",
        "\"Let there be one more of them, your highness.\"",
        "\"Your strength should not come out of your own hoard, sire.\"",
        "\"I will build, my liege, and I will keep it whole.\"",
        "\"One crystal, raised in your name, majesty.\"",
    };

    private static final String[] DISMANTLE = {
        "\"I will pull it down, my liege.\"",
        "\"Nothing stands that you would see fall, majesty.\"",
        "\"The bedrock yields to you, your highness.\"",
        "\"It was never ours to keep, my lord.\"",
        "\"Unmade, my liege, as you wish it.\"",
        "\"I break what I built. Say the word and I build it again.\"",
        "\"Down it comes, majesty.\"",
        "\"Even our own works answer to you, your highness.\"",
        "\"I take it apart, sire. Gladly.\"",
        "\"Let it be as though it never stood, my liege.\"",
    };

    private static final String[] PET = {
        "\"...your highness honours me.\"",
        "\"I am not used to being touched gently, my liege.\"",
        "\"Oh — majesty, I did not expect—\"",
        "\"I would follow you into the void for less, my lord.\"",
        "\"My lord remembers me.\"",
        "\"You need not. But I am glad of it, my liege.\"",
        "\"Your highness is kinder than the stories say.\"",
        "\"I will carry this longer than any errand, majesty.\"",
        "\"...\" — she leans in, and does not step away.",
        "\"For this, your grace, I would hold the sky up.\"",
    };

    private static final String[] RECALL = {
        "\"At your side, my liege.\"",
        "\"I am coming, your highness.\"",
        "\"You called, majesty. I am here.\"",
        "\"Wherever I was, my lord, this is better.\"",
        "\"Returned, my liege.\"",
        "\"Say the word and I will never leave again.\"",
        "\"Here, majesty. Always here.\"",
        "\"I heard you across the world, your highness.\"",
        "\"Back to you, sire, and glad of it.\"",
        "\"Your voice carries further than you know, my liege.\"",
    };

    private static final String[] HAUL = {
        "\"Yours, my liege — %d %s.\"",
        "\"%d %s, majesty, and the stone is poorer for it.\"",
        "\"I bring you %d %s, your highness.\"",
        "\"%d %s. Say the word and I go again, my lord.\"",
        "\"Into your hands, my liege: %d %s.\"",
        "\"The world gave up %d %s to me, majesty.\"",
        "\"%d %s, your highness. It was well hidden.\"",
        "\"For you, your grace — %d %s.\"",
        "\"%d %s, my liege, and not a moment wasted.\"",
        "\"I carried %d %s home to you, majesty.\"",
    };

    private static final String[] BARREN = {
        "\"There is no %s within reach, my liege. Forgive me.\"",
        "\"I searched, majesty. This land holds no %s.\"",
        "\"Nothing, your highness. Not one seam of %s.\"",
        "\"I have failed you, my lord — there is no %s here.\"",
        "\"The stone is empty of %s, my liege.\"",
        "\"Send me further, majesty, and I will find you %s.\"",
        "\"No %s, your highness. I would not come back to you with lies.\"",
        "\"I return empty, sire. There was no %s to take.\"",
        "\"This ground keeps no %s, my liege.\"",
        "\"I could not serve you this time, majesty. No %s.\"",
    };

    private static final String[] EARLY = {
        "\"I had not even begun, my liege.\"",
        "\"Empty-handed, majesty — you called me early.\"",
        "\"Nothing yet, your highness. Send me again?\"",
        "\"I came the moment you asked, my lord, so I bring nothing.\"",
        "\"Too soon, my liege, but I would rather be here.\"",
        "\"Your word outranks the errand, majesty. I have nothing.\"",
        "\"I would sooner stand with you empty than dig full, your highness.\"",
        "\"Nothing in hand, sire. Only me.\"",
        "\"Not a single block, my liege — but you called.\"",
        "\"The seam will keep for another day, majesty.\"",
    };

    /** A shade taking up a standing duty. */
    public static String duty(DragonMinions.Order order, RandomSource random, String quarry) {
        String[] lines = switch (order) {
            case PORTAL -> PORTAL;
            case DEFEND -> DEFEND;
            case ATTACK -> ATTACK;
            case COLLECT -> COLLECT;
            case CRYSTAL -> CRYSTAL;
            case DISMANTLE -> DISMANTLE;
            case PET -> PET;
            case RECALL -> RECALL;
        };
        String line = pick(lines, random);
        return line.contains("%s") ? String.format(line, quarry) : line;
    }

    /** Coming home with something. */
    public static String haul(RandomSource random, int count, String quarry) {
        return String.format(pick(HAUL, random), count, quarry);
    }

    /** Coming home with nothing, having genuinely looked. */
    public static String barren(RandomSource random, String quarry) {
        return String.format(pick(BARREN, random), quarry);
    }

    /** Coming home with nothing because it was called back before it started. */
    public static String early(RandomSource random) {
        return pick(EARLY, random);
    }

    private static String pick(String[] lines, RandomSource random) {
        return lines[random.nextInt(lines.length)];
    }
}
