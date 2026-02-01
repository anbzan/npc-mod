package com.example.npcmod.client.gui;

import com.example.npcmod.EntityNpc;
import com.example.npcmod.NpcMod;  // 导入NpcMod以访问NETWORK和Packet
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatGui extends Screen {
    private final EntityNpc npc;
    private final Player player;
    private EditBox chatInput;
    private List<String> chatHistory;
    private int scrollOffset = 0;
    
    // 静态Map用于缓存打开的ChatGui（按NPC ID），便于响应包更新 (public static final，无重复)
    public static final Map<Integer, ChatGui> openGuis = new HashMap<>();
    
    public ChatGui(EntityNpc npc, Player player) {
        super(Component.literal("与NPC对话"));
        this.npc = npc;
        this.player = player;
        this.chatHistory = new ArrayList<>();
        this.chatHistory.add("NPC: 你好！我是智能NPC，我们可以聊天。");
        
        // 缓存此GUI实例 (正确 put，无前缀)
        openGuis.put(npc.getId(), this);
    }

    @Override
    protected void init() {
        super.init();

        // 聊天输入框
        this.chatInput = new EditBox(this.font, this.width / 2 - 150, this.height - 40, 300, 20, Component.literal("输入消息"));
        this.chatInput.setMaxLength(256);
        this.addRenderableWidget(this.chatInput);
        this.setInitialFocus(this.chatInput);

        // 发送按钮 (Builder)
        this.addRenderableWidget(Button.builder(Component.literal("发送"), button -> sendMessage())
            .pos(this.width / 2 - 50, this.height - 60)
            .size(100, 20)
            .build());

        // 返回按钮 (Builder)
        this.addRenderableWidget(Button.builder(Component.literal("返回"), button -> {
            onClose();  // 清理缓存
            this.minecraft.setScreen(new NpcGui(npc, player));
        })
            .pos(this.width / 2 - 100, this.height - 90)
            .size(200, 20)
            .build());

        // 滚动按钮 ↑ (Builder)
        this.addRenderableWidget(Button.builder(Component.literal("↑"), button -> {
            if (scrollOffset > 0) scrollOffset--;
        })
            .pos(this.width - 30, this.height / 2 - 10)
            .size(20, 20)
            .build());

        // 滚动按钮 ↓ (Builder)
        this.addRenderableWidget(Button.builder(Component.literal("↓"), button -> {
            int maxVisibleLines = 10;
            int maxScroll = Math.max(0, chatHistory.size() - maxVisibleLines);
            if (scrollOffset < maxScroll) scrollOffset++;
        })
            .pos(this.width - 30, this.height / 2 + 10)
            .size(20, 20)
            .build());
    }

    private void sendMessage() {
        String message = chatInput.getValue().trim();
        if (!message.isEmpty()) {
            // 添加玩家消息到聊天历史（立即显示）
            chatHistory.add("你: " + message);
            
            // 发送网络包到服务器
            NpcMod.NETWORK.sendToServer(new NpcMod.ChatPacket(npc.getId(), message));
            
            // 清空输入框
            chatInput.setValue("");
            
            // 自动滚动到底部
            int maxVisibleLines = 10;
            scrollOffset = Math.max(0, chatHistory.size() - maxVisibleLines);
            
            // 添加占位响应，服务器响应后替换
            chatHistory.add("NPC: 响应中...");
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257) { // Enter键
            this.sendMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        
        // 绘制标题
        guiGraphics.drawString(this.font, "与NPC对话", this.width / 2 - 30, 20, 0xFFFFFF);
        
        // 绘制聊天历史（修正滚动逻辑）
        int startY = 50;
        int maxLines = 10;
        int totalLines = chatHistory.size();
        int maxScroll = Math.max(0, totalLines - maxLines);
        scrollOffset = Math.min(scrollOffset, maxScroll);  // 防止越界
        
        int startIndex = totalLines - maxLines + scrollOffset;
        if (startIndex < 0) startIndex = 0;  // 安全下界
        int endIndex = Math.min(totalLines, startIndex + maxLines);
        
        for (int i = startIndex; i < endIndex; i++) {
            String line = chatHistory.get(i);
            
            FormattedText truncated = this.font.substrByWidth(Component.literal(line), this.width-40);
            if (truncated != FormattedText.EMPTY) {
                line = truncated.getString() + "...";
            }
            int color = line.startsWith("你:") ? 0x55FF55 : (line.startsWith("NPC:") ? 0x5555FF : 0xFFFFFF);
            guiGraphics.drawString(this.font, line, 20, startY + (i - startIndex) * 12, color);
        }
        
        // 绘制滚动指示器
        if (totalLines > maxLines) {
            String scrollText = (scrollOffset + 1) + "/" + (maxScroll + 1);
            guiGraphics.drawString(this.font, scrollText, this.width - 60, this.height / 2 - 5, 0xAAAAAA);
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        // 清理缓存 (正确 remove，无前缀)
        openGuis.remove(npc.getId());
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    // 公共方法：用于响应包更新历史（从NpcMod.ChatResponsePacket.handle调用）
    public void updateChatHistory(String playerMsg, String npcResponse) {
        // 移除占位（假设最后一个是占位）
        if (!chatHistory.isEmpty() && chatHistory.get(chatHistory.size() - 1).contains("响应中...")) {
            chatHistory.remove(chatHistory.size() - 1);
        }
        chatHistory.add("NPC: " + npcResponse);
        
        // 重新计算滚动到底部
        int maxVisibleLines = 10;
        scrollOffset = Math.max(0, chatHistory.size() - maxVisibleLines);
    }
}