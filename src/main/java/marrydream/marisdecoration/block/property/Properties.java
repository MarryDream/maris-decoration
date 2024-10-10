package marrydream.marisdecoration.block.property;

import marrydream.marisdecoration.block.enums.GuardrailColumn;
import marrydream.marisdecoration.block.enums.PropHalf;
import marrydream.marisdecoration.block.enums.PropShape;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.Direction;

public class Properties {
    /** 直线、内左、内右、外左、外右 */
    public static final EnumProperty<PropShape> PROP_SHAPE = EnumProperty.of( "shape", PropShape.class );
    /** 上、下 */
    public static final EnumProperty<PropHalf> PROP_HALF = EnumProperty.of( "half", PropHalf.class );
    /** 无柱、左柱、右柱、中柱、两侧柱 */
    public static final EnumProperty<GuardrailColumn> GUARDRAIL_COLUMN = EnumProperty.of( "column", GuardrailColumn.class );
    /** 基本方向: 北、东、南、西 */
    public static final DirectionProperty BASE_ORIENTATION = DirectionProperty.of( "facing", Direction.Type.HORIZONTAL );
    /** 指定块是否含水 */
    public static final BooleanProperty WATERLOGGED = BooleanProperty.of( "waterlogged" );
}
