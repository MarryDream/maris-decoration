package marrydream.marisdecoration.block.enums;

import net.minecraft.util.StringIdentifiable;

public enum PropLadderShape implements StringIdentifiable {
    START( "start" ),
    NORMAL( "normal" );

    private final String name;

    private PropLadderShape( String name ) {
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
