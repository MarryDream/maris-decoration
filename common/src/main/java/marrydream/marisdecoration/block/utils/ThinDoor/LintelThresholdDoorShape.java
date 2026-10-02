package marrydream.marisdecoration.block.utils.ThinDoor;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LintelThresholdDoorShape {
    public VoxelShape base;
    public DoorLORShape open;
    public VoxelShape topBase;
    public VoxelShape bottomBase;
    private final VoxelShape THRESHOLD_SHAPE;
    private final VoxelShape LINTEL_SHAPE;

    public LintelThresholdDoorShape( double minX, double minZ, double maxX, double maxZ ) {
        this.base = Block.box( minX, 0.0, minZ, maxX, 16.0, maxZ );
        this.topBase = Block.box( minX, 0.0, minZ, maxX, 15.0, maxZ );
        this.bottomBase = Block.box( minX, 1.0, minZ, maxX, 16.0, maxZ );
        this.THRESHOLD_SHAPE = Block.box( minX, 0.0, minZ, maxX, 1.0, maxZ );
        this.LINTEL_SHAPE = Block.box( minX, 15.0, minZ, maxX, 16.0, maxZ );
    }

    public void setOpenShape( LintelThresholdDoorShape left, LintelThresholdDoorShape right ) {
        DoorShape leftOpen = new DoorShape(
                Shapes.or( this.LINTEL_SHAPE, left.topBase ),
                Shapes.or( this.THRESHOLD_SHAPE, left.bottomBase )
        );

        DoorShape rightOpen = new DoorShape(
                Shapes.or( this.LINTEL_SHAPE, right.topBase ),
                Shapes.or( this.THRESHOLD_SHAPE, right.bottomBase )
        );

        this.open = new DoorLORShape( leftOpen, rightOpen );
    }
}
