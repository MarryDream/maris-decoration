package marrydream.marisdecoration.item;

import marrydream.marisdecoration.block.CopycatGuardrailBlock;
import marrydream.marisdecoration.block.CopycatGuardrailBlockEntity;
import marrydream.marisdecoration.block.LayeredCopycatBoardBlock;
import marrydream.marisdecoration.block.LintelThresholdThinDoorBlock;
import marrydream.marisdecoration.block.utils.GuardrailParts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * 细工凿
 *
 * <p>本 mod 通用的「构件形态切换工具」：右键切换方块构件的形态（连接形状、边缘纹理等）。
 * 后续新增的构件形态切换一律挂在这个工具上，不要再为单个构件另起一个工具。
 *
 * <p><b>为什么形态切换要写在 {@link #useOn} 而不是方块的 {@code onUse} 里</b>：
 * 原版的交互管理器在「潜行 + 手持非空物品」时会走
 * {@code shouldCancelInteraction() && 手不空} 这个分支，<b>整段跳过</b>
 * {@code blockState.onUse}，只调用 {@code ItemStack.useOnBlock}。所以放在 {@code onUse} 里的逻辑
 * 平时能用、一潜行就失灵。{@code useOnBlock} 是两条路径的交汇点：不潜行时 {@code onUse} 返回
 * PASS 之后会落到它，潜行时则直接由它接手。
 */
public class DetailChisel extends Item {
    public final static String ID = "detail_chisel";

    private static Properties getSetting( ) {
        return new Properties().stacksTo( 1 );
    }

    public DetailChisel( ) {
        super( DetailChisel.getSetting() );
    }

    /**
     * 对伪装构件做形态切换。
     *
     * <p>伪装护栏：普通右键移除<b>实际点到的那一根</b>柱子（柱子只是藏起来，材质留着）；
     * 潜行右键把本方块内所有护栏的柱子恢复成默认状态。
     *
     * <p>分层伪装薄板：普通右键 Body / Window 区域切换该面的圆窗、点角切换归属的边、
     * 点边不处理；潜行右键把所有面的圆窗关掉。
     *
     * <p>两者都只动「可见性 / 形态」——伪装材质、被消耗物品记录一概不碰，也不返还任何物品。
     */
    @Override
    public InteractionResult useOn( UseOnContext context ) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if ( world.getBlockState( pos ).getBlock() instanceof LayeredCopycatBoardBlock ) {
            // 薄板的形态切换实现在方块类里，两边共用同一份逻辑（onUse 与 useOnBlock 都要挂，
            // 否则潜行时原版会跳过 BlockState#onUse，Shift + 右键收不到）。
            return LayeredCopycatBoardBlock.onChisel( world, pos, context.getPlayer(),
                    context.getClickLocation(), context.getClickedFace() );
        }
        if ( world.getBlockState( pos ).getBlock() instanceof LintelThresholdThinDoorBlock ) {
            return LintelThresholdThinDoorBlock.onRoofChisel( context );
        }
        if ( !( world.getBlockState( pos ).getBlock() instanceof CopycatGuardrailBlock ) ) {
            return InteractionResult.PASS;
        }
        if ( !( world.getBlockEntity( pos ) instanceof CopycatGuardrailBlockEntity blockEntity ) ) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if ( player != null && player.isShiftKeyDown() ) {
            return restoreAllColumns( world, pos, blockEntity );
        }
        return hideClickedColumn( context, world, pos, blockEntity );
    }

    /**
     * 潜行右键：恢复本方块内所有柱子。
     *
     * <p>不需要精确点到柱子——柱子藏起来之后本来就点不到，这里也不看命中部位，
     * 打在方块任意有效位置都算。没有隐藏项时不做任何操作。
     */
    private static InteractionResult restoreAllColumns( Level world, BlockPos pos, CopycatGuardrailBlockEntity blockEntity ) {
        if ( !blockEntity.hasHiddenColumns() ) {
            return InteractionResult.PASS;
        }
        if ( !world.isClientSide ) {
            blockEntity.showAllColumns();
            world.playSound( null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 0.7F, 1.4F );
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 普通右键：隐藏点中的那一根柱子。
     *
     * <p>点中哪根柱子由实际命中点的几何最近部件决定（{@link GuardrailParts#partAt}），
     * <b>不</b>按玩家朝向猜：柱子只有 1/16 宽，一个方块里最多四根，朝向根本区分不开。
     * 点在横梁上时什么都不做，把这次交互让回去。
     */
    private static InteractionResult hideClickedColumn( UseOnContext context, Level world, BlockPos pos,
                                                   CopycatGuardrailBlockEntity blockEntity ) {
        BlockState state = world.getBlockState( pos );
        Vec3 localHit = context.getClickLocation().subtract( pos.getX(), pos.getY(), pos.getZ() );
        GuardrailParts.Hit part = GuardrailParts.partAt( state, blockEntity.hiddenColumns(), localHit );
        if ( part == null || part.columnKey() == null ) {
            return InteractionResult.PASS;
        }
        if ( !world.isClientSide ) {
            blockEntity.hideColumn( part.columnKey() );
            world.playSound( null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 0.7F, 0.7F );
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText( ItemStack stack, Level world, List<Component> tooltip, TooltipFlag context ) {
        tooltip.add( Component.translatable( "item.maris-decoration.detail_chisel.tooltip" ) );
        tooltip.add( Component.translatable( "item.maris-decoration.detail_chisel.remark.tooltip" ).withStyle( ChatFormatting.BLUE ) );
    }
}
