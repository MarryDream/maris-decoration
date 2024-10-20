package marrydream.marisdecoration.block.utils.Door;

import net.minecraft.util.shape.VoxelShape;

public class DoorOpenShape {
    public VoxelShape left;
    public VoxelShape right;

    DoorOpenShape( VoxelShape left, VoxelShape right ) {
        this.left = left;
        this.right = right;
    }
}
