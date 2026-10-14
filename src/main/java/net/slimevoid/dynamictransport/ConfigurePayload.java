package net.slimevoid.dynamictransport;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record ConfigurePayload(int containerId, String name, int y) implements CustomPacketPayload {
  public static final Type<ConfigurePayload> TYPE = new Type<>(DynamicTransport.id("configure"));
  public static final StreamCodec<RegistryFriendlyByteBuf, ConfigurePayload> CODEC =
      StreamCodec.of(
          (buf, value) -> {
            buf.writeVarInt(value.containerId);
            buf.writeUtf(value.name, 32);
            buf.writeInt(value.y);
          },
          buf -> new ConfigurePayload(buf.readVarInt(), buf.readUtf(32), buf.readInt()));

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }

  static void register(RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToServer(
            TYPE,
            CODEC,
            (data, context) ->
                context.enqueueWork(
                    () -> {
                      if (context.player().containerMenu instanceof TransportMenu menu
                          && menu.containerId == data.containerId)
                        menu.configure(context.player(), data.name, data.y);
                    }));
  }
}
