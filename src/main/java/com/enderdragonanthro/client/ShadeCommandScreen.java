package com.enderdragonanthro.client;

import com.enderdragonanthro.ability.DragonMinions;
import com.enderdragonanthro.network.ShadeOrderPayload;
import com.enderdragonanthro.network.ShadeStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The court screen: every shade, every order, one click each.
 *
 * Orders used to cycle on a keypress, which made Collect the awkward one —
 * you had to pass through it to reach Crystal, and each pass threw away the
 * load the shade was carrying and re-read whatever block you happened to be
 * looking at. Here nothing is passed through; you pick the order you meant,
 * and the quarry is a text field you can leave alone.
 */
public class ShadeCommandScreen extends Screen {
    private static final int PANEL_W = 268;
    private static final int ROW_H = 52;
    private static final int BUTTON_W = 64;
    private static final int BUTTON_GAP = 68;

    /** Kept across rebuilds so a refresh does not eat what you are typing. */
    private final Map<Integer, String> typed = new HashMap<>();

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
        if (this.minecraft != null) {
            this.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        List<ShadeStatePayload.Entry> shades = this.state.shades();
        this.left = (this.width - PANEL_W) / 2;
        this.top = Math.max(34, (this.height - Math.max(1, shades.size()) * ROW_H) / 2);

        DragonMinions.Order[] orders = DragonMinions.Order.values();
        for (int i = 0; i < shades.size(); i++) {
            ShadeStatePayload.Entry shade = shades.get(i);
            int y = this.top + i * ROW_H;

            for (int o = 0; o < orders.length; o++) {
                DragonMinions.Order order = orders[o];
                boolean current = shade.order() == o;
                Button button = Button.builder(
                                Component.literal(order.label).withStyle(
                                        current ? order.colour : ChatFormatting.GRAY),
                                b -> send(shade.slot(), order.ordinal()))
                        .bounds(this.left + o * BUTTON_GAP, y + 11, BUTTON_W, 20)
                        .build();
                button.active = !current;      // the standing order is not a choice
                this.addRenderableWidget(button);
            }

            EditBox quarry = new EditBox(this.font, this.left, y + 34, PANEL_W, 16,
                    Component.literal("quarry"));
            quarry.setMaxLength(96);
            quarry.setHint(Component.literal("block to dig for — blank uses whatever you look at"));
            quarry.setValue(this.typed.getOrDefault(shade.slot(), shade.quarry()));
            quarry.setResponder(value -> this.typed.put(shade.slot(), value));
            this.addRenderableWidget(quarry);
        }
    }

    private void send(int slot, int order) {
        ClientPlayNetworking.send(new ShadeOrderPayload(
                slot, order, this.typed.getOrDefault(slot, "")));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFE079FA);

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
            if (shade.cargo() > 0) {
                graphics.drawString(this.font,
                        Component.literal("carrying " + shade.cargo()),
                        this.left + PANEL_W - this.font.width("carrying " + shade.cargo()),
                        y, 0xFF7DBF7D, true);
            }
        }
    }

    /** Standing in a menu should not stop the world while a fight is on. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
