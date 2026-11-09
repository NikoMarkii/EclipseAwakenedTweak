package com.Niko.EclipseAwakenedTweak.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cinematic, localized boss-name introduction shown once when a boss enters view. */
@Mod.EventBusSubscriber(modid = "eclipseawakenedtweak", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossIntroOverlay {
    private static final int SUBTITLE_REVEAL_TICKS = 28;
    private static final int NAME_APPEAR_TICK = 32;
    private static final int TOTAL_TICKS = 100;

    private static final Map<UUID, Intro> ACTIVE_INTROS = new HashMap<>();

    private BossIntroOverlay() {}

    public static void start(UUID uuid, String entityId, int subtitleColor, int nameColor, boolean simultaneous) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        String key = "boss_intro." + entityId.replace(':', '.');
        Component subtitle = I18n.exists(key) ? Component.translatable(key) : Component.translatable("boss_intro.generic");
        ACTIVE_INTROS.put(uuid, new Intro(uuid, entityId, subtitle, minecraft.level.getGameTime(),
                subtitleColor, nameColor, simultaneous));
    }

    @SubscribeEvent
    public static void onOverlayRender(RenderGuiOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            return;
        }

        long now = minecraft.level.getGameTime();
        ACTIVE_INTROS.entrySet().removeIf(entry -> now - entry.getValue().startTick >= TOTAL_TICKS);
        if (ACTIVE_INTROS.isEmpty()) {
            return;
        }

        Intro intro = ACTIVE_INTROS.values().stream().reduce((first, second) -> second).orElse(null);
        if (intro == null) {
            return;
        }

        long age = Math.max(0, now - intro.startTick);
        GuiGraphics graphics = event.getGuiGraphics();
        int centerX = event.getWindow().getGuiScaledWidth() / 2;
        int centerY = event.getWindow().getGuiScaledHeight() / 2;
        int alpha = alphaAt(age);

        int characterCount = intro.subtitle.getString().codePointCount(0, intro.subtitle.getString().length());
        int shownCharacters = (int) Math.min(characterCount, age * characterCount / SUBTITLE_REVEAL_TICKS);
        String subtitleText = prefixByCodePoints(intro.subtitle.getString(), shownCharacters);
        if (!subtitleText.isEmpty()) {
            PoseStack pose = graphics.pose(); pose.pushPose();
            pose.translate(centerX, centerY - 24, 0); pose.scale(1.45F, 1.45F, 1.0F);
            graphics.drawCenteredString(minecraft.font, Component.literal(subtitleText).withStyle(ChatFormatting.BOLD), 0, 0,
                    (alpha << 24) | intro.subtitleColor);
            pose.popPose();
        }

        // Both layouts wait for the complete subtitle; sequential profiles keep the extra beat.
        long nameStartTick = intro.simultaneous
                ? SUBTITLE_REVEAL_TICKS
                : Math.max(NAME_APPEAR_TICK, SUBTITLE_REVEAL_TICKS);
        if (age >= nameStartTick) {
            long nameAge = age - nameStartTick;
            float scale = nameAge < 7 ? 5.4F - 0.4F * nameAge / 7.0F : 5.0F;
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(centerX, centerY + 3, 0);
            pose.scale(scale, scale, 1.0F);
            String nameKey = "boss_intro." + intro.entityId.replace(':', '.') + ".name";
            Component name = I18n.exists(nameKey) ? Component.translatable(nameKey) : Component.literal(intro.entityId);
            if (!I18n.exists(nameKey)) {
                for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity.getUUID().equals(intro.uuid)) { name = entity.getDisplayName(); break; }
                }
            }
            graphics.drawCenteredString(minecraft.font, name.copy().withStyle(ChatFormatting.BOLD), 0, 0,
                    (alpha << 24) | intro.nameColor);
            pose.popPose();
        }
    }

    private static String prefixByCodePoints(String text, int count) {
        int end = text.offsetByCodePoints(0, Math.min(count, text.codePointCount(0, text.length())));
        return text.substring(0, end);
    }

    private static int alphaAt(long age) {
        if (age < 5) return (int) (255 * age / 5.0F);
        if (age < 78) return 255;
        return (int) (255 * (TOTAL_TICKS - age) / (float) (TOTAL_TICKS - 78));
    }

    private record Intro(UUID uuid, String entityId, Component subtitle, long startTick,
                         int subtitleColor, int nameColor, boolean simultaneous) {}
}
