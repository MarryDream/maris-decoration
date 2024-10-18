package marrydream.marisdecoration.block;

import marrydream.marisdecoration.block.enums.PropHalf;
import marrydream.marisdecoration.block.utils.BlockShape;
import marrydream.marisdecoration.block.utils.DirectionConnectBlockGroup;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;

// 带修边的房顶
public class TrimRoofBlock extends DirectionShapeHalfConnectBlock {
    // 不论哪个方向，碰撞体积都是一样的，仅材质不同
    protected static final BlockShape BLOCK_SHAPE_TOP = new BlockShape( 0.0, 15.0, 0.0, 16.0, 16.0, 16.0, -1 );
    protected static final BlockShape BLOCK_SHAPE_BOTTOM = new BlockShape( 0.0, 0.0, 0.0, 16.0, 1.0, 16.0, -1 );

    public TrimRoofBlock( BlockState baseBlockState, Settings settings ) {
        super( baseBlockState, settings, null, null, false );
    }

    @Override
    protected boolean isOneWayBlock() {
        return false;
    }

    // 永远都只返回这一个，不做处理了
    @Override
    protected BlockShape getShapeByState( BlockState state ) {
        return state.get( HALF ) == PropHalf.BOTTOM ? BLOCK_SHAPE_BOTTOM : BLOCK_SHAPE_TOP;
    }

    @Override
    protected boolean isDirectionShapeHalfConnectBlock( BlockState neighborState, BlockState curState ) {
        Block neighborBlock = neighborState.getBlock();
        if ( neighborBlock instanceof TrimRoofBlock ) return true;

        // 允许 top 时，纹理变化与 half 为 top 的墙方块联动
        if ( curState.get( HALF ) == PropHalf.BOTTOM ) return false;

        if ( neighborBlock instanceof WallBlock && neighborState.get( WallBlock.HALF ) == PropHalf.TOP ) {
            return true;
        }
        return neighborBlock instanceof ComponentWallBlock && neighborState.get( ComponentWallBlock.HALF ) == PropHalf.TOP;
    }
}
