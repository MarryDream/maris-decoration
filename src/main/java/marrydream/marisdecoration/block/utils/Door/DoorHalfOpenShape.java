package marrydream.marisdecoration.block.utils.Door;

public class DoorHalfOpenShape {
    public DoorOpenShape top;
    public DoorOpenShape bottom;

    DoorHalfOpenShape( DoorOpenShape top, DoorOpenShape bottom ) {
        this.top = top;
        this.bottom = bottom;
    }
}
