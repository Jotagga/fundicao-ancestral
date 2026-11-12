package dev.ancientfoundry.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.ancientfoundry.AncientFoundry;
import dev.ancientfoundry.logic.FoundryCore;
import dev.ancientfoundry.menu.FoundryMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Screen of the foundry controller. The background texture is 176x200; the animated pieces
 * (heat column, flame, arrow) sit to the right of it in the same 256x256 sheet.
 */
public class FoundryScreen extends AbstractContainerScreen<FoundryMenu> {
	private static final ResourceLocation TEXTURE = AncientFoundry.id("textures/gui/foundry.png");

	// Heat column.
	private static final int HEAT_X = 12;
	private static final int HEAT_Y = 20;
	private static final int HEAT_WIDTH = 8;
	private static final int HEAT_HEIGHT = 50;
	private static final int HEAT_U = 176;
	private static final int HEAT_V = 0;

	// Flame between the inputs and the fuel slot.
	private static final int FLAME_X = 56;
	private static final int FLAME_Y = 40;
	private static final int FLAME_SIZE = 14;
	private static final int FLAME_U = 176;
	private static final int FLAME_V = 52;

	// Progress arrow.
	private static final int ARROW_X = 92;
	private static final int ARROW_Y = 38;
	private static final int ARROW_WIDTH = 24;
	private static final int ARROW_HEIGHT = 17;
	private static final int ARROW_U = 176;
	private static final int ARROW_V = 68;

	// Read-out panel.
	private static final int PANEL_X = 7;
	private static final int PANEL_Y = 79;
	private static final int PANEL_WIDTH = 162;
	private static final int PANEL_HEIGHT = 25;
	private static final int LINE_1_Y = 83;
	private static final int LINE_2_Y = 93;

	/** Hint shown over each empty machine slot, indexed like the menu's first five slots. */
	private static final String[] SLOT_HINTS = {
			"gui.ancient_foundry.slot.rock",
			"gui.ancient_foundry.slot.soul",
			"gui.ancient_foundry.slot.fuel",
			"gui.ancient_foundry.slot.output",
			"gui.ancient_foundry.slot.byproduct"
	};

	private static final int COLOR_LABEL = 0x404040;
	private static final int COLOR_PANEL_TEXT = 0xD8D8D8;
	private static final int COLOR_GOOD = 0x6BF0E6;
	private static final int COLOR_WARM = 0xFFB347;
	private static final int COLOR_BAD = 0xFF6B6B;
	private static final int COLOR_IDLE = 0xA0A0A0;

	public FoundryScreen(FoundryMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 176;
		this.imageHeight = 200;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		this.renderTooltip(graphics, mouseX, mouseY);
		this.renderGaugeTooltips(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int left = this.leftPos;
		int top = this.topPos;
		graphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight);

		// Heat column fills from the bottom.
		int heat = scaled(this.menu.heatPermille(), HEAT_HEIGHT);

		if (heat > 0) {
			graphics.blit(TEXTURE, left + HEAT_X, top + HEAT_Y + HEAT_HEIGHT - heat, HEAT_U, HEAT_V + HEAT_HEIGHT - heat, HEAT_WIDTH, heat);
		}

		// Mark of the working temperature across the column.
		int mark = top + HEAT_Y + HEAT_HEIGHT - scaled(this.menu.workHeatPermille(), HEAT_HEIGHT);
		graphics.fill(left + HEAT_X - 2, mark, left + HEAT_X + HEAT_WIDTH + 2, mark + 1, 0xFFFFFFFF);

		// Flame shrinks from the top as the fuel item burns away.
		int burn = scaled(this.menu.burnPermille(), FLAME_SIZE);

		if (burn > 0) {
			graphics.blit(TEXTURE, left + FLAME_X, top + FLAME_Y + FLAME_SIZE - burn, FLAME_U, FLAME_V + FLAME_SIZE - burn, FLAME_SIZE, burn);
		}

		// Arrow fills from the left with the cycle.
		int progress = scaled(this.menu.progressPermille(), ARROW_WIDTH);

		if (progress > 0) {
			graphics.blit(TEXTURE, left + ARROW_X, top + ARROW_Y, ARROW_U, ARROW_V, progress, ARROW_HEIGHT);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, COLOR_LABEL, false);
		graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, COLOR_LABEL, false);

