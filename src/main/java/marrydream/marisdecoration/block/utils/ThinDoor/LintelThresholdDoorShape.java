package marrydream.marisdecoration.block.utils.ThinDoor;

import net.minecraft.block.Block;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public class LintelThresholdDoorShape {
    public VoxelShape base;
    public DoorLORShape open;
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

    public void setOpenShape( LintelThresholdDoorShape left, LintelThresholdDoorShape right ) {
        DoorShape leftOpen = new DoorShape(
                VoxelShapes.union( this.LINTEL_SHAPE, left.topBase ),
                VoxelShapes.union( this.THRESHOLD_SHAPE, left.bottomBase )
        );

        DoorShape rightOpen = new DoorShape(
                VoxelShapes.union( this.LINTEL_SHAPE, right.topBase ),
                VoxelShapes.union( this.THRESHOLD_SHAPE, right.bottomBase )
        );

        this.open = new DoorLORShape( leftOpen, rightOpen );
    }
}
