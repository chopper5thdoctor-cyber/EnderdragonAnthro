package com.enderdragonanthro.command;

import com.enderdragonanthro.ability.DragonMinions;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * Typing a quarry out, for when you cannot conveniently look at one —
 * {@code /shade collect deepslate_diamond_ore}. The shade being addressed
 * (the one you last picked with the select key) takes the order.
 */
public final class ShadeCommand {
    private ShadeCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("shade")
                    .requires(source -> source.hasPermission(0))
                    .then(Commands.literal("collect")
                            .then(Commands.argument("block", BlockStateArgument.block(registry))
                                    .executes(ctx -> {
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        Block block = BlockStateArgument
                                                .getBlock(ctx, "block").getState().getBlock();
                                        if (!DragonMinions.setQuarry(player, block)) {
                                            ctx.getSource().sendFailure(Component.literal(
                                                    "No shade is listening."));
                                            return 0;
                                        }
                                        return 1;
                                    })));
            dispatcher.register(root);
        });
    }

    /** Kept for the help line the mod prints on first summon. */
    public static Component usage() {
        return Component.literal("/shade collect <block>").withStyle(ChatFormatting.GRAY);
    }
}
