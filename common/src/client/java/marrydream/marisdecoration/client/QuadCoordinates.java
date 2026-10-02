package marrydream.marisdecoration.client;
public interface QuadCoordinates {
 float x(int vertex); float y(int vertex); float z(int vertex);
 float u(int vertex); float v(int vertex); void uv(int vertex,float u,float v);
}