		FoundryCore.Status status = this.menu.status();
		Component statusText = Component.translatable("gui.ancient_foundry.status." + status.name().toLowerCase(Locale.ROOT));
		graphics.drawString(this.font, statusText, PANEL_X + 4, LINE_1_Y, statusColor(status), false);

		Component temperature = Component.translatable("gui.ancient_foundry.temperature", this.menu.temperature());
		graphics.drawString(this.font, temperature, PANEL_X + PANEL_WIDTH - 4 - this.font.width(temperature), LINE_1_Y, COLOR_PANEL_TEXT, false);

		Component window = Component.translatable("gui.ancient_foundry.window", this.menu.windowCycle(), this.menu.windowSize());
		graphics.drawString(this.font, window, PANEL_X + 4, LINE_2_Y, COLOR_PANEL_TEXT, false);

		Component total = Component.translatable("gui.ancient_foundry.total", this.menu.totalScraps());
		graphics.drawString(this.font, total, PANEL_X + PANEL_WIDTH - 4 - this.font.width(total), LINE_2_Y, COLOR_PANEL_TEXT, false);
	}

	private void renderGaugeTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
		Slot hovered = this.hoveredSlot;

		if (hovered != null && hovered.index < SLOT_HINTS.length && !hovered.hasItem() && this.menu.getCarried().isEmpty()) {
			// An empty machine slot explains what it takes.
			graphics.renderTooltip(this.font, Component.translatable(SLOT_HINTS[hovered.index]), mouseX, mouseY);
		} else if (this.isHovering(HEAT_X - 2, HEAT_Y - 2, HEAT_WIDTH + 4, HEAT_HEIGHT + 4, mouseX, mouseY)) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable("gui.ancient_foundry.tooltip.heat", this.menu.temperature()));
			lines.add(Component.translatable("gui.ancient_foundry.tooltip.heat_needed").withStyle(ChatFormatting.GRAY));
			graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
		} else if (this.isHovering(ARROW_X, ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT, mouseX, mouseY)) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable("gui.ancient_foundry.tooltip.cycle", this.menu.progressPermille() / 10));
			lines.add(Component.translatable("gui.ancient_foundry.tooltip.cycle_cost").withStyle(ChatFormatting.GRAY));
			graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
		} else if (this.isHovering(FLAME_X, FLAME_Y, FLAME_SIZE, FLAME_SIZE, mouseX, mouseY)) {
			graphics.renderTooltip(this.font, Component.translatable("gui.ancient_foundry.tooltip.fuel", this.menu.burnPermille() / 10), mouseX, mouseY);
		} else if (this.isHovering(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, mouseX, mouseY)) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable("gui.ancient_foundry.tooltip.window", this.menu.windowSize()));
			lines.add(Component.translatable(this.menu.windowScrapFound()
					? "gui.ancient_foundry.tooltip.window_found"
					: "gui.ancient_foundry.tooltip.window_pending").withStyle(ChatFormatting.GRAY));
			int minutes = this.menu.activeMinutes();
			lines.add(Component.translatable("gui.ancient_foundry.tooltip.active", minutes / 60, minutes % 60).withStyle(ChatFormatting.GRAY));
			graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
		}
	}

	private static int scaled(int permille, int size) {
		if (permille <= 0) {
			return 0;
		}

		return Math.max(1, Math.min(size, permille * size / 1000));
	}

	private static int statusColor(FoundryCore.Status status) {
		return switch (status) {
			case WORKING -> COLOR_GOOD;
			case HEATING -> COLOR_WARM;
			case PAUSED -> COLOR_IDLE;
			case UNFORMED, NO_INPUT, OUTPUT_FULL, NO_FUEL -> COLOR_BAD;
		};
	}
}
