package marrydream.marisdecoration.block.property;

import marrydream.marisdecoration.block.enums.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class Properties {
    /** 基本方向: 北、东、南、西 */
    public static final DirectionProperty BASE_ORIENTATION = DirectionProperty.create( "facing", Direction.Plane.HORIZONTAL );
    /** 指定块是否含水 */
    public static final BooleanProperty WATERLOGGED = BooleanProperty.create( "waterlogged" );
    /** 梯子形状: 开始、中间 */
    public static final EnumProperty<PropLadderShape> LADDER_SHAPE = EnumProperty.create( "shape", PropLadderShape.class );
}
