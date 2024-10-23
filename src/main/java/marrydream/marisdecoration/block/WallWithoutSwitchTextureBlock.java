package marrydream.marisdecoration.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;

// 不需要开关纹理的墙壁
public class WallWithoutSwitchTextureBlock extends WallBlock {
    public WallWithoutSwitchTextureBlock( BlockState baseBlockState, Settings settings ) {
        super( baseBlockState, settings, false );
    }

    @Override
    protected boolean isDirectionShapeHalfConnectBlock( BlockState neighborState, BlockState state ) {
        return neighborState.getBlock() instanceof WallWithoutSwitchTextureBlock;
    }

    @Override
    protected void appendProperties( StateManager.Builder<Block, BlockState> builder ) {
        builder.add( FACING, HALF, SHAPE, WATERLOGGED );
    }
}
