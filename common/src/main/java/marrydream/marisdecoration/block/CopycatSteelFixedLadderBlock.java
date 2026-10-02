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

    //? if <1.21 {
    @Override
    //?}
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
    //? if >=1.21 {
/*public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
*///?} else {
public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
//?}
        CopycatLadderInteraction.onBreak(world, pos, player);
        //? if >=1.21 {
/*return super.playerWillDestroy(world, pos, state, player);
*///?} else {
super.playerWillDestroy(world, pos, state, player);
//?}
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState next, boolean moved) {
        CopycatLadderInteraction.onStateReplaced(state, world, pos, next, moved);
        super.onRemove(state, world, pos, next, moved);
    }

    @Override
    //? if >=1.21 {
/*public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader world, BlockPos pos, BlockState state) {
*///?} else {
public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {
//?}
        if (world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be && be.hasMaterial(CopycatLadderParts.MATERIAL)) {
            return new ItemStack(be.material(CopycatLadderParts.MATERIAL).getBlock());
        }
        return super.getCloneItemStack(world, pos, state);
    }
    //? if >=1.21 {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return switch (use(state,world,pos,player,hand,hit)) {
            case SUCCESS -> net.minecraft.world.ItemInteractionResult.SUCCESS;
            case CONSUME -> net.minecraft.world.ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> net.minecraft.world.ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> net.minecraft.world.ItemInteractionResult.FAIL;
            case PASS, SUCCESS_NO_ITEM_USED -> net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        };
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        return use(state,world,pos,player,InteractionHand.MAIN_HAND,hit);
    }

    *///?}
}
