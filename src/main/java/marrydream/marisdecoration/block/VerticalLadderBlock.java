package marrydream.marisdecoration.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.LadderBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

public class VerticalLadderBlock extends LadderBlock {
    public VerticalLadderBlock( AbstractBlock.Settings settings ) {
        super( settings );
    }

    // 你自由了！（限制后面再说）
    @Override
    public boolean canPlaceAt( BlockState state, WorldView world, BlockPos pos ) {
        return true;
    }
}
