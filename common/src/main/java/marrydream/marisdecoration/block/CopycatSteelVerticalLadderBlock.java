package marrydream.marisdecoration.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import marrydream.marisdecoration.block.utils.CopycatLadderInteraction;
import marrydream.marisdecoration.block.utils.CopycatLadderParts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class CopycatSteelVerticalLadderBlock extends VerticalLadderBlock implements EntityBlock, IWrenchable {
    public static final String ID_PATH = "copycat_steel_vertical_ladder";

    public CopycatSteelVerticalLadderBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        if (super.canSurvive(state, world, pos)) return true;

        BlockPos supportPos = pos.relative(state.getValue(FACING).getOpposite());
        BlockState support = world.getBlockState(supportPos);
        return !support.isAir() && !(support.getBlock() instanceof LiquidBlock);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CopycatLadderBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(world, pos, state, placer, itemStack);
        CopycatLadderInteraction.applyPlacedMaterial(world, pos, placer, CopycatLadderParts.RUNG);
    }

    public static String slotAt(BlockState state, BlockPos pos, Vec3 hit) {
        return CopycatLadderParts.verticalSlotAt(state, hit.subtract(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return CopycatLadderInteraction.use(state, world, pos, player, hand, hit, slotAt(state, pos, hit.getLocation()));
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return CopycatLadderInteraction.wrench(context, slotAt(state, context.getClickedPos(), context.getClickLocation()));
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        CopycatLadderInteraction.settleForSneakWrench(context, CopycatLadderParts.verticalSlots());
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        CopycatLadderInteraction.onBreak(world, pos, player);
        super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState next, boolean moved) {
        CopycatLadderInteraction.onStateReplaced(state, world, pos, next, moved);
        super.onRemove(state, world, pos, next, moved);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {
        if (world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be) {
            for (String slot : CopycatLadderParts.verticalSlots()) {
                if (be.hasMaterial(slot)) return new ItemStack(be.material(slot).getBlock());
            }
        }
        return super.getCloneItemStack(world, pos, state);
    }
}
