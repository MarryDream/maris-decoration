package marrydream.marisdecoration.block.enums;

import net.minecraft.util.StringRepresentable;

public enum PropLadderShape implements StringRepresentable {
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
    public String getSerializedName( ) {
        return this.name;
    }
}
