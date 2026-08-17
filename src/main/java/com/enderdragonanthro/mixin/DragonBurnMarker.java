package com.enderdragonanthro.mixin;

import net.minecraft.world.entity.Entity;

/**
 * Which entity renderFlame is currently drawing.
 *
 * renderFlame takes the entity but the sprite lookup inside it does not, and a
 * redirect only ever sees the call it replaced. Rather than reach for a local
 * capture — brittle against any remap — the dispatcher notes the entity on the
 * way in and clears it on the way out. Render is single-threaded, so a static
 * is honest here in a way it would not be anywhere else.
 */
public final class DragonBurnMarker {
    static Entity current;

    private DragonBurnMarker() {
    }
}
