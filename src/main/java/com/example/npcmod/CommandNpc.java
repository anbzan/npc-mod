package com.example.npcmod;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;  // 修正导入：net.minecraft.world.level.Level
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class CommandNpc {
    
    /**
     * 注册 /npc 命令。
     * 用法：/npc - 在玩家前方召唤一个NPC实体。
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("npc")
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                if (source.getEntity() instanceof ServerPlayer player) {
                    Level level = player.level();  // 现在 Level 导入正确
                    ServerLevel serverLevel = (ServerLevel) level;
                    
                    // 创建NPC实体
                    EntityNpc npc = new EntityNpc(NpcMod.NPC.get(), serverLevel);
                    
                    // 设置位置：玩家前方1格偏移，避免重叠
                    Vec3 playerPos = player.position();
                    Vec3 forward = player.getLookAngle().normalize().scale(2.0);
                    Vec3 spawnPos = playerPos.add(forward);
                    npc.setPos(spawnPos.x, playerPos.y, spawnPos.z);
                    
                    // 添加到世界
                    serverLevel.addFreshEntity(npc);
                    
                    // 发送成功消息
                    source.sendSuccess(() -> Component.literal("NPC 已召唤！位置: " + String.format("%.1f, %.1f, %.1f", spawnPos.x, spawnPos.y, spawnPos.z)), false);
                    return 1;
                } else {
                    // 非玩家来源
                    source.sendFailure(Component.literal("仅玩家可以使用此命令！"));
                    return 0;
                }
            })
        );
    }
}