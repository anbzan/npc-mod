package com.example.npcmod.client.gui;

import com.example.npcmod.EntityNpc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class NpcGui extends Screen {
    private final EntityNpc npc;
    private final Player player;
    
    public NpcGui(EntityNpc npc, Player player) {
        super(Component.translatable("npc.gui.title"));  // i18n支持
        this.npc = npc;
        this.player = player;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 200;
        int buttonHeight = 20;
        int startY = this.height / 2 - 40;
        int centerX = this.width / 2 - buttonWidth / 2;
        
        // 添加"给予物品"按钮 (Builder)
        this.addRenderableWidget(Button.builder(Component.literal("给予物品"), button -> {
                this.minecraft.setScreen(new GiveItemGui(npc, player));
            })
            .pos(centerX, startY)
            .size(buttonWidth, buttonHeight)
            .build());
        
        // 添加"对话"按钮 (Builder)
        this.addRenderableWidget(Button.builder(Component.literal("对话"), button -> {
                this.minecraft.setScreen(new ChatGui(npc, player));
            })
            .pos(centerX, startY + 25)
            .size(buttonWidth, buttonHeight)
            .build());
        
        // 添加"关闭"按钮 (Builder)
        this.addRenderableWidget(Button.builder(Component.literal("关闭"), button -> {
                this.minecraft.setScreen(null);
            })
            .pos(centerX, startY + 50)
            .size(buttonWidth, buttonHeight)
            .build());
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        
        // 绘制标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        
        // 绘制NPC状态信息（居中下方）
        int infoY = this.height / 2 - 100;
        int infoX = this.width / 2;
        
        // 饥饿值
        int foodLevel = npc.getFoodLevel();
        guiGraphics.drawCenteredString(this.font, Component.literal("饥饿: " + foodLevel + "/20"), infoX, infoY, 0x55FF55);
        
        // 持有物品
        ItemStack heldItem = npc.getHeldItem();
        String itemText = heldItem.isEmpty() ? "无" : heldItem.getDisplayName().getString();
        guiGraphics.drawCenteredString(this.font, Component.literal("持有: " + itemText), infoX, infoY + 15, 0xFFFF55);
        
        // 记忆摘要（可选，缩短显示）
        String memory = npc.getMemory();
        if (!memory.isEmpty()) {
            String shortMemory = memory.length() > 50 ? memory.substring(0, 50) + "..." : memory;
            guiGraphics.drawCenteredString(this.font, Component.literal("记忆: " + shortMemory), infoX, infoY + 30, 0xAAAAAA);
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public void tick() {
        super.tick();
        // 可选：每tick更新状态显示（如果动态变化）
    }
}