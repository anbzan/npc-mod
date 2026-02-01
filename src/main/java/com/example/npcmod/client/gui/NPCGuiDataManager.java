package com.example.npcmod.client.gui;

import net.minecraft.nbt.CompoundTag;
import java.util.HashMap;
import java.util.Map;

/**
 * NPC GUI数据管理器
 */
public class NPCGuiDataManager {
    private static NPCGuiDataManager instance;
    private final Map<String, CompoundTag> npcDataMap = new HashMap<>();
    
    private NPCGuiDataManager() {}
    
    public static NPCGuiDataManager getInstance() {
        if (instance == null) {
            instance = new NPCGuiDataManager();
        }
        return instance;
    }
    
    public void updateNPCData(String npcId, byte[] nbtData) {
        CompoundTag tag = CompoundTag.fromBytes(nbtData);
        npcDataMap.put(npcId, tag);
    }
    
    public CompoundTag getNPCData(String npcId) {
        return npcDataMap.getOrDefault(npcId, new CompoundTag());
    }
}