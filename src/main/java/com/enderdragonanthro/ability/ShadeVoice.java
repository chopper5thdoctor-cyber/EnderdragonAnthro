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
 */
public final class ShadeVoice {
    private ShadeVoice() {
    }

    private static final String[] DEFEND = {
        "\"At your side, my liege.\"",
        "\"None reach you but through me, your highness.\"",
        "\"I hold this ground, my king.\"",
        "\"Your shadow has teeth, majesty.\"",
        "\"Command me and it is done — until then, I watch.\"",
        "\"Let them come, sire. I am ready.\"",
        "\"I stand where you stand, my liege.\"",
        "\"Rest if you will. I do not.\"",
        "\"Your highness need not look behind.\"",
        "\"The watch is mine, my king.\"",
    };

    private static final String[] ATTACK = {
        "\"Name them and they are ended, my liege.\"",
        "\"Your enemies are my errand, majesty.\"",
        "\"It dies where it stands, sire.\"",
        "\"As you will it, my king.\"",
        "\"I go, your highness. It will not.\"",
        "\"Point, my liege. I need nothing else.\"",
        "\"Gladly, majesty.\"",
        "\"Whatever you look upon, sire, I hunt.\"",
        "\"Consider it already finished, my king.\"",
        "\"Let me show them what serves you.\"",
    };

    private static final String[] COLLECT = {
        "\"%s, my liege. I will find it.\"",
        "\"I go for %s, your highness.\"",
        "\"%s shall be in your hands, majesty.\"",
        "\"The world keeps %s from you. Not for long, sire.\"",
        "\"I know where %s hides, my king.\"",
        "\"As you ask — %s, and swiftly.\"",
        "\"%s. Nothing else will delay me, my liege.\"",
        "\"I will strip the stone of %s for you, majesty.\"",
        "\"Await me, your highness. I return with %s.\"",
        "\"%s it is, my king. The dark holds no secrets from us.\"",
    };

    private static final String[] CRYSTAL = {
        "\"I will raise a crystal for you, my liege.\"",
        "\"Your domain wants light, majesty. I will set it.\"",
        "\"A crystal, your highness, and I will guard it myself.\"",
        "\"The forge is mine to work, sire.\"",
        "\"It will stand as long as I do, my king.\"",
        "\"I make what you should not have to, my liege.\"",
        "\"Let there be one more of them, majesty.\"",
        "\"Your strength should not come from your own hoard, sire.\"",
        "\"I will build, your highness, and I will keep it whole.\"",
        "\"One crystal, my king, raised in your name.\"",
    };

    private static final String[] DISMANTLE = {
        "\"I will pull it down, my liege.\"",
        "\"Nothing stands that you would see fall, majesty.\"",
        "\"The bedrock yields, your highness.\"",
        "\"It was never ours to keep, sire.\"",
        "\"Unmade, my king, as you wish it.\"",
        "\"I will break what I built, my liege — say the word again and I rebuild.\"",
        "\"Down it comes, majesty.\"",
        "\"Even our own works answer to you, sire.\"",
        "\"I take it apart, your highness. Gladly.\"",
        "\"Let it be as though it never stood, my king.\"",
    };

    private static final String[] PET = {
        "\"...your highness honours me.\"",
        "\"I am not used to gentleness, my liege.\"",
        "\"Oh — majesty, I did not expect—\"",
        "\"I would follow you into the void for less, sire.\"",
        "\"My king remembers me.\"",
        "\"You need not, my liege. But I am glad of it.\"",
        "\"Your highness is kinder than the stories say.\"",
        "\"I will carry this longer than any errand, majesty.\"",
        "\"...\" — it leans in, and does not step away.",
        "\"For this, sire, I would hold the sky up.\"",
    };

    private static final String[] RECALL = {
        "\"At your side, my liege.\"",
        "\"I come, your highness.\"",
        "\"You called, majesty. I am here.\"",
        "\"Wherever I was, sire, this is better.\"",
        "\"Returned, my king.\"",
        "\"Say the word and I never leave again, my liege.\"",
        "\"Here, majesty. Always here.\"",
        "\"I heard you across the world, your highness.\"",
        "\"Back to you, sire, and glad of it.\"",
        "\"Your voice carries further than you know, my king.\"",
    };

    private static final String[] HAUL = {
        "\"Yours, my liege — %d %s.\"",
        "\"%d %s, majesty, and the stone is poorer for it.\"",
        "\"I bring you %d %s, your highness.\"",
        "\"%d %s. Say the word and I go again, sire.\"",
        "\"Into your hands, my king: %d %s.\"",
        "\"The world gave up %d %s, my liege.\"",
        "\"%d %s, majesty. It was well hidden.\"",
        "\"For you, your highness — %d %s.\"",
        "\"%d %s, sire, and not a moment wasted.\"",
        "\"I carried %d %s home to you, my king.\"",
    };

    private static final String[] BARREN = {
        "\"There is no %s within reach, my liege. Forgive me.\"",
        "\"I searched, majesty. This land holds no %s.\"",
        "\"Nothing, your highness. Not one seam of %s.\"",
        "\"I have failed you, sire — there is no %s here.\"",
        "\"The stone is empty of %s, my king.\"",
        "\"Send me further, my liege, and I will find %s.\"",
        "\"No %s, majesty. I would not come back with lies.\"",
        "\"I return empty, your highness. There was no %s to take.\"",
        "\"This ground keeps no %s, sire.\"",
        "\"I could not serve you this time, my king. No %s.\"",
    };

    private static final String[] EARLY = {
        "\"I had not yet begun, my liege.\"",
        "\"Empty-handed, majesty — you called me early.\"",
        "\"Nothing yet, your highness. Send me again?\"",
        "\"I came the moment you asked, sire, and so I bring nothing.\"",
        "\"Too soon, my king, but I would rather be here.\"",
        "\"Your word outranks the errand, my liege. I have nothing.\"",
        "\"I would sooner stand with you empty than dig full, majesty.\"",
        "\"Nothing in hand, your highness. Only me.\"",
        "\"Not a block, sire — but you called, and here I am.\"",
        "\"The seam keeps for another day, my king.\"",
    };

    /** A shade taking up a standing duty. */
    public static String duty(DragonMinions.Order order, RandomSource random, String quarry) {
        String[] lines = switch (order) {
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
