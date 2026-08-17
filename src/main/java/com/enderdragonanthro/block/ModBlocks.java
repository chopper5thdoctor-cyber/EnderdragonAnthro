package com.enderdragonanthro.block;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import static com.enderdragonanthro.EnderdragonAnthro.id;

public final class ModBlocks {
    public static final DragonFireBlock DRAGON_FIRE = Registry.register(
            BuiltInRegistries.BLOCK, id("dragon_fire"),
            new DragonFireBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    // No randomTicks: spreading and burning out are both on a
                    // scheduled tick now, and BlockBehaviour's randomTick just
                    // forwards to tick(), so leaving it on ran the whole pass
                    // twice on two different clocks.
                    .lightLevel(state -> 15)
                    .sound(SoundType.WOOL)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()));

    /**
     * Vanilla gives neither fire nor soul fire an item, so both are
     * command-only. This one gets one, because a fire you cannot place is a
     * fire you cannot build with — and it is the whole point of asking for a
     * third type rather than just a texture.
     */
    public static final Item DRAGON_FIRE_ITEM = Registry.register(
            BuiltInRegistries.ITEM, id("dragon_fire"),
            new BlockItem(DRAGON_FIRE, new Item.Properties()));

    private ModBlocks() {
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.NATURAL_BLOCKS)
                .register(entries -> entries.accept(DRAGON_FIRE_ITEM));
    }
}
