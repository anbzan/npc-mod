package com.example.npcmod.client.gui.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import com.example.npcmod.NPCMod;

import java.util.function.Supplier;

/**
 * NPC数据同步包
 */
public class SyncNPCDataPacket implements CustomPacketPayload {
    public static final ResourceLocation ID = new ResourceLocation(NPCMod.MOD_ID, "sync_npc_data");
    private final String npcId;
    private final byte[] nbtData;
    
    public SyncNPCDataPacket(String npcId, byte[] nbtData) {
        this.npcId = npcId;
        this.nbtData = nbtData;
    }
    
    public SyncNPCDataPacket(FriendlyByteBuf buf) {
        this.npcId = buf.readUtf();
        this.nbtData = buf.readByteArray();
    }
    
    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(npcId);
        buf.writeByteArray(nbtData);
    }
    
    @Override
    public ResourceLocation id() {
        return ID;
    }
    
    public static void handle(SyncNPCDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 在客户端处理数据同步
            NPCGuiDataManager.getInstance().updateNPCData(packet.npcId, packet.nbtData);
        });
        ctx.get().setPacketHandled(true);
    }
}