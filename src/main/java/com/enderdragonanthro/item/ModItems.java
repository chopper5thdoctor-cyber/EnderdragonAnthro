package com.enderdragonanthro.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import static com.enderdragonanthro.EnderdragonAnthro.id;

public final class ModItems {
    public static final Item HOMING_CRYSTAL = Registry.register(
            BuiltInRegistries.ITEM, id("homing_crystal"),
            new HomingCrystalItem(new Item.Properties().stacksTo(1).rarity(
                    net.minecraft.world.item.Rarity.RARE)));

    private ModItems() {
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(HOMING_CRYSTAL));
    }
}
