package marrydream.marisdecoration.block.enums;

import net.minecraft.util.StringIdentifiable;

public enum GuardrailColumn implements StringIdentifiable {
    NONE( "none" ), // 仅 outer 存在
    LEFT( "left" ), // 都存在
    RIGHT( "right" ), // 都存在
    CENTER( "center" ), // 仅 inner 存在
    SIDE( "side" ); // 仅 outer 不存在

    private final String name;

    private GuardrailColumn( String name ) {
        this.name = name;
    }

    public String toString( ) {
        return this.name;
    }

    @Override
    public String asString( ) {
        return this.name;
    }
}
