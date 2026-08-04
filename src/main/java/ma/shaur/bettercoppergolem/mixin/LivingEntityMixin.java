package ma.shaur.bettercoppergolem.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.CopperGolem;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	
	@Inject(method = "doPush", at = @At("HEAD"))
	public void onPushLivingEntity(Entity entity, CallbackInfo ci) {
		if (entity instanceof CopperGolem && (Object) this instanceof CopperGolem thisGolem) {
			// Prevent two golems from getting deadlocked by randomly stopping one's pathfinding when they collide.
			if (thisGolem.getRandom().nextInt(20) == 0) {
				thisGolem.getNavigation().stop();
			} else if (thisGolem.getRandom().nextInt(20) == 0) {
				// Random slight lateral push to help them slide past each other in wider areas
				double nudgeX = (thisGolem.getRandom().nextDouble() - 0.5) * 0.5;
				double nudgeZ = (thisGolem.getRandom().nextDouble() - 0.5) * 0.5;
				thisGolem.push(nudgeX, 0, nudgeZ);
			}
		}
	}
}
