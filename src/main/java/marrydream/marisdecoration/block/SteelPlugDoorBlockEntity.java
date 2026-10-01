package marrydream.marisdecoration.block;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardLayer;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.FaceDir;
import marrydream.marisdecoration.init.ModBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Canonical roof storage for the upper half of the base steel plug door. */
public class SteelPlugDoorBlockEntity extends LayeredCopycatBoardBlockEntity {

    public static final int ROOF_OCCUPANCY =
            LayeredBoardSlots.slotBitMask( FaceDir.UP, BoardLayer.OUTER );

    private boolean suppressRoofDrops;
    private final LerpedFloat doorAnimation;

    public SteelPlugDoorBlockEntity( BlockPos pos, BlockState state ) {
        super( ModBlockEntity.STEEL_PLUG_DOOR, pos, state );
        doorAnimation = LerpedFloat.linear().startWithValue(state.get(DoorBlock.OPEN) ? 1.0F : 0.0F);
    }

    /** Client-only ticker: every open source converges through the real OPEN block state. */
    public void tickDoorAnimation() {
        if (world == null || !world.isClient) return;
        doorAnimation.chase(getCachedState().get(DoorBlock.OPEN) ? 1.0F : 0.0F,
                0.15F, Chaser.LINEAR);
        doorAnimation.tickChaser();
    }

    public float doorAnimation(float partialTicks) {
        return doorAnimation.getValue(partialTicks);
    }

    @Override
    protected Box createRenderBoundingBox() {
        return new Box(pos.down()).union(new Box(pos)).expand(1.0);
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
