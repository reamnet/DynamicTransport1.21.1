package net.slimevoid.dynamictransport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.slimevoid.dynamictransport.*;

@EventBusSubscriber(
    modid = DynamicTransport.ID,
    bus = EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT)
public final class ClientRegistration {
  @SubscribeEvent
  public static void screens(RegisterMenuScreensEvent event) {
    event.register(DynamicTransport.MENU.get(), TransportScreen::new);
  }

  @SubscribeEvent
  public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(DynamicTransport.MOVING.get(), MovingRenderer::new);
    event.registerBlockEntityRenderer(DynamicTransport.TILE.get(), CamouflageRenderer::new);
  }

  private static class MovingRenderer extends EntityRenderer<MovingElevator> {
    MovingRenderer(EntityRendererProvider.Context context) {
      super(context);
    }

    @Override
    public void render(
        MovingElevator entity,
        float yaw,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int light) {
      pose.pushPose();
      pose.translate(-0.5, 0, -0.5);
      Minecraft.getInstance()
          .getBlockRenderer()
          .renderSingleBlock(entity.appearance(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
      pose.popPose();
      super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MovingElevator entity) {
      return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
  }

  private static class CamouflageRenderer implements BlockEntityRenderer<TransportBlockEntity> {
    CamouflageRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(
        TransportBlockEntity tile,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int light,
        int overlay) {
      if (tile.hasCamouflage()) {
        pose.pushPose();
        pose.translate(-0.001, -0.001, -0.001);
        pose.scale(1.002F, 1.002F, 1.002F);
        Minecraft.getInstance()
            .getBlockRenderer()
            .renderSingleBlock(tile.appearance(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
      }
      OverlayRenderer.draw(tile.overlayFaces(), pose, buffers, light);
    }
  }
}
