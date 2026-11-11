package com.Niko.EclipseAwakenedTweak.compatibility.netherexp.azazelhuman;

import com.benji.netherman.entity.AzazelHumanEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Keeps Goety optional while forwarding Azazel phase actions when it is installed. */
public final class AzazelGoetyBridge {
    private static final String MAGIC_CLASS = "com.Niko.EclipseAwakenedTweak.compatibility.netherexp.azazelhuman.AzazelGoetyMagic";
    private static volatile Class<?> magicType;
    private static volatile boolean magicLookupAttempted;

    private AzazelGoetyBridge() {}

    public static void halfPhase(AzazelHumanEntity azazel, LivingEntity target) {
        invoke("triggerHalfHealth", azazel, target);
    }

    public static void seventyPercentPhase(AzazelHumanEntity azazel, LivingEntity target) {
        invoke("triggerSeventyPercent", azazel, target);
    }

    public static void thirtyPercentPhase(AzazelHumanEntity azazel, LivingEntity target) {
        invoke("triggerThirtyPercent", azazel, target);
    }

    public static void castGoetyHellBlast(AzazelHumanEntity azazel, LivingEntity target, int direction) {
        invoke("castGoetyHellBlast", azazel, target, direction);
    }

    public static void castNetherSpell(AzazelHumanEntity azazel, LivingEntity target) {
        invoke("castNetherSpell", azazel, target);
    }

    public static void summonNetherServant(AzazelHumanEntity azazel, LivingEntity target, int choice) {
        invoke("summonNetherServant", azazel, target, choice);
    }

    public static void prepareSpell(AzazelHumanEntity azazel, boolean summon) {
        invoke("prepareSpell", azazel, summon);
    }

    public static boolean hasOwnedObsidianMonolith(AzazelHumanEntity azazel) {
        try {
            Class<?> magic = magicType();
            if (magic == null) return false;
            return (boolean) magic.getMethod("hasOwnedObsidianMonolith", AzazelHumanEntity.class)
                    .invoke(null, azazel);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    public static Entity getOwnedObsidianMonolith(AzazelHumanEntity azazel) {
        try {
            Class<?> magic = magicType();
            if (magic == null) return null;
            return (Entity) magic.getMethod("getOwnedObsidianMonolith", AzazelHumanEntity.class)
                    .invoke(null, azazel);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static void summonDamned(AzazelHumanEntity azazel, LivingEntity target) {
        invoke("summonDamned", azazel, target);
    }

    public static void summonMalghast(AzazelHumanEntity azazel, LivingEntity target) {
        invoke("summonMalghast", azazel, target);
    }

    public static int countOwnedMalghasts(AzazelHumanEntity azazel) {
        try {
            Class<?> magic = magicType();
            if (magic == null) return 0;
            return (int) magic.getMethod("countOwnedMalghasts", AzazelHumanEntity.class)
                    .invoke(null, azazel);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 0;
        }
    }

    public static boolean isOwnedByAzazel(AzazelHumanEntity azazel, Entity entity) {
        try {
            Class<?> magic = magicType();
            if (magic == null) return false;
            return (boolean) magic.getMethod("isOwnedByAzazel", AzazelHumanEntity.class, Entity.class)
                    .invoke(null, azazel, entity);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean isGoetyOwnedEntity(Entity entity) {
        try {
            Class<?> magic = magicType();
            if (magic == null) return false;
            return (boolean) magic.getMethod("isGoetyOwnedEntity", Entity.class).invoke(null, entity);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static void invoke(String method, Object... args) {
        try {
            Class<?> magic = magicType();
            if (magic == null) return;
            Class<?>[] parameterTypes = switch (method) {
                case "summonNetherServant" -> new Class<?>[]{AzazelHumanEntity.class, LivingEntity.class, int.class};
                case "castGoetyHellBlast" -> new Class<?>[]{AzazelHumanEntity.class, LivingEntity.class, int.class};
                case "prepareSpell" -> new Class<?>[]{AzazelHumanEntity.class, boolean.class};
                default -> new Class<?>[]{AzazelHumanEntity.class, LivingEntity.class};
            };
            magic.getMethod(method, parameterTypes).invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Goety is optional; NetherExp's Azazel compatibility still works without it.
        }
    }

    private static Class<?> magicType() {
        if (!magicLookupAttempted) {
            synchronized (AzazelGoetyBridge.class) {
                if (!magicLookupAttempted) {
                    try {
                        magicType = Class.forName(MAGIC_CLASS);
                    } catch (ClassNotFoundException | LinkageError ignored) {
                        magicType = null;
                    } finally {
                        magicLookupAttempted = true;
                    }
                }
            }
        }
        return magicType;
    }
}
