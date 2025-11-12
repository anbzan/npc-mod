package com.example.npcmod;

import com.example.npcmod.client.NpcRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NpcMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    /**
     * 注册NPC的渲染器。
     * 这会在客户端MOD加载时自动调用，确保NPC有正确的玩家模型渲染。
     */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NpcMod.NPC.get(), NpcRenderer::new);
    }

    /**
     * 可选：如果需要注册自定义层（如盔甲层），可以在这里添加。
     * 示例：
     * @SubscribeEvent
     * public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
     *     event.registerLayerDefinition(NpcRenderer.LAYER_LOCATION, NpcRenderer::createBodyLayer);  // 如果有自定义层
     * }
     */
}