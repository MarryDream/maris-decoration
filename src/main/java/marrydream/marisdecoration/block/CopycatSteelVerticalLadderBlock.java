package marrydream.marisdecoration.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import marrydream.marisdecoration.block.utils.CopycatLadderInteraction;
import marrydream.marisdecoration.block.utils.CopycatLadderParts;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class CopycatSteelVerticalLadderBlock extends VerticalLadderBlock implements BlockEntityProvider, IWrenchable {
    public static final String ID_PATH = "copycat_steel_vertical_ladder";

    public CopycatSteelVerticalLadderBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CopycatLadderBlockEntity(pos, state);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        CopycatLadderInteraction.applyPlacedMaterial(world, pos, placer, CopycatLadderParts.RUNG);
    }

    public static String slotAt(BlockState state, BlockPos pos, Vec3d hit) {
        return CopycatLadderParts.verticalSlotAt(state, hit.subtract(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        return CopycatLadderInteraction.use(state, world, pos, player, hand, hit, slotAt(state, pos, hit.getPos()));
    }

    @Override
    public ActionResult onWrenched(BlockState state, ItemUsageContext context) {
        return CopycatLadderInteraction.wrench(context, slotAt(state, context.getBlockPos(), context.getHitPos()));
    }

    @Override
    public ActionResult onSneakWrenched(BlockState state, ItemUsageContext context) {
        CopycatLadderInteraction.settleForSneakWrench(context, CopycatLadderParts.verticalSlots());
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
        if (world.getBlockEntity(pos) instanceof CopycatLadderBlockEntity be) {
            for (String slot : CopycatLadderParts.verticalSlots()) {
                if (be.hasMaterial(slot)) return new ItemStack(be.material(slot).getBlock());
            }
        }
        return super.getPickStack(world, pos, state);
    }
}
