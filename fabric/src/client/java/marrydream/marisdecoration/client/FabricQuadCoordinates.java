package marrydream.marisdecoration.client;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
record FabricQuadCoordinates(MutableQuadView quad) implements QuadCoordinates {
 public float x(int v){return quad.x(v);} public float y(int v){return quad.y(v);} public float z(int v){return quad.z(v);}
 public float u(int v){return quad.u(v);} public float v(int v){return quad.v(v);}
 public void uv(int vertex,float u,float v){quad.uv(vertex,u,v);}
}
