package com.example.npcmod.client.gui;

import com.example.npcmod.EntityNpc;
import com.example.npcmod.NpcMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GiveItemGui extends Screen {
    private final EntityNpc npc;
    private final Player player;
    private List<ItemButton> itemButtons = new ArrayList<>();
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 10;
    private static final int INVENTORY_SLOTS = 36;

public GiveItemGui(EntityNpc npc, Player player) {
        super(Component.literal("给予物品"));
        this.npc = npc;
        this.player = player;
    }

    @Override
    protected void init() {
        super.init();
        itemButtons.clear();
        loadItemsForPage(currentPage);

        // 分页按钮（如果有多页）
        int totalPages = (int) Math.ceil(getNonEmptyItemCount() / (double) ITEMS_PER_PAGE);
        if (totalPages > 1) {
            if (currentPage > 0) {
                this.addRenderableWidget(Button.builder(Component.literal("上一页"), button -> {
                        currentPage--;
                        loadItemsForPage(currentPage);
                    })
                    .pos(this.width / 2 - 150, this.height - 40)
                    .size(60, 20)
                    .build());
            }
            if (currentPage < totalPages - 1) {
                this.addRenderableWidget(Button.builder(Component.literal("下一页"), button -> {
                        currentPage++;
                        loadItemsForPage(currentPage);
                    })
                    .pos(this.width / 2 + 90, this.height - 40)
                    .size(60, 20)
                    .build());
            }
        }

        // 返回按钮
        this.addRenderableWidget(Button.builder(Component.literal("返回"), button -> {
                this.minecraft.setScreen(new NpcGui(npc, player));
            })
            .pos(this.width / 2 - 100, this.height - 60)
            .size(200, 20)
            .build());
    }

    private void loadItemsForPage(int page) {
        // 清除旧按钮
        this.children().removeIf(widget -> widget instanceof ItemButton);

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, INVENTORY_SLOTS);
        int buttonIndex = 0;

        for (int i = startIndex; i < endIndex && i < INVENTORY_SLOTS; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                int x = this.width / 2 - 100;
                int y = 50 + buttonIndex * 25;
                ItemButton button = new ItemButton(x, y, stack, i, this);
                this.addRenderableWidget(button);
                itemButtons.add(button);
                buttonIndex++;
            }
        }
    }

    private int getNonEmptyItemCount() {
        int count = 0;
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private void giveItemToNpc(int slot) {
        // 发送网络包到服务器
        NpcMod.NETWORK.sendToServer(new NpcMod.GiveItemPacket(npc.getId(), slot));
        
        // 关闭GUI
        this.minecraft.setScreen(new NpcGui(npc, player));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawString(this.font, "选择要给予的物品 (页 " + (currentPage + 1) + ")", this.width / 2 - 80, 20, 0xFFFFFF);
        
        // 渲染物品按钮（图标+文本）
        for (ItemButton button : itemButtons) {
            button.render(guiGraphics, mouseX, mouseY, partialTicks);
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // 修正后的 ItemButton：继承 AbstractButton，手动渲染按钮外观
    private static class ItemButton extends AbstractButton {
        private final ItemStack stack;
        private final int slot;
        private final GiveItemGui parent;

        public ItemButton(int x, int y, ItemStack stack, int slot, GiveItemGui parent) {
            super(x, y, 200, 20, Component.literal(stack.getDisplayName().getString() + " x" + stack.getCount()));
            this.stack = stack;
            this.slot = slot;
            this.parent = parent;
            this.active = true;
        }

        @Override
        public void onPress() {
            parent.giveItemToNpc(this.slot);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
            // 手动绘制按钮背景（根据悬停状态）
            int bgColor = this.isHoveredOrFocused() ? 0xAAFFAA80 : 0xAAAAAAAA;  // 半透明绿色/灰色
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), bgColor);

            // 绘制按钮边框
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + 1, 0xFF000000);  // 上边
            guiGraphics.fill(this.getX(), this.getY() + this.getHeight() - 1, this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xFF000000);  // 下边
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.getHeight(), 0xFF000000);  // 左边
            guiGraphics.fill(this.getX() + this.getWidth() - 1, this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xFF000000);  // 右边

            // 绘制文本（从 x=22 开始，避免物品图标重叠）
            int textColor = this.isHoveredOrFocused() ? 0xFFFFFF : 0x000000;
            guiGraphics.drawString(parent.font, this.getMessage(), this.getX() + 22, this.getY() + (this.getHeight() - parent.font.lineHeight) / 2, textColor);

            // 绘制物品图标（使用正确的 ItemRenderer 方法）
            int iconX = this.getX() + 2;
            int iconY = this.getY() + 2;
guiGraphics.renderItem(this.stack, iconX, iconY);
        }

        public void renderToolTip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
            if (this.isHoveredOrFocused()) {
                List<Component> tooltip = List.of(
                    Component.literal("槽位: " + slot), 
                    Component.literal("给予1个 " + stack.getHoverName().getString())
                );
                guiGraphics.renderComponentTooltip(parent.font, tooltip, mouseX, mouseY);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }
}