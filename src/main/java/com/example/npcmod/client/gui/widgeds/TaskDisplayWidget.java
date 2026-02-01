package com.example.npcmod.client.gui.widgets;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import com.example.npcmod.client.gui.NPCGuiDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * 激活任务列表及进度展示组件
 */
public class TaskDisplayWidget extends AbstractWidget {
    private final String npcId;
    private final Player player;
    
    public TaskDisplayWidget(int x, int y, int width, int height, String npcId, Player player) {
        super(x, y, width, height, Component.empty());
        this.npcId = npcId;
        this.player = player;
    }
    
    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        CompoundTag npcData = NPCGuiDataManager.getInstance().getNPCData(npcId);
        
        // 渲染标题
        this.minecraft.font.draw(poseStack, "激活任务列表", this.x, this.y, 0xFFFFFF);
        
        int yPos = this.y + 12;
        
        // 显示动态行为任务
        if (npcData.contains("active_tasks")) {
            ListTag activeTasks = npcData.getList("active_tasks", 10);
            if (!activeTasks.isEmpty()) {
                this.minecraft.font.draw(poseStack, "行为任务:", this.x, yPos, 0x44AAFF);
                yPos += 10;
                for (int i = 0; i < Math.min(activeTasks.size(), 3); i++) {
                    CompoundTag task = activeTasks.getCompound(i);
                    String title = task.contains("title") ? task.getString("title") : "未知任务";
                    int progress = task.contains("current_progress") ? task.getInt("current_progress") : 0;
                    int required = task.contains("required_progress") ? task.getInt("required_progress") : 100;
                    float progPercent = required > 0 ? (float)progress / required * 100 : 0;
                    
                    String taskText = "• " + truncateText(title, 20) + " " + (int)progPercent + "%";
                    this.minecraft.font.draw(poseStack, taskText, this.x + 5, yPos, 0xAAAAAA);
                    yPos += 10;
                    
                    // 绘制进度条
                    int barWidth = (int)(this.width - 20);
                    int filledWidth = (int)(barWidth * (progPercent / 100));
                    fill(poseStack, this.x + 10, yPos, this.x + 10 + filledWidth, yPos + 3, 0xFF44AAFF);
                    fill(poseStack, this.x + 10 + filledWidth, yPos, this.x + 10 + barWidth, yPos + 3, 0xFF444444);
                    yPos += 6;
                    
                    if (yPos > this.y + this.height - 15) break;
                }
            }
        }
        
        // 显示家政/战斗任务
        if (npcData.contains("housekeeping_tasks") || npcData.contains("combat_tasks")) {
            this.minecraft.font.draw(poseStack, "助手任务:", this.x, yPos, 0xFF4444);
            yPos += 10;
            
            // 家政任务
            if (npcData.contains("housekeeping_tasks")) {
                ListTag housekeepingTasks = npcData.getList("housekeeping_tasks", 10);
                for (int i = 0; i < Math.min(housekeepingTasks.size(), 2); i++) {
                    CompoundTag task = housekeepingTasks.getCompound(i);
                    String type = task.contains("type") ? task.getString("type") : "家政";
                    int progress = task.contains("current_progress") ? task.getInt("current_progress") : 0;
                    int required = task.contains("required_progress") ? task.getInt("required_progress") : 100;
                    float progPercent = required > 0 ? (float)progress / required * 100 : 0;
                    
                    String taskText = "• [家政] " + truncateText(type, 15) + " " + (int)progPercent + "%";
                    this.minecraft.font.draw(poseStack, taskText, this.x + 5, yPos, 0x88AA88);
                    yPos += 10;
                    if (yPos > this.y + this.height - 15) break;
                }
            }
            
            // 战斗任务
            if (npcData.contains("combat_tasks")) {
                ListTag combatTasks = npcData.getList("combat_tasks", 10);
                for (int i = 0; i < Math.min(combatTasks.size(), 2); i++) {
                    CompoundTag task = combatTasks.getCompound(i);
                    String type = task.contains("type") ? task.getString("type") : "战斗";
                    int progress = task.contains("current_progress") ? task.getInt("current_progress") : 0;
                    int required = task.contains("required_progress") ? task.getInt("required_progress") : 100;
                    float progPercent = required > 0 ? (float)progress / required * 100 : 0;
                    
                    String taskText = "• [战斗] " + truncateText(type, 15) + " " + (int)progPercent + "%";
                    this.minecraft.font.draw(poseStack, taskText, this.x + 5, yPos, 0xAA4444);
                    yPos += 10;
                    if (yPos > this.y + this.height - 15) break;
                }
            }
        }
    }
    
    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
    
    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
        // 空实现
    }
}