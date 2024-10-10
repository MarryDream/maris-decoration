package marrydream.marisdecoration.block.utils;

import net.minecraft.block.Block;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

// 用于存储外观碰撞体积和对实体来说的碰撞体积
public class BlockShape {
    private VoxelShape outlineShape = null;
    private VoxelShape collisionShape = null;

    /**
     * 通过两个碰撞体积组创建新的碰撞体积
     * @param unionShapeA 碰撞体积组A
     * @param unionShapeB 碰撞体积组B
     */
    public BlockShape( BlockShape unionShapeA, BlockShape unionShapeB ) {
        this.outlineShape = VoxelShapes.union( unionShapeA.outlineShape, unionShapeB.outlineShape );
        // 如果接受的两个 BlockShape 自己的两个碰撞体积完全相同，则实体碰撞体积为外观碰撞体积，不用再多此一举合并了
        if ( unionShapeA.outlineShape == unionShapeA.collisionShape && unionShapeB.outlineShape == unionShapeB.collisionShape ) {
            this.collisionShape = this.outlineShape;
        } else {
            this.collisionShape = VoxelShapes.union( unionShapeA.collisionShape, unionShapeB.collisionShape );
        }

    }

    /**
     * 通过坐标创建外观碰撞体积与实体碰撞体积
     * @param collisionHeight 实体碰撞体积的高度，若为 -1 则实体碰撞体积为外观碰撞体积
     */
    public BlockShape( double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double collisionHeight ) {
        this.outlineShape = Block.createCuboidShape( minX, minY, minZ, maxX, maxY, maxZ );
        this.collisionShape = collisionHeight == -1 ? this.outlineShape : Block.createCuboidShape( minX, minY, minZ, maxX, collisionHeight, maxZ );
    }

    public VoxelShape getOutlineShape( ) {
        return this.outlineShape;
    }

    // 获取碰撞体积，如果不存在 collisionShape 则返回 outlineShape
    public VoxelShape getCollisionShape( ) {
        return this.collisionShape != null ? this.collisionShape : this.outlineShape;
    }
}
