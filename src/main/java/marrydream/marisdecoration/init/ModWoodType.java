package marrydream.marisdecoration.init;

import net.minecraft.block.BlockSetType;
import net.minecraft.block.WoodType;

/**
 * 柚木木种。
 *
 * <p>原版每个木种都有一组 {@link BlockSetType} + {@link WoodType}：方块设置里不写音效，
 * 而是由这两个对象提供（活板门、栅栏门、压力板、按钮的构造器会自己取用），
 * 同时决定门的开合音效、按钮与压力板的咔哒声。
 * 原版注册在 {@code Blocks} 的静态初始化里，木种列表由 {@code WoodType.register} 维护；
 * 该方法在 1.20.1 是私有的，模组侧按惯例直接构造即可（它们只是记录值，不占注册表 ID）。
 */
public final class ModWoodType {
    /** 柚木方块音效组：与原版木种一样使用木质音效、且可以徒手开关。 */
    public static final BlockSetType TEAK_SET_TYPE = new BlockSetType( "teak" );

    /** 柚木木种：栅栏门取用它的木质音效和栅栏门开关音效。 */
    public static final WoodType TEAK = new WoodType( "teak", TEAK_SET_TYPE );

    private ModWoodType( ) {
    }
}
