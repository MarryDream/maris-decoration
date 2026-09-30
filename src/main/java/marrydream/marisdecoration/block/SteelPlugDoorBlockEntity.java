package marrydream.marisdecoration.block;

import marrydream.marisdecoration.block.utils.LayeredBoardSlots;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.BoardLayer;
import marrydream.marisdecoration.block.utils.LayeredBoardSlots.FaceDir;
import marrydream.marisdecoration.init.ModBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** Canonical roof storage for the upper half of the base steel plug door. */
public class SteelPlugDoorBlockEntity extends LayeredCopycatBoardBlockEntity {

    public static final int ROOF_OCCUPANCY =
            LayeredBoardSlots.slotBitMask( FaceDir.UP, BoardLayer.OUTER );

    private boolean suppressRoofDrops;

    public SteelPlugDoorBlockEntity( BlockPos pos, BlockState state ) {
        super( ModBlockEntity.STEEL_PLUG_DOOR, pos, state );
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
