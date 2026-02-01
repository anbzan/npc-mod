package com.example.npcmod.client;

import com.example.npcmod.EntityNpc;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;

public class NpcRenderer extends HumanoidMobRenderer<EntityNpc, HumanoidModel<EntityNpc>> {
    private static final ResourceLocation DEFAULT_SKIN = new ResourceLocation("textures/entity/steve.png");
    private static final ResourceLocation ALEX_SKIN = new ResourceLocation("textures/entity/alex.png");

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        
        // 添加盔甲层（支持NPC穿戴盔甲渲染）
        this.addLayer(new HumanoidArmorLayer<>(this,
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()
        ));
        
        // 可选：添加持有物品层（如果EntityNpc有getHeldItem()）
        // this.addLayer(new HeldItemLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(EntityNpc entity) {
        // 动态皮肤：从实体数据读取（默认Steve；可扩展为Alex或自定义）
        // 假设EntityNpc添加方法：public ResourceLocation getSkinLocation() { return random.nextBoolean() ? DEFAULT_SKIN : ALEX_SKIN; }
        return entity.getSkinLocation();  // 或直接 return DEFAULT_SKIN;
    }

    /**
     * 可选重写：自定义渲染逻辑，如持有物品姿势
     */
    @Override
    public void render(EntityNpc entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}