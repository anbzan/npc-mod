package com.example.npcmod.client.gui.widgets;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import com.example.npcmod.client.gui.NPCGuiDataManager;
import net.minecraft.nbt.CompoundTag;

/**
 * 事件触发状态指示器组件
 */
public class EventIndicatorWidget extends AbstractWidget {
    private final String npcId;
    private final Player player;
    
    public EventIndicatorWidget(int x, int y, int width, int height, String npcId, Player player) {
        super(x, y, width, height, Component.empty());
        this.npcId = npcId;
        this.player = player;
    }
    
    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        CompoundTag npcData = NPCGuiDataManager.getInstance().getNPCData(npcId);
        
        // 渲染标题
        this.minecraft.font.draw(poseStack, "事件触发状态", this.x, this.y, 0xFFFFFF);
        
        int yPos = this.y + 12;
        
        // 好感度触发条件
        boolean affectionTrigger = false;
        if (npcData.contains("affection_score")) {
            float affection = npcData.getFloat("affection_score");
            // 假设好感度达到50以上可以触发某些事件
            affectionTrigger = affection >= 50.0f;
        }
        
        String affectionStatus = "好感度触发: " + (affectionTrigger ? "✓ 可触发" : "✗ 未满足");
        int affectionColor = affectionTrigger ? 0x44FF44 : 0xFF4444;
        this.minecraft.font.draw(poseStack, affectionStatus, this.x, yPos, affectionColor);
        yPos += 12;
        
        // 任务完成触发条件
        boolean taskCompleteTrigger = false;
        if (npcData.contains("completed_count")) {
            int completed = npcData.getInt("completed_count");
            // 假设完成5个任务以上可以触发事件
            taskCompleteTrigger = completed >= 5;
        }
        
        String taskStatus = "任务完成触发: " + (taskCompleteTrigger ? "✓ 可触发" : "✗ 未满足");
        int taskColor = taskCompleteTrigger ? 0x44FF44 : 0xFF4444;
        this.minecraft.font.draw(poseStack, taskStatus, this.x, yPos, taskColor);
        yPos += 12;
        
        // 时间触发条件（基于游戏时间）
        boolean timeTrigger = false;
        if (player.level() != null) {
            long gameTime = player.level().getGameTime();
            // 假设在特定游戏时间可以触发事件（例如夜晚）
            long dayTime = gameTime % 24000L;
            timeTrigger = (dayTime >= 12000 && dayTime <= 23000); // 夜晚时间
        }
        
        String timeStatus = "时间触发: " + (timeTrigger ? "✓ 可触发" : "✗ 未满足");
        int timeColor = timeTrigger ? 0x44FF44 : 0xFF4444;
        this.minecraft.font.draw(poseStack, timeStatus, this.x, yPos, timeColor);
        yPos += 12;
        
        // 脚本事件触发状态
        if (npcData.contains("active_scripts")) {
            int activeScripts = npcData.getInt("active_scripts");
            String scriptStatus = "活动脚本: " + activeScripts + " 个";
            this.minecraft.font.draw(poseStack, scriptStatus, this.x, yPos, 0xAAAAFF);
        }
    }
    
    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
        // 空实现
    }
}