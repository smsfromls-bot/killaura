package com.smsfr.killaura.client;

import com.smsfr.killaura.KillauraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Runs once per client tick while the aura is enabled: keeps one target and
 * rotates towards it (visibly, or "silently" — rotation is only sent to the
 * server around the attack). Attacks fire the same way a vanilla left click
 * does, optionally waiting for a full-cooldown falling crit.
 */
public final class AuraRunner {
	/** Rotation must be within this many degrees of the target before attacking. */
	private static final double AIM_TOLERANCE_DEGREES = 6.0;
	private static final float ATTACK_STRENGTH_THRESHOLD = 0.9F;
	private static final float CRIT_STRENGTH_THRESHOLD = 0.999F;

	private static LivingEntity target;
	private static float virtualYaw;
	private static float virtualPitch;

	private AuraRunner() {
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		KillauraConfig cfg = KillauraConfig.get();
		if (player == null || level == null || client.gameMode == null || cfg == null || !player.isAlive()) {
			return;
		}

		LivingEntity previousTarget = target;
		if (!isValidTarget(target, player, cfg)) {
			target = findTarget(player, level, cfg);
		}
		if (target == null) {
			return;
		}
		if (target != previousTarget) {
			// aim starts from wherever the player is currently looking
			virtualYaw = player.getYRot();
			virtualPitch = player.getXRot();
		}

		Vec3 aimPoint = target.getBoundingBox().getCenter();
		double dx = aimPoint.x - player.getX();
		double dy = aimPoint.y - player.getEyeY();
		double dz = aimPoint.z - player.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float desiredYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float desiredPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));

		float speed = (float) cfg.aimSpeedDegrees;
		virtualYaw += Mth.clamp(Mth.wrapDegrees(desiredYaw - virtualYaw), -speed, speed);
		virtualPitch = Mth.clamp(virtualPitch + Mth.clamp(desiredPitch - virtualPitch, -speed, speed), -90.0F, 90.0F);

		if (!cfg.silentRotation) {
			player.setYRot(virtualYaw);
			player.setXRot(virtualPitch);
		}

		double distSq = target.getBoundingBox().distanceToSqr(player.getEyePosition());
		double reachSq = cfg.attackReach * cfg.attackReach;
		Vec3 direction = new Vec3(dx, dy, dz).normalize();
		Vec3 virtualLook = rotationVec(virtualYaw, virtualPitch);
		double cosAngle = Mth.clamp(virtualLook.dot(direction), -1.0, 1.0);
		double angleDegrees = Math.toDegrees(Math.acos(cosAngle));

		boolean strengthReady = player.getAttackStrengthScale(0.5F) >= (cfg.critOnly ? CRIT_STRENGTH_THRESHOLD : ATTACK_STRENGTH_THRESHOLD);
		boolean critReady = !cfg.critOnly || isFallingForCrit(player);

		if (distSq <= reachSq
				&& angleDegrees <= AIM_TOLERANCE_DEGREES
				&& strengthReady
				&& critReady
				&& hasLineOfSight(player, target)) {
			attack(client, player, target, cfg, virtualYaw, virtualPitch);
		}
	}

	private static void attack(Minecraft client, LocalPlayer player, LivingEntity target, KillauraConfig cfg,
							   float yaw, float pitch) {
		if (cfg.silentRotation) {
			// server-side snap to the aim, attack, snap back — camera never moves
			player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision));
		}
		client.gameMode.attack(player, target);
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		player.swing(InteractionHand.MAIN_HAND, held.getAttackAnimation(), false);
		player.connection.send(ServerboundPunchPacket.INSTANCE);
		if (cfg.silentRotation) {
			player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), player.getXRot(), player.onGround(), player.horizontalCollision));
		}
	}

	/** Vanilla critical hit conditions on the attacker's side. */
	private static boolean isFallingForCrit(LocalPlayer player) {
		return !player.onGround()
				&& player.fallDistance > 0.0
				&& player.getDeltaMovement().y < 0.0
				&& !player.isInWater()
				&& !player.isPassenger();
	}

	private static Vec3 rotationVec(float yaw, float pitch) {
		float yawRad = yaw * ((float) Math.PI / 180.0F);
		float pitchRad = pitch * ((float) Math.PI / 180.0F);
		return new Vec3(
				-Math.sin(yawRad) * Math.cos(pitchRad),
				-Math.sin(pitchRad),
				Math.cos(yawRad) * Math.cos(pitchRad));
	}

	private static LivingEntity findTarget(LocalPlayer player, ClientLevel level, KillauraConfig cfg) {
		Vec3 eyes = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double reachSq = cfg.attackReach * cfg.attackReach;
		AABB searchBox = player.getBoundingBox().inflate(cfg.attackReach);

		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;

		for (Entity entity : level.getEntities(player, searchBox, e -> e instanceof LivingEntity)) {
			LivingEntity living = (LivingEntity) entity;
			if (!isValidTarget(living, player, cfg)) {
				continue;
			}
			if (living.getBoundingBox().distanceToSqr(eyes) > reachSq) {
				continue;
			}
			if (!hasLineOfSight(player, living)) {
				continue;
			}

			Vec3 center = living.getBoundingBox().getCenter();
			double distance = center.distanceTo(eyes);
			Vec3 toTarget = center.subtract(eyes).normalize();
			double cosAngle = Mth.clamp(look.dot(toTarget), -1.0, 1.0);
			double angle = Math.toDegrees(Math.acos(cosAngle));
			double score = angle + distance * 4.0;
			if (score < bestScore) {
				bestScore = score;
				best = living;
			}
		}
		return best;
	}

	private static boolean isValidTarget(LivingEntity living, LocalPlayer player, KillauraConfig cfg) {
		if (living == null || living.isRemoved() || !living.isAlive() || living instanceof ArmorStand) {
			return false;
		}
		if (living == player || living.distanceToSqr(player) > cfg.attackReach * cfg.attackReach * 4.0) {
			return false;
		}
		return matchesConfig(living, cfg);
	}

	private static boolean matchesConfig(LivingEntity living, KillauraConfig cfg) {
		if (living instanceof Player) {
			return cfg.targetPlayers;
		}
		if (cfg.skipNamedEntities && living.hasCustomName()) {
			return false;
		}
		MobCategory category = living.getType().getCategory();
		return switch (category) {
			case MONSTER -> cfg.targetMonsters;
			case CREATURE, AMBIENT -> cfg.targetCreatures;
			case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> cfg.targetWaterMobs;
			case MISC -> cfg.targetOthers;
		};
	}

	private static boolean hasLineOfSight(LocalPlayer player, LivingEntity target) {
		Vec3 from = player.getEyePosition();
		Vec3[] destinations = {target.getEyePosition(), target.getBoundingBox().getCenter()};
		for (Vec3 to : destinations) {
			BlockHitResult hit = player.level().clip(new ClipContext(
					from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (hit.getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}
}
