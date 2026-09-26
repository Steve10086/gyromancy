package com.astune.gyromancy.block;

import com.astune.gyromancy.worldgen.SilverTreeGrowth;
import com.astune.gyromancy.worldgen.SilverTreeLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * Silver tree log.
 *
 * <p>{@code is_leaf} marks a branch tip: an {@code original} silver log whose
 * six faces touch exactly one other silver log. The stored value is a mirror
 * only; the authoritative check is {@link SilverTreeLogic#isLeafLog}. Mined and
 * player-placed logs always keep {@code is_leaf} false.</p>
 *
 * <p>{@code original} marks logs that spawned with the tree. Only these
 * respond to bone meal, and placing a dropped log clears the flag.</p>
 */
public class SilverLogBlock extends RotatedPillarBlock {

    public static final BooleanProperty IS_LEAF = BooleanProperty.create("is_leaf");
    public static final BooleanProperty ORIGINAL = BooleanProperty.create("original");

    public SilverLogBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .instrument(NoteBlockInstrument.BASS)
                .strength(2.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava()
                .randomTicks());
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(IS_LEAF, false)
                .setValue(ORIGINAL, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        // Mined and player-placed logs are never branch tips.
        return state.setValue(IS_LEAF, false).setValue(ORIGINAL, false);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide) {
            syncIsLeaf(level, pos, state);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean movedByPiston) {
        if (!level.isClientSide) {
            syncIsLeaf(level, pos, state);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        syncIsLeaf(level, pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!state.getValue(ORIGINAL) || !stack.is(Items.BONE_MEAL)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel serverLevel) {
            Optional<BlockPos> grown = SilverTreeGrowth.tryGrow(serverLevel, pos, level.random);
            if (grown.isPresent()) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                BlockPos grownPos = grown.get();
                level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        grownPos.getX() + 0.5D, grownPos.getY() + 0.5D, grownPos.getZ() + 0.5D,
                        24, 1.1D, 1.1D, 1.1D, 0.0D);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, IS_LEAF, ORIGINAL);
    }

    /** Re-derives the stored tip flag and pushes it to clients when it changed. */
    private static void syncIsLeaf(Level level, BlockPos pos, BlockState state) {
        boolean leaf = state.getValue(ORIGINAL)
                && SilverTreeLogic.countAdjacentLogs(level, pos) == 1;
        if (state.getValue(IS_LEAF) != leaf) {
            level.setBlock(pos, state.setValue(IS_LEAF, leaf), Block.UPDATE_CLIENTS);
        }
    }
}
