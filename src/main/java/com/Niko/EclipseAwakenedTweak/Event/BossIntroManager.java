package com.Niko.EclipseAwakenedTweak.Event;

import com.Niko.EclipseAwakenedTweak.Data.BossIntroSavedData;
import com.Niko.EclipseAwakenedTweak.EclipseAwakenedTweak;
import com.Niko.EclipseAwakenedTweak.Network.BossIntroNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side trigger/profile registry. Other compatibility modules can register custom timing. */
@Mod.EventBusSubscriber(modid = EclipseAwakenedTweak.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossIntroManager {
    private static final Map<ResourceLocation, Profile> PROFILES = new ConcurrentHashMap<>();

    static {
        add("minecraft:ender_dragon", Trigger.SPAWN, 0xC887FF, 0xF2C5FF, false);
        add("minecraft:wither", Trigger.SPAWN, 0xAFAFAF, 0xF0F0F0, true);
        add("cataclysm:ender_guardian", Trigger.SPAWN, 0x9A78FF, 0xD8C5FF, false);
        add("cataclysm:ancient_remnant", Trigger.CUSTOM, 0xDDB85B, 0xFFF0AA, true);
        add("cataclysm:netherite_monstrosity", Trigger.CUSTOM, 0xFF8A48, 0xFFE0A0, true);
        add("cataclysm:ignis", Trigger.SPAWN, 0xFF673D, 0xFFC16E, false);
        add("cataclysm:the_harbinger", Trigger.CUSTOM, 0xFF6A72, 0xFFD16A, false);
        add("cataclysm:the_leviathan", Trigger.SPAWN, 0x4b0082, 0x8a2be2, true);
        add("cataclysm:maledictus", Trigger.SPAWN, 0xdaa520, 0x00ffff, false);
        add("cataclysm:scylla", Trigger.CUSTOM, 0x5B9DFF, 0xD0E7FF, true);
    }

    private static void add(String id, Trigger trigger, int subtitleColor, int nameColor, boolean simultaneous) {
        PROFILES.put(parseEntityTypeId(id), new Profile(trigger, subtitleColor, nameColor, simultaneous));
    }

    private static ResourceLocation parseEntityTypeId(String id) {
        int separator = id.indexOf(':');
        if (separator <= 0 || separator == id.length() - 1) {
            throw new IllegalArgumentException("Entity type ID must be namespace:path: " + id);
        }
        return ResourceLocation.fromNamespaceAndPath(id.substring(0, separator), id.substring(separator + 1));
    }

    private BossIntroManager() {}

    public enum Trigger { SPAWN, CUSTOM }
    public record Profile(Trigger trigger, int subtitleColor, int nameColor, boolean simultaneous) {}

    /** Register a per-entity intro. CUSTOM profiles are started by calling trigger(entity) at the desired event. */
    public static void register(ResourceLocation entityType, Profile profile) {
        PROFILES.put(entityType, profile);
    }

    /** Call from a summon, item-use, altar, or wake-up event for CUSTOM profiles. */
    public static void trigger(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !(entity instanceof LivingEntity living)) return;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Profile profile = PROFILES.get(id);
        if (profile == null) return;
        if (BossIntroSavedData.get(level).markSeen(entity.getUUID())) {
            BossIntroNetwork.StartIntro packet = new BossIntroNetwork.StartIntro(entity.getUUID(), id.toString(),
                    profile.subtitleColor, profile.nameColor, profile.simultaneous);
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(entity) <= 128.0 * 128.0) BossIntroNetwork.send(player, packet);
            }
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof LivingEntity living)) return;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
        Profile profile = PROFILES.get(id);
        if (profile != null && profile.trigger == Trigger.SPAWN) trigger(living);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel)) return;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id.equals(ResourceLocation.fromNamespaceAndPath("cataclysm", "the_harbinger"))
                && invokeBoolean(entity, "getIsAct")) {
            trigger(entity);
        } else if (id.equals(ResourceLocation.fromNamespaceAndPath("cataclysm", "netherite_monstrosity"))
                && invokeBoolean(entity, "getIsAwaken")) {
            trigger(entity);
        } else if (id.equals(ResourceLocation.fromNamespaceAndPath("cataclysm", "scylla"))
                && invokeBoolean(entity, "getAct")) {
            // Cataclysm activates Scylla through its anchor interaction.
            trigger(entity);
        } else if (id.equals(ResourceLocation.fromNamespaceAndPath("cataclysm", "ancient_remnant"))
                && invokeInt(entity, "getAttackState") == 2) {
            // Attack state 2 is the Remnant's wake-up animation.
            trigger(entity);
        }
    }

    private static boolean invokeBoolean(Object target, String methodName) {
        try {
            Object result = target.getClass().getMethod(methodName).invoke(target);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return false;
        }
    }

    private static int invokeInt(Object target, String methodName) {
        try {
            Object result = target.getClass().getMethod(methodName).invoke(target);
            return result instanceof Integer value ? value : -1;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return -1;
        }
    }

}
