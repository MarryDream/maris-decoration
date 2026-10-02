package marrydream.marisdecoration.block.utils.ThinDoor;

import net.minecraft.world.phys.shapes.VoxelShape;

public class DoorShape {
    public VoxelShape top;
    public VoxelShape bottom;

    DoorShape( VoxelShape top, VoxelShape bottom ) {
        this.top = top;
        this.bottom = bottom;
    }
}
