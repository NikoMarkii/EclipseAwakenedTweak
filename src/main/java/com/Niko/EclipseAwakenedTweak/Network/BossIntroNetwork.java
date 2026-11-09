package com.Niko.EclipseAwakenedTweak.Network;

import com.Niko.EclipseAwakenedTweak.EclipseAwakenedTweak;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;

public final class BossIntroNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(EclipseAwakenedTweak.MODID, "boss_intro"),
            () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;

    private BossIntroNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(0, StartIntro.class, StartIntro::encode, StartIntro::decode,
                StartIntro::handle);
    }

    public static void send(ServerPlayer player, StartIntro packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public record StartIntro(UUID uuid, String entityId, int subtitleColor, int nameColor,
                             boolean simultaneous) {
        private static void encode(StartIntro p, FriendlyByteBuf b) {
            b.writeUUID(p.uuid); b.writeUtf(p.entityId); b.writeInt(p.subtitleColor);
            b.writeInt(p.nameColor); b.writeBoolean(p.simultaneous);
        }
        private static StartIntro decode(FriendlyByteBuf b) {
            return new StartIntro(b.readUUID(), b.readUtf(), b.readInt(), b.readInt(), b.readBoolean());
        }
        private static void handle(StartIntro p, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.Niko.EclipseAwakenedTweak.client.BossIntroOverlay.start(p.uuid, p.entityId,
                            p.subtitleColor, p.nameColor, p.simultaneous)));
            ctx.setPacketHandled(true);
        }
    }
}
