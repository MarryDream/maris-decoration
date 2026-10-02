package marrydream.marisdecoration.block;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardLayer;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.FaceDir;
import marrydream.marisdecoration.init.ModBlockEntity;

/** Canonical roof storage for the upper half of the base steel plug door. */
public class SteelPlugDoorBlockEntity extends LayeredCopycatBoardBlockEntity {

    public static final int ROOF_OCCUPANCY =
            LayeredBoardSlots.slotBitMask( FaceDir.UP, BoardLayer.OUTER );

    private boolean suppressRoofDrops;
    private final LerpedFloat doorAnimation;

    public SteelPlugDoorBlockEntity( BlockPos pos, BlockState state ) {
        super( ModBlockEntity.STEEL_PLUG_DOOR, pos, state );
        doorAnimation = LerpedFloat.linear().startWithValue(state.getValue(DoorBlock.OPEN) ? 1.0F : 0.0F);
    }

    /** Client-only ticker: every open source converges through the real OPEN block state. */
    public void tickDoorAnimation() {
        if (level == null || !level.isClientSide) return;
        doorAnimation.chase(getBlockState().getValue(DoorBlock.OPEN) ? 1.0F : 0.0F,
                0.15F, Chaser.LINEAR);
        doorAnimation.tickChaser();
    }

    public float doorAnimation(float partialTicks) {
        return doorAnimation.getValue(partialTicks);
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition.below()).minmax(new AABB(worldPosition)).inflate(1.0);
    }

    @Override
    protected int sanitizeOccupancy( int value ) {
        return value & ROOF_OCCUPANCY;
    }

    public boolean hasRoof() {
        return occupancy() == ROOF_OCCUPANCY;
    }

    public void insertRoof() {
        setOccupancy( ROOF_OCCUPANCY );
    }

    public void clearRoof() {
        clearWindows();
        setOccupancy( 0 );
    }

    public void suppressRoofDrops() {
        suppressRoofDrops = true;
        clearConsumedItems();
    }

    public boolean consumeDropSuppression() {
        boolean value = suppressRoofDrops;
        suppressRoofDrops = false;
        return value;
    }
}
