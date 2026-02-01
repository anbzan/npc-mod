package com.example.npcmod.client.gui.screens;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import com.example.npcmod.client.gui.containers.NPCContainer;
import com.example.npcmod.client.gui.widgets.*;
import net.minecraft.client.Minecraft;

/**
 * NPC主GUI屏幕类
 */
public class NPCGuiScreen extends Screen {
    private final Player player;
    private final String npcId;
    private final NPCContainer container;
    
    // UI组件
    private AffectionDisplayWidget affectionWidget;
    private MemoryDisplayWidget memoryWidget;
    private TaskDisplayWidget taskWidget;
    private EventIndicatorWidget eventWidget;
    private ScriptViewerWidget scriptWidget;
    
    public NPCGuiScreen(Player player, String npcId, NPCContainer container) {
        super(Component.translatable("screen.npcmod.npc_gui"));
        this.player = player;
        this.npcId = npcId;
        this.container = container;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // 初始化UI组件
        int guiLeft = (this.width - 256) / 2;
        int guiTop = (this.height - 180) / 2;
        
        // 好感度显示组件
        this.affectionWidget = new AffectionDisplayWidget(guiLeft + 10, guiTop + 10, 120, 40, npcId, player);
        this.addRenderableWidget(this.affectionWidget);
        
        // 记忆显示组件
        this.memoryWidget = new MemoryDisplayWidget(guiLeft + 140, guiTop + 10, 110, 60, npcId, player);
        this.addRenderableWidget(this.memoryWidget);
        
        // 任务显示组件
        this.taskWidget = new TaskDisplayWidget(guiLeft + 10, guiTop + 60, 240, 80, npcId, player);
        this.addRenderableWidget(this.taskWidget);
        
        // 事件指示器组件
        this.eventWidget = new EventIndicatorWidget(guiLeft + 10, guiTop + 150, 120, 60, npcId, player);
        this.addRenderableWidget(this.eventWidget);
        
        // 脚本查看器组件
        this.scriptWidget = new ScriptViewerWidget(guiLeft + 140, guiTop + 150, 110, 60, npcId, player, this::onScriptTrigger);
        this.addRenderableWidget(this.scriptWidget);
        
        // 关闭按钮
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
            .pos(guiLeft + 256 - 40, guiTop + 180 - 20)
            .size(40, 20)
            .build());
        
        // 触发脚本按钮
        this.addRenderableWidget(Button.builder(Component.translatable("button.npcmod.trigger_script"), button -> this.onScriptTrigger())
            .pos(guiLeft + 140, guiTop + 175)
            .size(110, 15)
            .build());
    }
    
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        
        // 渲染标题
        this.font.draw(poseStack, "NPC交互界面", this.width / 2 - 50, this.height / 2 - 95, 0xFFFFFF);
    }
    
    private void onScriptTrigger() {
        // 触发选中的脚本
        if (this.scriptWidget != null) {
            this.scriptWidget.triggerSelectedScript();
            // 这里可以发送网络包到服务端触发脚本
            // NetworkHandler.sendToServer(new TriggerScriptPacket(npcId));
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}