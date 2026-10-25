package com.smsfr.killaura.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.smsfr.killaura.KillauraConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class KillauraClient implements ClientModInitializer {
	public static KeyMapping toggleKey;
	public static KeyMapping menuKey;
	public static boolean enabled = false;

	@Override
	public void onInitializeClient() {
		KillauraConfig cfg = KillauraConfig.get();
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("killaura", "main"));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.killaura.toggle", InputConstants.KEY_G, category));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.killaura.menu", InputConstants.KEY_K, category));
		applySavedBind(toggleKey, cfg.toggleKeyName);
		applySavedBind(menuKey, cfg.menuKeyName);

		ClientTickEvents.START_CLIENT_TICK.register(KillauraClient::onStartTick);
	}

	private static void onStartTick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			enabled = !enabled;
		}
		while (menuKey.consumeClick()) {
			client.gui.setScreen(new KillauraScreen());
		}
		if (enabled) {
			AuraRunner.tick(client);
		}
	}

	public static void applySavedBind(KeyMapping mapping, String keyName) {
		try {
			mapping.setKey(InputConstants.getKey(keyName));
		} catch (Exception e) {
			// unknown key name in the config, keep the default bind
		}
	}
}
