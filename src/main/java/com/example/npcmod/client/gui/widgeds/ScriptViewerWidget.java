package com.example.npcmod.client.gui.widgets;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import com.example.npcmod.client.gui.NPCGuiDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * 预设脚本查看与触发功能组件
 */
public class ScriptViewerWidget extends AbstractWidget {
    private final String npcId;
    private final Player player;
    private final Runnable onScriptTrigger;
    
    public ScriptViewerWidget(int x, int y, int width, int height, String npcId, Player player, Runnable onScriptTrigger) {
        super(x, y, width, height, Component.empty());
        this.npcId = npcId;
        this.player = player;
        this.onScriptTrigger = onScriptTrigger;
    }
    
    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        CompoundTag npcData = NPCGuiDataManager.getInstance().getNPCData(npcId);
        
        // 渲染标题
        this.minecraft.font.draw(poseStack, "预设脚本", this.x, this.y, 0xFFFFFF);
        
        int yPos = this.y + 12;
        
        // 显示可用脚本
        if (npcData.contains("available_scripts")) {
            ListTag availableScripts = npcData.getList("available_scripts", 10);
            if (!availableScripts.isEmpty()) {
                this.minecraft.font.draw(poseStack, "可用脚本:", this.x, yPos, 0xAAAAFF);
                yPos += 10;
                for (int i = 0; i < Math.min(availableScripts.size(), 3); i++) {
                    CompoundTag script = availableScripts.getCompound(i);
                    String scriptName = script.contains("script_name") ? script.getString("script_name") : "未知脚本";
                    boolean active = script.contains("active") ? script.getBoolean("active") : false;
                    
                    String scriptText = (active ? "✓ " : "○ ") + truncateText(scriptName, 25);
                    int color = active ? 0x44FF44 : 0xAAAAAA;
                    this.minecraft.font.draw(poseStack, scriptText, this.x + 5, yPos, color);
                    yPos += 10;
                    
                    if (yPos > this.y + this.height - 15) break;
                }
                
                // 渲染触发按钮提示
                if (yPos <= this.y + this.height - 20) {
                    this.minecraft.font.draw(poseStack, "点击UI中的触发按钮来执行脚本", this.x, yPos, 0x888888);
                }
            } else {
                this.minecraft.font.draw(poseStack, "暂无可用脚本", this.x, yPos, 0x888888);
            }
        } else {
            this.minecraft.font.draw(poseStack, "脚本系统未初始化", this.x, yPos, 0xFF8888);
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
    
    // 触发脚本的方法（由外部调用）
    public void triggerSelectedScript() {
        if (this.onScriptTrigger != null) {
            this.onScriptTrigger.run();
        }
    }
}