package com.example.npcmod;

import com.example.npcmod.client.gui.ChatGui;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Supplier;

@Mod(NpcMod.MODID)
public class NpcMod {
    public static final String MODID = "npcmod";
    
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    
    public static final RegistryObject<EntityType<EntityNpc>> NPC = ENTITIES.register("npc",
            () -> EntityType.Builder.of(EntityNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .setTrackingRange(64)
                    .setUpdateInterval(3)
                    .build("npc"));
// 音效注册
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = EntityNpc.SOUND_EVENTS;
    
    // 网络通道
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    
    public NpcMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        ENTITIES.register(modEventBus);
SOUND_EVENTS.register(modEventBus);
        modEventBus.addListener(this::registerAttributes);
        // 修正 config 注册：直接调用，无 addListener
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        
        // 网络消息注册
        registerNetworkMessages();
        
        // Forge事件总线
        MinecraftForge.EVENT_BUS.register(NpcInteraction.class);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);  // 客户端事件
// Forge事件总线上注册命令事件
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> this.registerCommands(event));
        
        // 通用设置事件
        modEventBus.addListener(this::setup);
    }
    
    private void setup(final FMLCommonSetupEvent event) {
        // 通用初始化
    }
    
    private void registerNetworkMessages() {
        int messageId = 0;
        
        // ChatPacket: C->S 发送消息
        NETWORK.registerMessage(messageId++, ChatPacket.class, ChatPacket::encode, ChatPacket::decode, ChatPacket::handle);
        
        // ChatResponsePacket: S->C 更新GUI
        NETWORK.registerMessage(messageId++, ChatResponsePacket.class, ChatResponsePacket::encode, ChatResponsePacket::decode, ChatResponsePacket::handle);
        
        // GiveItemPacket: C->S 给予物品
        NETWORK.registerMessage(messageId++, GiveItemPacket.class, GiveItemPacket::encode, GiveItemPacket::decode, GiveItemPacket::handle);
    }
    
    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(NPC.get(), EntityNpc.createAttributes().build());
    }
    
    // 命令注册事件 (私有方法)
    private void registerCommands(RegisterCommandsEvent event) {
        CommandNpc.register(event.getDispatcher());
    }
    
    // ChatPacket: 客户端到服务器发送聊天消息
    public static class ChatPacket {
        private final int npcId;
        private final String message;
        
        public ChatPacket(int npcId, String message) {
            this.npcId = npcId;
            this.message = message;
        }
        
        public static void encode(ChatPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.npcId);
            buf.writeUtf(msg.message, 256);
        }
        
        public static ChatPacket decode(FriendlyByteBuf buf) {
            return new ChatPacket(buf.readInt(), buf.readUtf(256));
        }
        
        public static void handle(ChatPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    Entity npc = player.level().getEntity(msg.npcId);
                    if (npc instanceof EntityNpc entityNpc) {
                        String response = entityNpc.talkToNpc(msg.message);
                        // 发送响应回客户端 (修正：player.connection.connection)
                        ChatResponsePacket responsePkt = new ChatResponsePacket(msg.npcId, msg.message, response);
                        NETWORK.sendTo(responsePkt, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
    
    // ChatResponsePacket: 服务器到客户端更新聊天历史
    public static class ChatResponsePacket {
        private final int npcId;
        private final String playerMsg;
        private final String npcResponse;
        
        public ChatResponsePacket(int npcId, String playerMsg, String npcResponse) {
            this.npcId = npcId;
            this.playerMsg = playerMsg;
            this.npcResponse = npcResponse;
        }
        
        public static void encode(ChatResponsePacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.npcId);
            buf.writeUtf(msg.playerMsg, 256);
            buf.writeUtf(msg.npcResponse, 256);
        }
        
        public static ChatResponsePacket decode(FriendlyByteBuf buf) {
            return new ChatResponsePacket(buf.readInt(), buf.readUtf(256), buf.readUtf(256));
        }
        
        public static void handle(ChatResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                // 更新GUI（用静态缓存）
                ChatGui gui = ChatGui.openGuis.get(msg.npcId);
                if (gui != null) {
                    gui.updateChatHistory(msg.playerMsg, msg.npcResponse);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
    
    // GiveItemPacket: 客户端到服务器给予物品
    public static class GiveItemPacket {
        private final int npcId;
        private final int slot;
        
        public GiveItemPacket(int npcId, int slot) {
            this.npcId = npcId;
            this.slot = slot;
        }
        
        public static void encode(GiveItemPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.npcId);
            buf.writeInt(msg.slot);
        }
        
        public static GiveItemPacket decode(FriendlyByteBuf buf) {
            return new GiveItemPacket(buf.readInt(), buf.readInt());
        }
        
        public static void handle(GiveItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    Entity npc = player.level().getEntity(msg.npcId);
                    if (npc instanceof EntityNpc entityNpc) {
                        ItemStack stack = player.getInventory().getItem(msg.slot);
                        if (!stack.isEmpty()) {
                            ItemStack toGive = stack.copy();
                            toGive.setCount(1);
                            player.getInventory().removeItem(msg.slot, 1);
                            entityNpc.receiveItem(toGive);
                            player.sendSystemMessage(Component.literal("你给了NPC: " + toGive.getDisplayName().getString()));
                        }
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}