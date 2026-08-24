package com.enderdragonanthro.client;

import com.enderdragonanthro.EnderdragonAnthro;
import com.enderdragonanthro.network.DragonPickupPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A small switch beside the inventory: stoop, or leave it where it fell.
 *
 * It sits on the panel's right edge rather than inside it, because every square
 * inside is already a slot and a button over one would be a button you cannot
 * click without dropping what you are holding.
 */
public class PickupButton extends Button {
    private static final ResourceLocation ON =
            EnderdragonAnthro.id("textures/gui/pickup_on.png");
    private static final ResourceLocation OFF =
            EnderdragonAnthro.id("textures/gui/pickup_off.png");
    private static final int ICON = 16;
    public static final int SIZE = 20;

    public PickupButton(int x, int y) {
        super(x, y, SIZE, SIZE, CommonComponents.EMPTY,
                b -> ClientPlayNetworking.send(
                        new DragonPickupPayload(!DragonPickupClient.picksUp())),
                DEFAULT_NARRATION);
        retitle();
    }

    /** Keep the hover text honest about what the click will do. */
    public void retitle() {
        boolean on = DragonPickupClient.picksUp();
        setTooltip(Tooltip.create(Component.literal(
                        on ? "Picking things up" : "Leaving things where they fall")
                .withStyle(on ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY)
                .append(Component.literal(on
                                ? "\nClick to walk over items without taking them."
                                : "\nClick to collect items again.")
                        .withStyle(ChatFormatting.DARK_GRAY))));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderWidget(graphics, mouseX, mouseY, delta);
        ResourceLocation icon = DragonPickupClient.picksUp() ? ON : OFF;
        graphics.blit(icon, getX() + (SIZE - ICON) / 2, getY() + (SIZE - ICON) / 2,
                0, 0.0F, 0.0F, ICON, ICON, ICON, ICON);
    }
}
