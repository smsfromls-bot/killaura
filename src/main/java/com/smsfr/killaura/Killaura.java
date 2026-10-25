package com.smsfr.killaura;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Killaura implements ModInitializer {
	public static final String MOD_ID = "killaura";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		KillauraConfig.load();
	}
}
