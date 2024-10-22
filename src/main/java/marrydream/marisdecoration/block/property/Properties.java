package marrydream.marisdecoration.block.property;

import marrydream.marisdecoration.block.enums.*;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.Direction;

public class Properties {
    /** 直线、内左、内右、外左、外右 */
    public static final EnumProperty<PropShape> PROP_SHAPE = EnumProperty.of( "shape", PropShape.class );
    /** 上、下 */
    public static final EnumProperty<PropHalf> PROP_HALF = EnumProperty.of( "half", PropHalf.class );
    /** 无纹理，左纹理，右纹理，两侧纹理 */
    public static final EnumProperty<PropTexture> PROP_TEXTURE = EnumProperty.of( "texture", PropTexture.class );
    /** 无柱、左柱、右柱、中柱、两侧柱 */
    public static final EnumProperty<GuardrailColumn> GUARDRAIL_COLUMN = EnumProperty.of( "column", GuardrailColumn.class );
    /** 基本方向: 北、东、南、西 */
    public static final DirectionProperty BASE_ORIENTATION = DirectionProperty.of( "facing", Direction.Type.HORIZONTAL );
    /** 指定块是否含水 */
    public static final BooleanProperty WATERLOGGED = BooleanProperty.of( "waterlogged" );
    /** 梯子形状: 开始、中间 */
    public static final EnumProperty<PropLadderShape> LADDER_SHAPE = EnumProperty.of( "shape", PropLadderShape.class );
    /** 指定带房顶细门是否有房顶纹理 */
    public static final BooleanProperty DOOR_TEXTURE = BooleanProperty.of( "texture" );
}
