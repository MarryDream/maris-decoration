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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class CopycatSteelFixedLadderBlock extends LadderBlock implements EntityBlock, IWrenchable {
    public static final String ID_PATH = "copycat_steel_fixed_ladder";

    public CopycatSteelFixedLadderBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CopycatLadderBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(world, pos, state, placer, itemStack);
        CopycatLadderInteraction.applyPlacedMaterial(world, pos, placer, CopycatLadderParts.MATERIAL);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return CopycatLadderInteraction.use(state, world, pos, player, hand, hit, CopycatLadderParts.MATERIAL);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return CopycatLadderInteraction.wrench(context, CopycatLadderParts.MATERIAL);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        CopycatLadderInteraction.settleForSneakWrench(context, CopycatLadderParts.fixedSlots());
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
        if (world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be && be.hasMaterial(CopycatLadderParts.MATERIAL)) {
            return new ItemStack(be.material(CopycatLadderParts.MATERIAL).getBlock());
        }
        return super.getCloneItemStack(world, pos, state);
    }
}
