package com.enderdragonanthro.mixin;

import com.enderdragonanthro.client.DragonHud;
import com.enderdragonanthro.client.PickupButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hang the pick-up switch off the inventory.
 *
 * Extending the target's own superclass rather than shadowing into it, which is
 * the ordinary way to reach protected members from a mixin: leftPos, topPos,
 * imageWidth and addRenderableWidget are all inherited, and this makes them
 * inherited here too.
 *
 * The position is refreshed every frame rather than fixed at init, and that is
 * not laziness. Opening the recipe book slides the whole panel sideways without
 * re-running init, so a button placed once would detach from the thing it
 * belongs to the first time anybody opened it.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryPickupMixin extends AbstractContainerScreen<InventoryMenu> {
    @Unique
    private PickupButton enderdragonanthro$toggle;

    private InventoryPickupMixin(InventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void enderdragonanthro$addToggle(CallbackInfo ci) {
        this.enderdragonanthro$toggle = addRenderableWidget(
                new PickupButton(this.leftPos + this.imageWidth + 2, this.topPos + 4));
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void enderdragonanthro$placeToggle(GuiGraphics graphics, int mouseX, int mouseY,
                                               float delta, CallbackInfo ci) {
        PickupButton button = this.enderdragonanthro$toggle;
        if (button == null) {
            return;
        }
        // Only a dragon has the problem this solves: a person can walk around
        // an item they do not want, and something eight blocks across cannot.
        Minecraft client = Minecraft.getInstance();
        button.visible = client.player != null && DragonHud.isDragonForm(client.player);
        button.setX(this.leftPos + this.imageWidth + 2);
        button.setY(this.topPos + 4);
        button.retitle();
    }
}
