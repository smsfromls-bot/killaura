package com.smsfr.killaura.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.smsfr.killaura.KillauraConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class KillauraScreen extends Screen {
	private static final int WIDGET_WIDTH = 300;
	private static final double MIN_SPEED = 5.0;
	private static final double MAX_SPEED = 180.0;
	private static final double MIN_REACH = 1.0;
	private static final double MAX_REACH = 6.0;

	private Button toggleButton;
	private Button toggleBindButton;
	private Button menuBindButton;
	private int listening = -1; // 0 = toggle key, 1 = menu key

	public KillauraScreen() {
		super(Component.literal("Killaura"));
	}

	@Override
	protected void init() {
		KillauraConfig cfg = KillauraConfig.get();
		int x = this.width / 2 - WIDGET_WIDTH / 2;
		int y = 50;

		toggleButton = addRenderableWidget(Button.builder(toggleLabel(), button -> {
			KillauraClient.enabled = !KillauraClient.enabled;
			button.setMessage(toggleLabel());
		}).bounds(x, y, WIDGET_WIDTH, 20).build());
		y += 28;

		addRenderableWidget(new DoubleSlider(x, y, WIDGET_WIDTH, 20,
				"Скорость наводки", cfg.aimSpeedDegrees, MIN_SPEED, MAX_SPEED, false,
				value -> cfg.aimSpeedDegrees = value));
		y += 26;

		addRenderableWidget(new DoubleSlider(x, y, WIDGET_WIDTH, 20,
				"Дальность атаки", cfg.attackReach, MIN_REACH, MAX_REACH, true,
				value -> cfg.attackReach = value));
		y += 30;

		addRenderableWidget(addCheckbox(x, y, "Враждебные мобы", cfg.targetMonsters, v -> cfg.targetMonsters = v));
		y += 24;
		addRenderableWidget(addCheckbox(x, y, "Мирные мобы", cfg.targetCreatures, v -> cfg.targetCreatures = v));
		y += 24;
		addRenderableWidget(addCheckbox(x, y, "Водные мобы", cfg.targetWaterMobs, v -> cfg.targetWaterMobs = v));
		y += 24;
		addRenderableWidget(addCheckbox(x, y, "Прочие существа", cfg.targetOthers, v -> cfg.targetOthers = v));
		y += 24;
		addRenderableWidget(addCheckbox(x, y, "Игроки", cfg.targetPlayers, v -> cfg.targetPlayers = v));
		y += 24;
		addRenderableWidget(addCheckbox(x, y, "Не бить переименованных", cfg.skipNamedEntities, v -> cfg.skipNamedEntities = v));
		y += 24;
		addRenderableWidget(addCheckbox(x, y, "Бить только критами (в падении)", cfg.critOnly, v -> cfg.critOnly = v));
		y += 24;
		addRenderableWidget(addCheckbox(x, y, "Сайлент-ротация (не дёргать камеру)", cfg.silentRotation, v -> cfg.silentRotation = v));
		y += 30;

		toggleBindButton = addRenderableWidget(Button.builder(bindLabel(0), button -> {
			listening = 0;
			refreshBindLabels();
		}).bounds(x, y, 145, 20).build());
		menuBindButton = addRenderableWidget(Button.builder(bindLabel(1), button -> {
			listening = 1;
			refreshBindLabels();
		}).bounds(x + 155, y, 145, 20).build());
		y += 28;

		addRenderableWidget(Button.builder(Component.literal("Готово"), button -> onClose())
				.bounds(x, y, WIDGET_WIDTH, 20).build());
	}

	private Checkbox addCheckbox(int x, int y, String label, boolean selected, Consumer<Boolean> setter) {
		return Checkbox.builder(Component.literal(label), this.font)
				.pos(x, y)
				.selected(selected)
				.onValueChange((checkbox, value) -> {
					setter.accept(value);
					KillauraConfig.save();
				})
				.build();
	}

	private Component toggleLabel() {
		return Component.literal(KillauraClient.enabled ? "Состояние: ВКЛ (нажми, чтобы выключить)" : "Состояние: ВЫКЛ (нажми, чтобы включить)");
	}

	private Component bindLabel(int slot) {
		String name = slot == 0
				? KillauraClient.toggleKey.getTranslatedKeyMessage().getString()
				: KillauraClient.menuKey.getTranslatedKeyMessage().getString();
		String prefix = slot == 0 ? "Вкл/выкл" : "Меню";
		if (listening == slot) {
			return Component.literal(prefix + ": > нажми клавишу <");
		}
		return Component.literal(prefix + ": [" + name + "]");
	}

	private void refreshBindLabels() {
		toggleBindButton.setMessage(bindLabel(0));
		menuBindButton.setMessage(bindLabel(1));
	}

	private void applyBind(int slot, InputConstants.Key key) {
		KeyMapping mapping = slot == 0 ? KillauraClient.toggleKey : KillauraClient.menuKey;
		KillauraConfig cfg = KillauraConfig.get();
		mapping.setKey(key);
		if (slot == 0) {
			cfg.toggleKeyName = key.getName();
		} else {
			cfg.menuKeyName = key.getName();
		}
		KillauraConfig.save();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening >= 0) {
			if (event.key() != InputConstants.KEY_ESCAPE) {
				applyBind(listening, InputConstants.getKey(event));
			}
			listening = -1;
			refreshBindLabels();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
		graphics.centeredText(this.font, Component.literal("ESC — закрыть"), this.width / 2, this.height - 20, 0xA0A0A0);
	}

	@Override
	public void removed() {
		KillauraConfig.save();
		super.removed();
	}

	private class DoubleSlider extends AbstractSliderButton {
		private final String label;
		private final double min;
		private final double max;
		private final boolean oneDecimal;
		private final DoubleConsumer setter;

		DoubleSlider(int x, int y, int width, int height, String label, double current,
					 double min, double max, boolean oneDecimal, DoubleConsumer setter) {
			super(x, y, width, height, Component.empty(), (Mth.clamp(current, min, max) - min) / (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.oneDecimal = oneDecimal;
			this.setter = setter;
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			double value = this.min + this.value * (this.max - this.min);
			String formatted = this.oneDecimal
					? String.format(Locale.ROOT, "%.1f", value)
					: String.format(Locale.ROOT, "%.0f", value);
			this.setMessage(Component.literal(this.label + ": " + formatted));
		}

		@Override
		protected void applyValue() {
			this.setter.accept(this.min + this.value * (this.max - this.min));
		}
	}
}
