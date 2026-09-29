package marrydream.marisdecoration.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import marrydream.marisdecoration.block.utils.CopycatLadderInteraction;
import marrydream.marisdecoration.block.utils.CopycatLadderParts;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class CopycatSteelFixedLadderBlock extends LadderBlock implements BlockEntityProvider, IWrenchable {
    public static final String ID_PATH = "copycat_steel_fixed_ladder";

    public CopycatSteelFixedLadderBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CopycatLadderBlockEntity(pos, state);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        CopycatLadderInteraction.applyPlacedMaterial(world, pos, placer, CopycatLadderParts.MATERIAL);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        return CopycatLadderInteraction.use(state, world, pos, player, hand, hit, CopycatLadderParts.MATERIAL);
    }

    @Override
    public ActionResult onWrenched(BlockState state, ItemUsageContext context) {
        return CopycatLadderInteraction.wrench(context, CopycatLadderParts.MATERIAL);
    }

    @Override
    public ActionResult onSneakWrenched(BlockState state, ItemUsageContext context) {
        CopycatLadderInteraction.settleForSneakWrench(context, CopycatLadderParts.fixedSlots());
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        CopycatLadderInteraction.onBreak(world, pos, player);
        super.onBreak(world, pos, state, player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        CopycatLadderInteraction.onStateReplaced(state, world, pos, next, moved);
        super.onStateReplaced(state, world, pos, next, moved);
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        if (world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be && be.hasMaterial(CopycatLadderParts.MATERIAL)) {
            return new ItemStack(be.material(CopycatLadderParts.MATERIAL).getBlock());
        }
        return super.getPickStack(world, pos, state);
    }
}
