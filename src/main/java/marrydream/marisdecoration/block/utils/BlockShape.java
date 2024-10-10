package marrydream.marisdecoration.block.utils;

import net.minecraft.block.Block;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

// 用于存储外观碰撞体积和对实体来说的碰撞体积
public class BlockShape {
    private VoxelShape outlineShape = null;
    private VoxelShape collisionShape = null;

    // 通过两个碰撞体积组创建新的碰撞体积
    public BlockShape( BlockShape unionShapeA, BlockShape unionShapeB ) {
        this.outlineShape = VoxelShapes.union( unionShapeA.outlineShape, unionShapeB.outlineShape );
        this.collisionShape = VoxelShapes.union( unionShapeA.collisionShape, unionShapeB.collisionShape );
    }

    // 通过坐标创建外观碰撞体积与实体碰撞体积
    public BlockShape( double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double collisionHeight ) {
        this.outlineShape = Block.createCuboidShape( minX, minY, minZ, maxX, maxY, maxZ );
        this.collisionShape = Block.createCuboidShape( minX, minY, minZ, maxX, collisionHeight, maxZ );
    }

    public VoxelShape getOutlineShape() {
        return this.outlineShape;
    }

    // 获取碰撞体积，如果不存在 collisionShape 则返回 outlineShape
    public VoxelShape getCollisionShape() {
        return this.collisionShape != null ? this.collisionShape : this.outlineShape;
    }
}
