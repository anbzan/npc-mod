package com.example.npcmod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class Config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec COMMON_SPEC;
    
    public static final ForgeConfigSpec.ConfigValue<String> DEEPSEEK_API_KEY;
    
    static {
        BUILDER.comment("NPC Mod Configuration");
        DEEPSEEK_API_KEY = BUILDER.comment("DeepSeek API Key").define("deepseek_api_key", "your_key_here");
        COMMON_SPEC = BUILDER.build();
    }
    
    public static void register(ForgeConfigSpec spec) {  // 接收 spec 参数
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, spec);
    }
}