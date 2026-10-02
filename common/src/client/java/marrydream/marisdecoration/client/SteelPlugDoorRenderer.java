package marrydream.marisdecoration.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import marrydream.marisdecoration.block.SteelPlugDoorBlockEntity;
import marrydream.marisdecoration.block.utils.SteelPlugDoorAnimation;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/** Renders both moving leaf halves from the canonical upper-half block entity. */
public final class SteelPlugDoorRenderer extends SafeBlockEntityRenderer<SteelPlugDoorBlockEntity> {

    public SteelPlugDoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(SteelPlugDoorBlockEntity blockEntity, float partialTicks,
                              PoseStack matrices, MultiBufferSource buffers,
                              int light, int overlay) {
        BlockState state = blockEntity.getBlockState();
        Direction facing = state.getValue(DoorBlock.FACING);
        DoorHingeSide hinge = state.getValue(DoorBlock.HINGE);
        float progress = blockEntity.doorAnimation(partialTicks);
        float swing = SteelPlugDoorAnimation.swingAngle(hinge, progress);
        var pivot = SteelPlugDoorAnimation.hingePivot(hinge);

        matrices.pushPose();
        matrices.translate(0.5, 0.0, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(
                SteelPlugDoorAnimation.facingAngle(facing)));
        matrices.translate(-0.5, 0.0, -0.5);

        VertexConsumer consumer = buffers.getBuffer(RenderType.solid());
        boolean left = hinge == DoorHingeSide.LEFT;
        int lowerLight = blockEntity.getLevel() == null ? light
                : LevelRenderer.getLightColor(blockEntity.getLevel(), blockEntity.getBlockPos().below());

        if (SteelPlugDoorAnimation.shouldRenderUpperFrameCap(progress, blockEntity.hasRoof())) {
            render(left ? SteelPlugDoorPartialModels.LINTEL_BOTTOM_LEFT
                            : SteelPlugDoorPartialModels.LINTEL_BOTTOM_RIGHT,
                    state, matrices, consumer, light);
        }
        if (SteelPlugDoorAnimation.shouldRenderFrameCaps(progress)) {
            matrices.pushPose();
            matrices.translate(0.0, -1.0, 0.0);
            render(left ? SteelPlugDoorPartialModels.THRESHOLD_TOP_LEFT
                            : SteelPlugDoorPartialModels.THRESHOLD_TOP_RIGHT,
                    state, matrices, consumer, lowerLight);
            matrices.popPose();
        }

        matrices.translate(pivot.x, 0.0, pivot.z);
        matrices.mulPose(Axis.YP.rotationDegrees(swing));
        matrices.translate(-pivot.x, 0.0, -pivot.z);

        render(left ? SteelPlugDoorPartialModels.TOP_LEFT : SteelPlugDoorPartialModels.TOP_RIGHT,
                state, matrices, consumer, light);
        if (!blockEntity.hasRoof()) {
            render(left ? SteelPlugDoorPartialModels.TOP_CAP_LEFT : SteelPlugDoorPartialModels.TOP_CAP_RIGHT,
                    state, matrices, consumer, light);
        }

        matrices.pushPose();
        matrices.translate(0.0, -1.0, 0.0);
        render(left ? SteelPlugDoorPartialModels.BOTTOM_LEFT : SteelPlugDoorPartialModels.BOTTOM_RIGHT,
                state, matrices, consumer, lowerLight);
        matrices.popPose();
        matrices.popPose();
    }

    private static void render(PartialModel model, BlockState state, PoseStack matrices,
                               VertexConsumer consumer, int light) {
        SuperByteBuffer buffer = CachedBuffers.partial(model, state);
        buffer.light(light).renderInto(matrices, consumer);
    }
}
