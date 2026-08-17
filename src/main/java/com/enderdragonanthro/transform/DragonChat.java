package com.enderdragonanthro.transform;

import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * A dragon does not talk like a person.
 *
 * Deep purple and bold for anything said while transformed, plain the moment
 * the form drops. It goes through Fabric's message decorator rather than
 * through a mixin on the chat packet, because chat is signed: rewriting the
 * packet would either break the signature or strip it, and a decorator is the
 * seam the game leaves open for exactly this.
 *
 * STYLING_PHASE, not CONTENT_PHASE — the distinction matters. Content phase is
 * for changing what was said, which invalidates the signature and marks the
 * message as modified; styling phase runs after, on a message already signed,
 * and only dresses it. Nothing here touches a single character of what the
 * player typed.
 */
public final class DragonChat {
    /**
     * DARK_PURPLE rather than LIGHT_PURPLE, which is the tint the court speaks
     * in — the dragon should not read as one of its own retinue.
     */
    private static final ChatFormatting VOICE = ChatFormatting.DARK_PURPLE;

    private DragonChat() {
    }

    public static void register() {
        ServerMessageDecoratorEvent.EVENT.register(ServerMessageDecoratorEvent.STYLING_PHASE,
                (player, message) -> {
                    if (player == null || !DragonFormManager.isDragon(player)) {
                        return message;
                    }
                    return Component.empty().append(message)
                            .withStyle(VOICE, ChatFormatting.BOLD);
                });
    }
}
