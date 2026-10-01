package marrydream.marisdecoration.block.property;

import marrydream.marisdecoration.block.enums.*;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.Direction;

public class Properties {
    /** 基本方向: 北、东、南、西 */
    public static final DirectionProperty BASE_ORIENTATION = DirectionProperty.of( "facing", Direction.Type.HORIZONTAL );
    /** 指定块是否含水 */
    public static final BooleanProperty WATERLOGGED = BooleanProperty.of( "waterlogged" );
    /** 梯子形状: 开始、中间 */
    public static final EnumProperty<PropLadderShape> LADDER_SHAPE = EnumProperty.of( "shape", PropLadderShape.class );
}
