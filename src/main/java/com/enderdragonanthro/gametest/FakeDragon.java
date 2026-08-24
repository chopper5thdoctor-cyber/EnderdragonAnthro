package com.enderdragonanthro.gametest;

import com.enderdragonanthro.transform.DragonFormManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.world.level.GameType;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A dragon with nobody behind it.
 *
 * Almost everything in this mod takes a ServerPlayer, and GameTest hands you a
 * world with no players in it — which is why the whole feature set has never
 * been testable and why every bug in it has had to be found by playing.
 *
 * A ServerPlayer is constructible on its own; what it does not have is a
 * connection, and the two places that matters are both messages. Overriding
 * them costs two methods and buys something better than silence: the messages
 * are KEPT, so a test can assert on what the dragon was told, which is often
 * the only observable an ability has.
 *
 * <h2>What this cannot do</h2>
 *
 * Anything that sends a packet still needs a real connection —
 * ServerPlayNetworking.send reads player.connection directly, so it cannot be
 * overridden away. That rules out Dragonsight's sweep, the court screen and the
 * pick-up switch, and those stay the client harness's job. Everything that
 * changes the world rather than the screen is reachable from here.
 */
public class FakeDragon extends ServerPlayer {
    private final List<String> told = new ArrayList<>();

    private FakeDragon(ServerLevel level) {
        super(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "TestDragon"),
                ClientInformation.createDefault());
        // Sets this.connection as a side effect, which is the whole point.
        new ServerGamePacketListenerImpl(level.getServer(),
                new Connection(PacketFlow.CLIENTBOUND), this,
                CommonListenerCookie.createInitial(this.getGameProfile(), false));
    }

    /**
     * Vanilla's own no-op filter.
     *
     * MinecraftServer.createTextFilterForPlayer returns null unless a server is
     * configured with one, and the packet listener's constructor calls join()
     * on it without checking.
     */
    @Override
    public TextFilter getTextFilter() {
        return TextFilter.DUMMY;
    }

    /**
     * A transformed dragon standing at a position relative to the test.
     *
     * Added to the level so mob AI can find it: DragonPresence searches
     * level.players(), which is the only reason the fear goal can see anything.
     */
    public static FakeDragon transformed(GameTestHelper helper, BlockPos where) {
        ServerLevel level = helper.getLevel();
        FakeDragon dragon = new FakeDragon(level);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(where));
        dragon.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        // The gametest server hands out its own default game mode, and if that
        // is creative then abilities.invulnerable is set and every hurt() in
        // every test silently returns false -- which reads exactly like the
        // damage code being broken. Survival, explicitly.
        dragon.setGameMode(GameType.SURVIVAL);
        // Only doTick() counts this down, and a hand-made player is never
        // ticked, so without this every hurt() in every test answers false.
        ((com.enderdragonanthro.mixin.ServerPlayerSpawnGraceAccessor) dragon)
                .enderdragonanthro$setSpawnGrace(0);
        dragon.getAbilities().invulnerable = false;
        dragon.setInvulnerable(false);
        level.addFreshEntity(dragon);
        DragonFormManager.applyForm(dragon);
        return dragon;
    }

    /** Everything displayClientMessage would have sent, in order. */
    public List<String> told() {
        return this.told;
    }

    public boolean wasTold(String fragment) {
        return this.told.stream().anyMatch(line -> line.contains(fragment));
    }

    @Override
    public void displayClientMessage(Component message, boolean actionBar) {
        this.told.add(message.getString());
    }

    @Override
    public void sendSystemMessage(Component message) {
        this.told.add(message.getString());
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public boolean isSpectator() {
        return false;
    }
}
