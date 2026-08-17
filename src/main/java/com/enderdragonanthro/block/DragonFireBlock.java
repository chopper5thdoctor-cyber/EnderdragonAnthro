package com.enderdragonanthro.block;

import com.mojang.serialization.MapCodec;
import com.enderdragonanthro.ability.DragonFire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Map;

/**
 * A third fire, after the orange one and the blue one.
 *
 * It burns hotter than either: 3 a tick against fire's 1 and soul fire's 2.
 * Neither of those was breathed by anything.
 *
 * It spreads, and it goes out. Both are on a scheduled tick rather than a
 * random one, which is how vanilla fire is paced and why vanilla fire feels
 * alive: a random tick reaches a given block about once a minute, so a fire
 * driven by one creeps in slow motion and ages out over a quarter of an hour.
 */
public class DragonFireBlock extends BaseFireBlock {
    public static final MapCodec<DragonFireBlock> CODEC = simpleCodec(DragonFireBlock::new);

    /** How far through burning out this flame is. Purely a clock. */
    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;

    /**
     * Which neighbours this flame is clinging to, when it is clinging rather
     * than standing.
     *
     * This is the whole of why vanilla fire lies flat against leaves and ours
     * did not. Fire is not one model. The blockstate is a multipart switched on
     * these five booleans: with all of them false it draws the bonfire — a pair
     * of crossed sheets standing in the middle of the block — and with any of
     * them true it drops the bonfire and draws a flat panel pressed against
     * each face that is burning.
     *
     * They are only set when there is nothing underneath to stand on. Fire on a
     * floor is a bonfire even if a tree is beside it; fire in a canopy has no
     * floor, so it becomes panels stuck to the leaves. Ours applied the floor
     * model unconditionally, which is why burning a treetop left whole cubes of
     * flame hanging between the leaves.
     */
    private static final Map<Direction, BooleanProperty> ATTACHED = Map.of(
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.EAST, BlockStateProperties.EAST,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.UP, BlockStateProperties.UP);

    /**
     * Per tick, standing in it. Fire does 1 and soul fire 2; this does 3,
     * because it is not a hazard the world grew, it is a dragon's.
     */
    private static final float FIRE_DAMAGE = 3.0F;

    /** How long standing in it keeps the burn marked as ours. */
    private static final int MARK_TICKS = 300;
    /** Chance per tick that a flammable neighbour is burnt away outright. */
    private static final float CONSUME_CHANCE = 0.2F;
    /** ...and that the flame reaches past it into open air instead. */
    private static final float CATCH_CHANCE = 0.3F;
    /** Ticks between one pass and the next, plus a little scatter. */
    private static final int TICK_INTERVAL = 30;
    /** Passes a flame lasts once nothing near it will burn. */
    private static final int MAX_AGE = 15;

    public DragonFireBlock(Properties properties) {
        super(properties, FIRE_DAMAGE);
        BlockState state = getStateDefinition().any().setValue(AGE, 0);
        for (BooleanProperty attached : ATTACHED.values()) {
            state = state.setValue(attached, false);
        }
        registerDefaultState(state);
    }

