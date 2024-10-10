package marrydream.marisdecoration.block.utils;

// 方向、内外连接方块组
public class DirectionConnectBlockGroup {
    private static final int[] SHAPE_INDICES = new int[]{ 12, 5, 3, 10, 14, 13, 7, 11, 13, 7, 11, 14, 8, 4, 1, 2, 4, 1, 2, 8 };
    private final BlockShape[] ALL_SHAPES;

    public DirectionConnectBlockGroup(
            BlockShape northStraight, BlockShape southStraight, BlockShape westStraight, BlockShape eastStraight,
            BlockShape northWestOuter, BlockShape southWestOuter, BlockShape northEastOuter, BlockShape southEastOuter,
            BlockShape northWestInner, BlockShape southWestInner, BlockShape northEastInner, BlockShape southEastInner
    ) {
        this.ALL_SHAPES = new BlockShape[]{
                null, northWestOuter, northEastOuter, northStraight, southWestOuter,
                westStraight, null, northWestInner, southEastOuter, null, eastStraight,
                northEastInner, southStraight, southWestInner, southEastInner, null
        };
    }

    private int getShapeIndexIndex( int shapeIndex, int facingIndex ) {
        return shapeIndex * 4 + facingIndex;
    }

    // 根据形状和朝向状态获取碰撞体积
    public BlockShape getShapeByShapeAndFacing( int shapeIndex, int facingIndex ) {
        return this.ALL_SHAPES[SHAPE_INDICES[getShapeIndexIndex( shapeIndex, facingIndex )]];
    }
}
