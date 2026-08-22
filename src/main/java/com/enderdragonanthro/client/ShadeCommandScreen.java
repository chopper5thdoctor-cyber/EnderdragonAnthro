package com.enderdragonanthro.client;

import com.enderdragonanthro.ability.DragonMinions;
import com.enderdragonanthro.network.ShadeOrderPayload;
import com.enderdragonanthro.network.ShadeStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The court screen: every shade, every order, one click each.
 *
 * Orders used to cycle on a keypress, which made Collect the awkward one — you
 * had to pass through it to reach Crystal, and each pass threw away the load
 * the shade was carrying. Here nothing is passed through.
 *
 * Pet and Recall sit alongside the four duties but are not duties: they happen
 * and the standing order carries on untouched.
 */
public class ShadeCommandScreen extends Screen {
    private static final int PANEL_W = 296;
    private static final int ROW_H = 58;
    /** Five duties across the top of a shade's block, then its own controls. */
    private static final int DUTY_W = 56;
    private static final int DUTY_GAP = 59;
    /**
     * The second row: a quarry picker, then one button per momentary order.
     *
     * Sized so the three of them fit beside the picker rather than off the
     * panel — Pet, Recall and Portal at 52 apiece with a 4-pixel gutter.
     */
    private static final int QUARRY_W = 130;
    private static final int ACT_W = 50;
    private static final int ACT_GAP = 53;

    /** Kept across rebuilds so a refresh does not lose an unsent choice. */
    private final Map<Integer, String> quarry = new HashMap<>();

    private ShadeStatePayload state;
    private int left;
    private int top;

    public ShadeCommandScreen(ShadeStatePayload state) {
        super(Component.literal("The Court"));
        this.state = state;
    }

    /** A fresh roster arrived from the server. */
    public void refresh(ShadeStatePayload next) {
        this.state = next;
        if (this.minecraft != null && this.minecraft.screen == this) {
            this.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        List<ShadeStatePayload.Entry> shades = this.state.shades();
        this.left = (this.width - PANEL_W) / 2;
        this.top = Math.max(30, (this.height - Math.max(1, shades.size()) * ROW_H) / 2);

        DragonMinions.Order[] orders = DragonMinions.Order.values();
        for (int i = 0; i < shades.size(); i++) {
            ShadeStatePayload.Entry shade = shades.get(i);
            int y = this.top + i * ROW_H;

            int column = 0;
            for (DragonMinions.Order order : orders) {
                if (order.momentary()) {
                    continue;                   // those live on the second row
                }
                boolean standing = shade.order() == order.ordinal();
                Button button = Button.builder(
                                Component.literal(order.label)
                                        .withStyle(standing ? order.colour : ChatFormatting.GRAY),
                                b -> send(shade.slot(), order.ordinal()))
                        .bounds(this.left + column * DUTY_GAP, y + 10, DUTY_W, 20)
                        .build();
                button.active = !standing;      // the standing order is not a choice
                this.addRenderableWidget(button);
                column++;
            }

            String named = this.quarry.getOrDefault(shade.slot(), shade.quarry());
            this.addRenderableWidget(Button.builder(
                            Component.literal("Quarry: " + describe(named)),
                            b -> this.minecraft.setScreen(new BlockPickerScreen(
                                    this, named, picked -> this.quarry.put(shade.slot(), picked))))
                    .bounds(this.left, y + 32, QUARRY_W, 18).build());

            // Built from the enum rather than written out, which is why Portal
            // had no button at all: the duty row above skips momentary orders
            // on purpose, and this row used to name Pet and Recall by hand, so
            // a third one existed everywhere except on screen.
            int slot = 0;
            for (DragonMinions.Order order : orders) {
                if (!order.momentary()) {
                    continue;
                }
                this.addRenderableWidget(Button.builder(
                                Component.literal(order.label).withStyle(order.colour),
                                b -> send(shade.slot(), order.ordinal()))
                        .bounds(this.left + QUARRY_W + 4 + slot * ACT_GAP, y + 32,
                                ACT_W, 18).build());
                slot++;
            }
        }
    }

    /** A block id shown the way a person reads it, not the way it is stored. */
    private static String describe(String id) {
        if (id == null || id.isBlank()) {
            return "anything it finds";
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        Block block = key == null ? null : BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
        return block == null ? id : block.getName().getString();
    }

    /**
     * Send the quarry the row is actually showing.
     *
     * This used to fall back to an empty string when the player had not touched
     * the picker this time round, so simply pressing Collect again wiped the
     * quarry the shade already had and sent it after anything it fancied.
     */
    private void send(int slot, int order) {
        String named = this.quarry.get(slot);
        if (named == null) {
            named = this.state.shades().stream()
                    .filter(e -> e.slot() == slot)
                    .map(ShadeStatePayload.Entry::quarry)
                    .findFirst().orElse("");
        }
        ClientPlayNetworking.send(new ShadeOrderPayload(slot, order, named));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFE079FA);

        List<ShadeStatePayload.Entry> shades = this.state.shades();
        if (shades.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.literal("No shade answers yet — summon one first."),
                    this.width / 2, this.height / 2 - 4, 0xFF9A93A5);
            return;
        }
        for (int i = 0; i < shades.size(); i++) {
            ShadeStatePayload.Entry shade = shades.get(i);
            int y = this.top + i * ROW_H;
            graphics.drawString(this.font, Component.literal(shade.name()),
                    this.left, y, 0xFFE079FA, true);
            if (shade.awaySeconds() > 0) {
                // The count is the point: Recall now takes whatever this says,
                // so it has to be visible while you decide whether to wait.
                String away = shade.carrying() + " in hand — back in "
                        + shade.awaySeconds() + "s";
                graphics.drawString(this.font, Component.literal(away),
                        this.left + PANEL_W - this.font.width(away), y, 0xFFD8B84A, true);
            }
        }
    }

    /** Standing in a menu should not stop the world while a fight is on. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