    @Override
    public MapCodec<? extends BaseFireBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(AGE);
        ATTACHED.values().forEach(builder::add);
    }

    /**
     * Our fire, from our item.
     *
     * BaseFireBlock's version hands back Blocks.FIRE or Blocks.SOUL_FIRE
     * depending on what is underneath — it is written for the two vanilla
     * fires and picks between them. Inherited unchanged, the dragonfire item
     * placed ordinary orange fire, which is a strange thing for it to do.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return shaped(context.getLevel(), context.getClickedPos());
    }

    /**
     * The flame this spot should hold: a bonfire if it has a floor, panels on
     * the burning faces if it does not.
     *
     * Vanilla's rule, and it is the block below that decides — not whether
     * anything nearby burns. Fire beside a tree but on solid ground is still a
     * bonfire; only fire with nothing under it goes flat.
     */
    public BlockState shaped(BlockGetter level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        if (flammable(under) || under.isFaceSturdy(level, below, Direction.UP)) {
            return defaultBlockState();
        }
        BlockState state = defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> face : ATTACHED.entrySet()) {
            state = state.setValue(face.getValue(),
                    flammable(level.getBlockState(pos.relative(face.getKey()))));
        }
        return state;
    }

    /**
     * Something to stand on, or something to eat.
     *
     * This is why flint and steel could light leaves and a dragon could not.
     * Requiring a sturdy face sounds like the loose version of vanilla's rule
     * and is in fact the strict one, because LeavesBlock overrides
     * getBlockSupportShape to return nothing — leaves hold up no torch, no
     * fire, nothing. Every flame breathed into a canopy failed its own
     * survival check and popped in the same tick.
     *
     * Vanilla fire does not ask for support on the sides at all. It asks for a
     * sturdy floor OR a neighbour that burns, and it is the second clause that
     * puts fire in a tree: fire lives in leaves because leaves are fuel, not
     * because they are solid.
     */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return anchored(level, pos) || fuelled(level, pos);
    }

    /** Whether anything next to this spot will burn. */
    private static boolean fuelled(LevelReader level, BlockPos pos) {
        for (Direction face : Direction.values()) {
            if (flammable(level.getBlockState(pos.relative(face)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ask again whenever a neighbour changes.
     *
     * This is why a burnt tree left a tree-shaped column of flame hanging in
     * the air. canSurvive was right and was simply never consulted a second
     * time: BaseFireBlock has no updateShape at all, so a flame checked its
     * footing once, when it was placed, and then kept burning no matter what
     * happened around it. Every log the fire ate became another flame with
     * nothing under it, and the trunk stayed lit after the tree was gone.
     *
     * Vanilla's two fires both override this. Soul fire is the one to copy
     * here; FireBlock's version carries its neighbour-tracking properties
     * along, which this fire does not have.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (!canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        // The shape is recomputed here too: a flame that was standing on a log
        // is clinging to leaves the moment the log burns away, and the clock it
        // has already run down carries over.
        return shaped(level, pos).setValue(AGE, state.getValue(AGE));
    }

    /**
     * Any solid face will hold it, not just a floor.
     *
     * Vanilla fire only accepts a floor, which is fine for something that is
     * always struck onto the ground and climbs on its own. Dragonfire is
     * breathed at whatever the dragon is looking at, so it has to be able to
     * start halfway up a wall.
     *
     * Soul fire is pickier still — soul sand or soul soil only, which is what
     * makes it a Nether feature rather than a fire.
     */
    private static boolean anchored(LevelReader level, BlockPos pos) {
        for (Direction face : Direction.values()) {
            BlockPos side = pos.relative(face);
            if (level.getBlockState(side).isFaceSturdy(level, side, face.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return true;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        level.scheduleTick(pos, this, TICK_INTERVAL + level.getRandom().nextInt(10));
    }

    /**
     * Eat what is next to it, then age.
     *
     * Only blocks the game already marks flammable are touched, so stone and
     * dirt are safe and a tree is not. Fuel next door slows the ageing rather
     * than stopping it: a flame with a forest to work through lasts a good
     * while, and one left on bare ground gives up in half a minute. Nothing is
     * permanent, which matters more here than it does for vanilla fire —
     * dragonfire is placed twenty-five blocks at a time.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, TICK_INTERVAL + random.nextInt(10));
        if (!canSurvive(state, level, pos)) {
            level.removeBlock(pos, false);
            return;
        }

        boolean fed = false;
        for (Direction face : Direction.values()) {
            BlockPos next = pos.relative(face);
            if (!flammable(level.getBlockState(next))) {
                continue;
            }
            fed = true;
            if (random.nextFloat() < CONSUME_CHANCE) {
                consume(level, next);
            } else if (random.nextFloat() < CATCH_CHANCE) {
                spread(level, next.relative(face));
            }
        }

        if (fed && random.nextInt(3) != 0) {
            return;
        }
        int age = state.getValue(AGE);
        if (age >= MAX_AGE) {
            level.removeBlock(pos, false);
        } else {
            level.setBlock(pos, state.setValue(AGE, age + 1), 4);
        }
    }

    private static boolean flammable(BlockState state) {
        return state.ignitedByLava() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    /**
     * Burn a block away, leaving flame behind only where flame can stand.
     *
     * The check matters for exactly the case that went wrong: a trunk is held
     * up by the trunk, so consuming it upward left each flame propped on the
     * log above until that went too. updateShape cleans that up after the
     * fact; not creating it in the first place is tidier.
     */
    private void consume(ServerLevel level, BlockPos at) {
        boolean holds = defaultBlockState().canSurvive(level, at);
        level.setBlockAndUpdate(at, holds ? shaped(level, at) : Blocks.AIR.defaultBlockState());
    }

    private void spread(ServerLevel level, BlockPos at) {
        if (level.getBlockState(at).canBeReplaced() && defaultBlockState().canSurvive(level, at)) {
            level.setBlockAndUpdate(at, shaped(level, at));
        }
    }

    /**
     * Standing in it counts as being lit by it.
     *
     * BaseFireBlock already does the damage; this only says whose fire it was,
     * so the flames and the screen go purple for as long as the burn lasts
     * rather than only while your feet are in the block.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level instanceof ServerLevel server && entity instanceof LivingEntity victim
                && !entity.fireImmune()) {
            // A fixed span, not getRemainingFireTicks(). BaseFireBlock adds a
            // tick at a time, so reading it back here marked the burn as ours
            // for one tick and the screen went orange again immediately.
            DragonFire.mark(server, victim, MARK_TICKS);
        }
    }
}
