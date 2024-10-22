package marrydream.marisdecoration.block.utils.ThinDoor;

import net.minecraft.block.Block;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public class RoofThresholdDoorShape {
    public DoorShape close;
    public DoorLORShape open;
    public VoxelShape topBase;
    public VoxelShape bottomBase;
    private final VoxelShape ROOF_SHAPE;
    private final VoxelShape THRESHOLD_SHAPE;

    public RoofThresholdDoorShape( double minX, double minZ, double maxX, double maxZ, VoxelShape roofShape ) {
        this.ROOF_SHAPE = roofShape;
        this.THRESHOLD_SHAPE = Block.createCuboidShape( minX, 0.0, minZ, maxX, 1.0, maxZ );
        this.topBase = Block.createCuboidShape( minX, 0.0, minZ, maxX, 15.0, maxZ );
        this.bottomBase = Block.createCuboidShape( minX, 1.0, minZ, maxX, 16.0, maxZ );
        this.close = new DoorShape(
                VoxelShapes.union( this.ROOF_SHAPE, this.topBase ),
                Block.createCuboidShape( minX, 0.0, minZ, maxX, 16.0, maxZ )
        );
    }

    public void setOpenShape( RoofThresholdDoorShape left, RoofThresholdDoorShape right ) {
        DoorShape leftOpen = new DoorShape(
                VoxelShapes.union( this.ROOF_SHAPE, left.topBase ),
                VoxelShapes.union( this.THRESHOLD_SHAPE, left.bottomBase )
        );

        DoorShape rightOpen = new DoorShape(
                VoxelShapes.union( this.ROOF_SHAPE, right.topBase ),
                VoxelShapes.union( this.THRESHOLD_SHAPE, right.bottomBase )
        );

        this.open = new DoorLORShape( leftOpen, rightOpen );
    }
}
