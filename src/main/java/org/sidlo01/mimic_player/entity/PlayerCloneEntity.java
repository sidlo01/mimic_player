package org.sidlo01.mimic_player.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * A simple living entity that is *rendered* like a player.
 *
 * Important: this entity is NOT a real player and does not have an inventory,
 * permissions, etc. It's just a mob that uses a player skin client-side.
 */
public class PlayerCloneEntity extends PathfinderMob {
    // We sync the profile name + UUID to clients, so they can fetch the right skin.
    private static final EntityDataAccessor<String> DATA_PROFILE_NAME =
            SynchedEntityData.defineId(PlayerCloneEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Optional<UUID>> DATA_PROFILE_UUID =
            SynchedEntityData.defineId(PlayerCloneEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public PlayerCloneEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    /**
     * Default attributes so the entity exists as a normal living entity.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PROFILE_NAME, "Steve");
        this.entityData.define(DATA_PROFILE_UUID, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        // Tiny bit of "life" so it's not a statue. Remove if you want a totally static NPC.
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    /**
     * Sets the visual identity for this clone.
     *
     * Call this on the SERVER right after spawning. The name/uuid is synced automatically.
     */
    public void setProfile(GameProfile profile) {
        // Store name/uuid in synced entity data.
        this.entityData.set(DATA_PROFILE_NAME, profile.getName() == null ? "Steve" : profile.getName());
        this.entityData.set(DATA_PROFILE_UUID, Optional.ofNullable(profile.getId()));

        // Optional: also use the name as the entity's custom name (shown on hover if enabled).
        this.setCustomName(Component.literal(getProfileName()));
        this.setCustomNameVisible(false);
    }

    public String getProfileName() {
        return this.entityData.get(DATA_PROFILE_NAME);
    }

    @Nullable
    public UUID getProfileUUID() {
        return this.entityData.get(DATA_PROFILE_UUID).orElse(null);
    }

    /**
     * Builds a GameProfile on demand. Client renderer uses this to ask Minecraft for the skin texture.
     */
    public GameProfile getGameProfile() {
        UUID id = getProfileUUID();
        String name = getProfileName();

        if (id == null) {
            // If UUID is missing (e.g., config name not resolvable), create an "offline" UUID.
            // This makes default skins stable (Steve/Alex choice) per-name.
            id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        }
        return new GameProfile(id, name);
    }

    // ---------- Persistence ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ProfileName", getProfileName());
        UUID id = getProfileUUID();
        if (id != null) {
            tag.putUUID("ProfileUUID", id);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String name = tag.getString("ProfileName");
        UUID id = tag.hasUUID("ProfileUUID") ? tag.getUUID("ProfileUUID") : null;
        this.entityData.set(DATA_PROFILE_NAME, name.isBlank() ? "Steve" : name);
        this.entityData.set(DATA_PROFILE_UUID, Optional.ofNullable(id));

        // Keep custom name in sync after load.
        this.setCustomName(Component.literal(getProfileName()));
        this.setCustomNameVisible(false);
    }

    // ---------- Behavior tweaks (optional) ----------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Make clones "tanky" to accidental hits? Uncomment if desired:
        // amount *= 0.5f;
        return super.hurt(source, amount);
    }
}
