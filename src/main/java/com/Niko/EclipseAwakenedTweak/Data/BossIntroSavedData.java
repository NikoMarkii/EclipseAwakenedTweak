package com.Niko.EclipseAwakenedTweak.Data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** World-persistent record of boss entities whose introduction has already played. */
public final class BossIntroSavedData extends SavedData {
    private static final String DATA_NAME = "eclipseawakenedtweak_boss_intros";
    private final Set<UUID> seen = new HashSet<>();

    public static BossIntroSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                BossIntroSavedData::load, BossIntroSavedData::new, DATA_NAME);
    }

    public boolean markSeen(UUID id) {
        boolean added = seen.add(id);
        if (added) setDirty();
        return added;
    }

    private static BossIntroSavedData load(CompoundTag tag) {
        BossIntroSavedData data = new BossIntroSavedData();
        ListTag ids = tag.getList("seen", 8);
        for (int index = 0; index < ids.size(); index++) {
            try { data.seen.add(UUID.fromString(ids.getString(index))); }
            catch (IllegalArgumentException ignored) {}
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag ids = new ListTag();
        seen.forEach(id -> ids.add(StringTag.valueOf(id.toString())));
        tag.put("seen", ids);
        return tag;
    }
}
