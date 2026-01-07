package kassuk.addon.blackout.mixins;

import kassuk.addon.blackout.modules.SoundModifier;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayerEntity {
    @Unique
    Entity attackEntity = null;

    @Inject(method = "attack", at = @At(value = "HEAD"))
    private void inject(Entity target, CallbackInfo ci) {
        attackEntity = target;
    }

    @Redirect(
        method = "attack",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;playSound(Lnet/minecraft/entity/Entity;DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"
        ),
        require = 0
    )
    private void modifyAttackSound(World instance, Entity source, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        SoundModifier m = Modules.get().get(SoundModifier.class);

        if (m.isActive() && m.crystalHits.get() && attackEntity instanceof EndCrystalEntity) {
            instance.playSound(source, x, y, z, sound, category, (float)(volume * m.crystalHitVolume.get()), (float)(pitch * m.crystalHitPitch.get()));
        } else {
            instance.playSound(source, x, y, z, sound, category, volume, pitch);
        }
    }
}
