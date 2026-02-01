package com.example.npcmod.client.gui.events;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.example.npcmod.NPCMod;
import com.example.npcmod.client.gui.screens.NPCGuiScreen;
import com.example.npcmod.client.gui.containers.NPCContainer;
import net.minecraft.client.Minecraft;

/**
 * NPC实体交互事件处理器
 */
@Mod.EventBusSubscriber(modid = NPCMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NPCEntityInteractEvent {
    
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();
        
        // 检查目标是否为NPC实体
        if (isNPCEntity(target)) {
            if (!player.level().isClientSide) {
                // 服务端：打开容器
                player.openMenu(new NPCContainer.Provider(target.getUUID().toString()));
            } else {
                // 客户端：打开GUI屏幕
                Minecraft.getInstance().setScreen(new NPCGuiScreen(player, target.getUUID().toString(), null));
            }
            
            event.setCanceled(true);
        }
    }
    
    private static boolean isNPCEntity(Entity entity) {
        // 这里应该检查实体是否为自定义NPC实体
        // 根据实际的NPC实体类型进行判断
        return entity.getType().getRegistryName().getNamespace().equals(NPCMod.MOD_ID) &&
               entity.getType().getRegistryName().getPath().contains("npc");
    }
    
    // 容器提供者内部类
    public static class Provider implements MenuProvider {
        private final String npcId;
        
        public Provider(String npcId) {
            this.npcId = npcId;
        }
        
        @Override
        public Component getDisplayName() {
            return Component.translatable("container.npcmod.npc_gui");
        }
        
        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
            return new NPCContainer(containerId, inventory);
        }
    }
}