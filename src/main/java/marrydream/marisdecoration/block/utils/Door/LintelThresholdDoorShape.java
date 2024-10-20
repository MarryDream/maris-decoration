package marrydream.marisdecoration.block.utils.Door;

import net.minecraft.block.Block;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public class LintelThresholdDoorShape {
    public VoxelShape base;
    public DoorHalfOpenShape open;
    public VoxelShape topBase;
    public VoxelShape bottomBase;
    private final VoxelShape THRESHOLD_SHAPE;
    private final VoxelShape LINTEL_SHAPE;

    public LintelThresholdDoorShape( double minX, double minZ, double maxX, double maxZ ) {
        this.base = Block.createCuboidShape( minX, 0.0, minZ, maxX, 16.0, maxZ );
        this.topBase = Block.createCuboidShape( minX, 0.0, minZ, maxX, 15.0, maxZ );
        this.bottomBase = Block.createCuboidShape( minX, 1.0, minZ, maxX, 16.0, maxZ );
        this.THRESHOLD_SHAPE = Block.createCuboidShape( minX, 0.0, minZ, maxX, 1.0, maxZ );
        this.LINTEL_SHAPE = Block.createCuboidShape( minX, 15.0, minZ, maxX, 16.0, maxZ );
    }

    public void setOpenShape( LintelThresholdDoorShape leftOpen, LintelThresholdDoorShape rightOpen ) {
        DoorOpenShape topOpen = new DoorOpenShape(
                VoxelShapes.union( this.LINTEL_SHAPE, leftOpen.topBase ),
                VoxelShapes.union( this.LINTEL_SHAPE, rightOpen.topBase )
        );
        DoorOpenShape bottomOpen = new DoorOpenShape(
                VoxelShapes.union( this.THRESHOLD_SHAPE, leftOpen.bottomBase ),
                VoxelShapes.union( this.THRESHOLD_SHAPE, rightOpen.bottomBase )
        );
        this.open = new DoorHalfOpenShape( topOpen, bottomOpen );
    }
}
