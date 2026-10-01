package marrydream.marisdecoration.client;

import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import marrydream.marisdecoration.block.SteelPlugDoorBlockEntity;
import marrydream.marisdecoration.block.utils.SteelPlugDoorAnimation;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/** Renders both moving leaf halves from the canonical upper-half block entity. */
public final class SteelPlugDoorRenderer extends SafeBlockEntityRenderer<SteelPlugDoorBlockEntity> {

    public SteelPlugDoorRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    protected void renderSafe(SteelPlugDoorBlockEntity blockEntity, float partialTicks,
                              MatrixStack matrices, VertexConsumerProvider buffers,
                              int light, int overlay) {
        BlockState state = blockEntity.getCachedState();
        Direction facing = state.get(DoorBlock.FACING);
        DoorHinge hinge = state.get(DoorBlock.HINGE);
        float progress = blockEntity.doorAnimation(partialTicks);
        float swing = SteelPlugDoorAnimation.swingAngle(hinge, progress);
        var pivot = SteelPlugDoorAnimation.hingePivot(hinge);

        matrices.push();
        matrices.translate(0.5, 0.0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
                SteelPlugDoorAnimation.facingAngle(facing)));
        matrices.translate(-0.5, 0.0, -0.5);

        VertexConsumer consumer = buffers.getBuffer(RenderLayer.getSolid());
        boolean left = hinge == DoorHinge.LEFT;
        int lowerLight = blockEntity.getWorld() == null ? light
                : WorldRenderer.getLightmapCoordinates(blockEntity.getWorld(), blockEntity.getPos().down());

        if (SteelPlugDoorAnimation.shouldRenderUpperFrameCap(progress, blockEntity.hasRoof())) {
            render(left ? SteelPlugDoorPartialModels.LINTEL_BOTTOM_LEFT
                            : SteelPlugDoorPartialModels.LINTEL_BOTTOM_RIGHT,
                    state, matrices, consumer, light);
        }
        if (SteelPlugDoorAnimation.shouldRenderFrameCaps(progress)) {
            matrices.push();
            matrices.translate(0.0, -1.0, 0.0);
            render(left ? SteelPlugDoorPartialModels.THRESHOLD_TOP_LEFT
                            : SteelPlugDoorPartialModels.THRESHOLD_TOP_RIGHT,
                    state, matrices, consumer, lowerLight);
            matrices.pop();
        }

        matrices.translate(pivot.x, 0.0, pivot.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(swing));
        matrices.translate(-pivot.x, 0.0, -pivot.z);

        render(left ? SteelPlugDoorPartialModels.TOP_LEFT : SteelPlugDoorPartialModels.TOP_RIGHT,
                state, matrices, consumer, light);
        if (!blockEntity.hasRoof()) {
            render(left ? SteelPlugDoorPartialModels.TOP_CAP_LEFT : SteelPlugDoorPartialModels.TOP_CAP_RIGHT,
                    state, matrices, consumer, light);
        }

        matrices.push();
        matrices.translate(0.0, -1.0, 0.0);
        render(left ? SteelPlugDoorPartialModels.BOTTOM_LEFT : SteelPlugDoorPartialModels.BOTTOM_RIGHT,
                state, matrices, consumer, lowerLight);
        matrices.pop();
        matrices.pop();
    }

    private static void render(PartialModel model, BlockState state, MatrixStack matrices,
                               VertexConsumer consumer, int light) {
        SuperByteBuffer buffer = CachedBuffers.partial(model, state);
        buffer.light(light).renderInto(matrices, consumer);
    }
}
