package com.example.npcmod;

import com.example.npcmod.client.gui.NpcGui;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.LogicalSide;

@Mod.EventBusSubscriber(modid = NpcMod.MODID, value = {Dist.CLIENT, Dist.DEDICATED_SERVER})
public class NpcInteraction {
    
    /**
     * 处理玩家与NPC的交互事件。
     * 客户端：打开GUI菜单。
     * 服务器：记录交互日志/验证。
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof EntityNpc npc) {
            Player player = event.getEntity();
            
            if (event.getLevel().isClientSide()) {
                // 客户端：打开GUI（主线程执行）
                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().setScreen(new NpcGui(npc, player));
                });
            } else {
                // 服务器：验证并记录（可选：添加权限检查）
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.literal("与NPC交互已记录。"));
                    // 可添加日志：LogManager.getLogger().info("Player {} interacted with NPC {}", player.getName().getString(), npc.getId());
                }
            }
            
            // 取消事件，防止默认行为（如攻击）
            event.setCanceled(true);
        }
    }
}