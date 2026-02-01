package com.example.npcmod.client.gui.widgets;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import com.example.npcmod.client.gui.NPCGuiDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/**
 * 交互历史摘要与记忆关键点显示组件
 */
public class MemoryDisplayWidget extends AbstractWidget {
    private final String npcId;
    private final Player player;
    
    public MemoryDisplayWidget(int x, int y, int width, int height, String npcId, Player player) {
        super(x, y, width, height, Component.empty());
        this.npcId = npcId;
        this.player = player;
    }
    
    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        CompoundTag npcData = NPCGuiDataManager.getInstance().getNPCData(npcId);
        
        // 渲染标题
        this.minecraft.font.draw(poseStack, "交互历史摘要", this.x, this.y, 0xFFFFFF);
        
        int yPos = this.y + 12;
        
        // 显示最近的玩家消息
        if (npcData.contains("last_player_message")) {
            String lastPlayerMsg = npcData.getString("last_player_message");
            if (!lastPlayerMsg.isEmpty()) {
                String playerText = "玩家: " + truncateText(lastPlayerMsg, 30);
                this.minecraft.font.draw(poseStack, playerText, this.x, yPos, 0xAAAAAA);
                yPos += 10;
            }
        }
        
        // 显示最近的NPC响应
        if (npcData.contains("last_npc_response")) {
            String lastNpcResponse = npcData.getString("last_npc_response");
            if (!lastNpcResponse.isEmpty()) {
                String npcText = "NPC: " + truncateText(lastNpcResponse, 30);
                this.minecraft.font.draw(poseStack, npcText, this.x, yPos, 0xAAAAAA);
                yPos += 10;
            }
        }
        
        // 显示关键记忆点
        if (npcData.contains("key_memories")) {
            ListTag keyMemories = npcData.getList("key_memories", 8);
            if (!keyMemories.isEmpty()) {
                this.minecraft.font.draw(poseStack, "关键记忆点:", this.x, yPos, 0xFFFFFF);
                yPos += 10;
                for (int i = 0; i < Math.min(keyMemories.size(), 3); i++) {
                    String memory = keyMemories.getString(i);
                    String memoryText = "• " + truncateText(memory, 25);
                    this.minecraft.font.draw(poseStack, memoryText, this.x + 5, yPos, 0x888888);
                    yPos += 10;
                    if (yPos > this.y + this.height - 10) break;
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