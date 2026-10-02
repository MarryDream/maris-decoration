package marrydream.marisdecoration.client;

import marrydream.marisdecoration.block.LayeredCopycatBoardBlockEntity.RenderData;
import marrydream.marisdecoration.block.SteelPlugDoorBlockEntity;
import marrydream.marisdecoration.block.utils.SteelPlugDoorAnimation;
import marrydream.marisdecoration.block.utils.SteelPlugDoorRoof;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** Base steel door model with an optional Layered Board roof appended to its upper half. */
public final class SteelPlugDoorModel extends LayeredCopycatBoardModel {

    public SteelPlugDoorModel(BakedModel wrapped) {
        super(wrapped);
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos,
                               Supplier<RandomSource> randomSupplier, RenderContext context) {
        boolean upper = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
        RenderData data = upper ? readRenderData(blockView, pos) : null;
        boolean hasRoof = data != null
                && data.occupancy() == SteelPlugDoorBlockEntity.ROOF_OCCUPANCY;

        AABB frameBox = SteelPlugDoorAnimation.fixedFrameBox(
                state.getValue(DoorBlock.FACING), state.getValue(DoorBlock.HALF));
        DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
        context.pushTransform(quad -> isFixedFrameQuad(quad, frameBox)
                && !SteelPlugDoorAnimation.isInnerFrameCap(half,
                quad.y(0), quad.y(1), quad.y(2), quad.y(3))
                && (!hasRoof || !SteelPlugDoorRoof.isCoveredDoorQuad(
                quad.y(0), quad.y(1), quad.y(2), quad.y(3))));
        wrapped.emitBlockQuads(blockView, state, pos, randomSupplier, context);
        context.popTransform();
        if (!hasRoof) return;

        Direction facing = state.getValue(DoorBlock.FACING);
        emitLayeredQuads(data, blockView, pos, randomSupplier, context,
                box -> SteelPlugDoorRoof.toWorld(box, facing));
    }

    private static boolean isFixedFrameQuad(QuadView quad, AABB frameBox) {
        for (int vertex = 0; vertex < 4; vertex++) {
            if (!SteelPlugDoorAnimation.contains(frameBox,
                    quad.x(vertex), quad.y(vertex), quad.z(vertex))) return false;
        }
        return true;
    }

    @Override
    public TextureAtlasSprite getParticleIcon(@Nullable Object data) {
        if (data instanceof RenderData renderData
                && renderData.occupancy() == SteelPlugDoorBlockEntity.ROOF_OCCUPANCY) {
            return super.getParticleIcon(data);
        }
        return wrapped.getParticleIcon();
    }
}
