package com.example.npcmod.client.gui.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import com.example.npcmod.NPCMod;
import net.minecraft.world.entity.Entity;

/**
 * NPC GUI相关渲染器（占位类，实际渲染在屏幕类中处理）
 */
public class NPCGuiRenderer {
    
    // 这个类主要用于组织代码结构
    // 实际的GUI渲染在NPCGuiScreen中处理
    
    public static final ResourceLocation GUI_TEXTURE = new ResourceLocation(NPCMod.MOD_ID, "textures/gui/npc_gui.png");
    
    public static void renderGuiBackground(PoseStack poseStack, MultiBufferSource buffer, int x, int y, int width, int height) {
        // 如果需要自定义GUI背景纹理，可以在这里实现
        // 目前使用Minecraft默认的GUI背景
    }
    
    public static void renderProgressBar(PoseStack poseStack, int x, int y, int width, int height, float progress, int color) {
        // 渲染进度条的辅助方法
        int filledWidth = (int)(width * progress);
        fill(poseStack, x, y, x + filledWidth, y + height, color);
        fill(poseStack, x + filledWidth, y, x + width, y + height, 0xFF444444);
    }
}