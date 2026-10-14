package net.slimevoid.dynamictransport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

final class OverlayRenderer {
  private static final ResourceLocation TEXTURE =
      ResourceLocation.fromNamespaceAndPath(
          "dynamictransport", "textures/block/elevator_overlay.png");
  // Vertices are ordered around each face; the cutout texture provides the selection panel.
  private static final float[][][] FACES = {
    {{0, 0, 1}, {1, 0, 1}, {1, 0, 0}, {0, 0, 0}}, {{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}},
    {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}}, {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
    {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}}, {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}
  };

  static void draw(int faces, PoseStack pose, MultiBufferSource buffers, int light) {
    pose.pushPose();
    pose.translate(-0.002, -0.002, -0.002);
    pose.scale(1.004F, 1.004F, 1.004F);
    var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
    float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
    for (Direction side : Direction.values())
      if ((faces & (1 << side.get3DDataValue())) != 0) {
        var points = FACES[side.get3DDataValue()];
        for (int i = 0; i < 4; i++)
          vertices
              .addVertex(pose.last(), points[i][0], points[i][1], points[i][2])
              .setColor(255, 255, 255, 255)
              .setUv(uv[i][0], uv[i][1])
              .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
              .setLight(light)
              .setNormal(pose.last(), side.getStepX(), side.getStepY(), side.getStepZ());
      }
    pose.popPose();
  }
}
