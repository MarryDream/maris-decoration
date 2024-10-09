package marrydream.marisdecoration.block.enums;

import net.minecraft.util.StringIdentifiable;

public enum GuardrailColumn implements StringIdentifiable {
    NONE( "none" ),
    LEFT( "left" ),
    RIGHT( "right" ),
    CENTER( "center" ),
    SIDE( "side" );

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
