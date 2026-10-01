package marrydream.marisdecoration.client;

import marrydream.marisdecoration.block.LayeredCopycatBoardBlockEntity.RenderData;
import marrydream.marisdecoration.block.SteelPlugDoorBlockEntity;
import marrydream.marisdecoration.block.utils.SteelPlugDoorAnimation;
import marrydream.marisdecoration.block.utils.SteelPlugDoorRoof;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** Base steel door model with an optional Layered Board roof appended to its upper half. */
public final class SteelPlugDoorModel extends LayeredCopycatBoardModel {

    public SteelPlugDoorModel(BakedModel wrapped) {
        super(wrapped);
    }

    @Override
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos,
                               Supplier<Random> randomSupplier, RenderContext context) {
        boolean upper = state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
        RenderData data = upper ? readRenderData(blockView, pos) : null;
        boolean hasRoof = data != null
                && data.occupancy() == SteelPlugDoorBlockEntity.ROOF_OCCUPANCY;

        Box frameBox = SteelPlugDoorAnimation.fixedFrameBox(
                state.get(DoorBlock.FACING), state.get(DoorBlock.HALF));
        DoubleBlockHalf half = state.get(DoorBlock.HALF);
        context.pushTransform(quad -> isFixedFrameQuad(quad, frameBox)
                && !SteelPlugDoorAnimation.isInnerFrameCap(half,
                quad.y(0), quad.y(1), quad.y(2), quad.y(3))
                && (!hasRoof || !SteelPlugDoorRoof.isCoveredDoorQuad(
                quad.y(0), quad.y(1), quad.y(2), quad.y(3))));
        wrapped.emitBlockQuads(blockView, state, pos, randomSupplier, context);
        context.popTransform();
        if (!hasRoof) return;

        Direction facing = state.get(DoorBlock.FACING);
        emitLayeredQuads(data, blockView, pos, randomSupplier, context,
                box -> SteelPlugDoorRoof.toWorld(box, facing));
    }

    private static boolean isFixedFrameQuad(QuadView quad, Box frameBox) {
        for (int vertex = 0; vertex < 4; vertex++) {
            if (!SteelPlugDoorAnimation.contains(frameBox,
                    quad.x(vertex), quad.y(vertex), quad.z(vertex))) return false;
        }
        return true;
    }

    @Override
    public Sprite getParticleIcon(@Nullable Object data) {
        if (data instanceof RenderData renderData
                && renderData.occupancy() == SteelPlugDoorBlockEntity.ROOF_OCCUPANCY) {
            return super.getParticleIcon(data);
        }
        return wrapped.getParticleSprite();
    }
}
