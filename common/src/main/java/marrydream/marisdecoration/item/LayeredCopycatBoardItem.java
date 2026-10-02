package marrydream.marisdecoration.item;

import marrydream.marisdecoration.block.LayeredCopycatBoardBlock;
import marrydream.marisdecoration.block.LintelThresholdThinDoorBlock;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * 分层伪装薄板的物品形态。
 *
 * <p>之所以需要自己一个 {@link BlockItem}：玩家可能<b>不是</b>点在薄板上，而是点在旁边的普通方块上，
 * 而那个面外推一格正好落进一块已有的薄板里（典型的「贴着已铺好的板再补一个方向」）。
 * 这时 {@code LayeredCopycatBoardBlock#onUse} 根本不会执行——被点的是石头，走的是石头的
 * {@code onUse}——所以「往已有薄板里追加槽位」这件事必须在物品这条入口上再问一次。
 *
 * <p>两边共用同一个 {@link LayeredCopycatBoardBlock#tryAppendExisting}：由 {@code resolveTarget}
 * 决定「放哪里」，再由目标格当前是什么决定「怎么执行」。这里只做分流，不重复任何几何或槽位逻辑。
 */
public class LayeredCopycatBoardItem extends BlockItem {

    public LayeredCopycatBoardItem( Block block, Properties settings ) {
        super( block, settings );
    }

    @Override
    public InteractionResult useOn( UseOnContext context ) {
        InteractionResult insertedRoof = LintelThresholdThinDoorBlock.tryInsertRoof( context );
        if ( insertedRoof != null ) {
            return insertedRoof;
        }
        // 目标格已经是薄板 → 直接改那个方块实体的 occupancy。
        // 绝不能落到 super：方块状态只有 WATERLOGGED，setBlockState 会返回 false，整次放置会失败。
        InteractionResult appended = LayeredCopycatBoardBlock.tryAppendExisting(
                context.getLevel(), context.getClickLocation(), context.getClickedFace(),
                context.getItemInHand(), context.getPlayer() );
        return appended != null ? appended : super.useOn( context );
    }
}
