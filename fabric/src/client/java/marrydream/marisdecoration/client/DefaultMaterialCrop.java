package marrydream.marisdecoration.client;
import com.simibubi.create.foundation.model.BakedModelHelper;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
final class DefaultMaterialCrop extends DefaultMaterialGeometry {
 static void cropAndMove(MutableQuadView quad,TextureAtlasSprite sprite,AABB source,AABB target) {
  BakedModelHelper.cropAndMove(quad,sprite,source,new Vec3(target.minX-source.minX,target.minY-source.minY,target.minZ-source.minZ));
 }
}
