package com.smsfr.killaura;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class KillauraConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static KillauraConfig instance;

	public double aimSpeedDegrees = 40.0;
	public double attackReach = 3.5;
	public boolean critOnly = false;
	public boolean silentRotation = false;
	public boolean targetMonsters = true;
	public boolean targetCreatures = false;
	public boolean targetWaterMobs = false;
	public boolean targetOthers = false;
	public boolean targetPlayers = false;
	public boolean skipNamedEntities = true;
	public String toggleKeyName = "key.keyboard.g";
	public String menuKeyName = "key.keyboard.k";

	public static KillauraConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("killaura.json");
		if (Files.exists(path)) {
			try {
				instance = GSON.fromJson(Files.readString(path), KillauraConfig.class);
			} catch (Exception e) {
				Killaura.LOGGER.warn("Could not parse killaura.json, using defaults", e);
			}
		}
		if (instance == null) {
			instance = new KillauraConfig();
		}
		save();
	}

	public static void save() {
		if (instance == null) {
			return;
		}
		try {
			Path path = FabricLoader.getInstance().getConfigDir().resolve("killaura.json");
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(instance));
		} catch (IOException e) {
			Killaura.LOGGER.warn("Could not save killaura.json", e);
		}
	}
}
