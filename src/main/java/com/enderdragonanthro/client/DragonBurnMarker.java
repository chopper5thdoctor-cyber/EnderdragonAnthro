package com.enderdragonanthro.client;

import net.minecraft.world.entity.Entity;

/**
 * Which entity renderFlame is currently drawing.
 *
 * renderFlame takes the entity but the sprite lookup inside it does not, and a
 * redirect only ever sees the call it replaced. So the dispatcher notes the
 * entity on the way in and clears it on the way out. Render is single-threaded,
 * so a static is honest here in a way it would not be anywhere else.
 *
 * It lives in client, NOT in mixin. Mixin owns that package outright: any class
 * under it is treated as a mixin and refuses to be referenced directly, which
 * crashed the game the first time a shade caught fire in view. Helper classes a
 * mixin calls into belong outside it.
 */
public final class DragonBurnMarker {
    public static Entity current;

    private DragonBurnMarker() {
    }
}
