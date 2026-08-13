package com.enderdragonanthro.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Search a block by name and pick it from the list.
 *
 * Replaces looking at one and hoping: aiming at a block to name a quarry meant
 * the thing you wanted had to be in front of you and in reach, which for
 * "diamond ore" is a problem. The registry is right here on the client, so it
 * may as well be searchable.
 */
public class BlockPickerScreen extends Screen {
    private static final int ROWS = 8;
    private static final int ROW_H = 20;
    private static final int PANEL_W = 260;

    private final Screen parent;
    private final String initial;
    private final Consumer<String> onPick;
    private final List<ResourceLocation> matches = new ArrayList<>();

    private EditBox search;
    private int listTop;

    public BlockPickerScreen(Screen parent, String initial, Consumer<String> onPick) {
        super(Component.literal("Name a quarry"));
        this.parent = parent;
        this.initial = initial;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_W) / 2;
        int top = Math.max(30, (this.height - (ROWS + 3) * ROW_H) / 2);
        this.listTop = top + 26;

        this.search = new EditBox(this.font, left, top, PANEL_W, 18,
                Component.literal("search"));
        this.search.setMaxLength(64);
        this.search.setHint(Component.literal("type to search — diamond, deepslate, debris…"));
        this.search.setValue(this.initial == null ? "" : this.initial);
        this.search.setResponder(v -> refresh(v));
        this.addRenderableWidget(this.search);
        this.setInitialFocus(this.search);

        refresh(this.search.getValue());

        this.addRenderableWidget(Button.builder(Component.literal("Any block"),
                        b -> pick(""))
                .bounds(left, this.listTop + ROWS * ROW_H + 4, PANEL_W / 2 - 2, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"),
                        b -> this.minecraft.setScreen(this.parent))
                .bounds(left + PANEL_W / 2 + 2, this.listTop + ROWS * ROW_H + 4,
                        PANEL_W / 2 - 2, 20).build());
    }

    /**
     * Rebuild the match list. Blocks with no item cannot be carried home, so
     * a shade could never deliver them — they are left out rather than offered
     * and then quietly failed on.
     */
    private void refresh(String query) {
        this.matches.clear();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == Blocks.AIR || block.asItem() == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            if (needle.isEmpty() || key.getPath().contains(needle)
                    || block.getName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                this.matches.add(key);
            }
            if (this.matches.size() >= 512) {
                break;
            }
        }
        // shortest path first, so "diamond_ore" outranks "deepslate_diamond_ore"
        this.matches.sort((a, b) -> {
            int byLength = Integer.compare(a.getPath().length(), b.getPath().length());
            return byLength != 0 ? byLength : a.getPath().compareTo(b.getPath());
        });
        rebuildRows();
    }

    private void rebuildRows() {
        this.clearWidgets();
        this.addRenderableWidget(this.search);
        int left = (this.width - PANEL_W) / 2;
        for (int i = 0; i < Math.min(ROWS, this.matches.size()); i++) {
            ResourceLocation key = this.matches.get(i);
            this.addRenderableWidget(Button.builder(
                            Component.literal(key.getPath()), b -> pick(key.toString()))
                    .bounds(left, this.listTop + i * ROW_H, PANEL_W, 18).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Any block"), b -> pick(""))
                .bounds(left, this.listTop + ROWS * ROW_H + 4, PANEL_W / 2 - 2, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"),
                        b -> this.minecraft.setScreen(this.parent))
                .bounds(left + PANEL_W / 2 + 2, this.listTop + ROWS * ROW_H + 4,
                        PANEL_W / 2 - 2, 20).build());
    }

    private void pick(String id) {
        this.onPick.accept(id);
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFE079FA);
        if (this.matches.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal("nothing by that name"),
                    this.width / 2, this.listTop + 6, 0xFF9A93A5);
        } else if (this.matches.size() > ROWS) {
            graphics.drawCenteredString(this.font,
                    Component.literal((this.matches.size() - ROWS) + " more — keep typing"),
                    this.width / 2, this.listTop + ROWS * ROW_H + 30, 0xFF7A7A85);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
