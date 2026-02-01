package com.example.npcmod.client.gui.widgets;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import com.example.npcmod.client.gui.NPCGuiDataManager;
import net.minecraft.nbt.CompoundTag;

/**
 * 好感度等级与羁绊进度条展示组件
 */
public class AffectionDisplayWidget extends AbstractWidget {
    private final String npcId;
    private final Player player;
    
    public AffectionDisplayWidget(int x, int y, int width, int height, String npcId, Player player) {
        super(x, y, width, height, Component.empty());
        this.npcId = npcId;
        this.player = player;
    }
    
    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        CompoundTag npcData = NPCGuiDataManager.getInstance().getNPCData(npcId);
        
        // 获取好感度数据
        float affectionScore = npcData.contains("affection_score") ? npcData.getFloat("affection_score") : 0.0f;
        int bondLevel = npcData.contains("bond_level") ? npcData.getInt("bond_level") : 0;
        float bondExperience = npcData.contains("bond_experience") ? npcData.getFloat("bond_experience") : 0.0f;
        
        // 渲染好感度等级
        String levelText = "好感度等级: " + bondLevel;
        this.minecraft.font.draw(poseStack, levelText, this.x, this.y, 0xFFFFFF);
        
        // 渲染羁绊进度条
        float progress = Math.min(bondExperience / 100.0f, 1.0f); // 假设100为满经验
        int progressBarWidth = (int)(this.width * progress);
        fill(poseStack, this.x, this.y + 12, this.x + progressBarWidth, this.y + 22, 0xFF44AAFF);
        fill(poseStack, this.x + progressBarWidth, this.y + 12, this.x + this.width, this.y + 22, 0xFF444444);
        
        String expText = String.format("羁绊经验: %.1f", bondExperience);
        this.minecraft.font.draw(poseStack, expText, this.x, this.y + 24, 0xAAAAAA);
    }
    
    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
        // 空实现
    }
}