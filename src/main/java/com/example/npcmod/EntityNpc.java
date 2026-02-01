package com.example.npcmod;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class EntityNpc extends PathfinderMob {
// 音效注册
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "npcmod");
    public static final RegistryObject<SoundEvent> NPC_HURT = SOUND_EVENTS.register("npc_hurt", 
        () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("npcmod", "npc_hurt")));
    private static final EntityDataAccessor<Integer> DATA_FOOD_LEVEL = SynchedEntityData.defineId(EntityNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_MEMORY = SynchedEntityData.defineId(EntityNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<CompoundTag> DATA_HELD_ITEM = SynchedEntityData.defineId(EntityNpc.class, EntityDataSerializers.COMPOUND_TAG);

    private int foodLevel = 20;
    private int lastFoodLevel = 20;
    private int foodTimer = 0;
    
    // 对话记忆和物品存储（同步）
    private String memory = "";
    private ItemStack heldItem = ItemStack.EMPTY;


    private final Gson gson = new Gson();
// API配置（动态从Config读取）
    private String getApiKey() {
        return Config.DEEPSEEK_API_KEY.get();
    }
    private final Random random = new Random();

    public EntityNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FOOD_LEVEL, 20);
        this.entityData.define(DATA_MEMORY, "");
        this.entityData.define(DATA_HELD_ITEM, new CompoundTag());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FoodLevel", foodLevel);
        tag.putString("Memory", memory);
        CompoundTag itemTag = new CompoundTag();
        heldItem.save(itemTag);
        tag.put("HeldItem", itemTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        foodLevel = tag.getInt("FoodLevel");
        memory = tag.getString("Memory");
        heldItem = ItemStack.of(tag.getCompound("HeldItem"));
        this.entityData.set(DATA_FOOD_LEVEL, foodLevel);
        this.entityData.set(DATA_MEMORY, memory);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new EatFoodGoal(this));
        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(5, new JumpGoal(this));
        
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        
        // 仅服务器处理饥饿
        if (!this.level().isClientSide()) {
            foodTimer++;
            
// 每30秒减少一点饥饿值（600 tick = 30秒）- 调整为更合理的速度
            if (foodTimer >= 600) {
                foodTimer = 0;
                if (foodLevel > 0) {
                    foodLevel--;
                    this.entityData.set(DATA_FOOD_LEVEL, foodLevel);  // 同步
                    lastFoodLevel = foodLevel;
                }
            }
            
            // 当饥饿值为0时，每2秒造成伤害
            if (foodLevel <= 0 && foodTimer % 40 == 0) {
                this.hurt(this.damageSources().starve(), 1.0F);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source == this.damageSources().starve() && foodLevel > 0) {
            return false; // 有饥饿值时不会饿死
        }
        return super.hurt(source, amount);
    }

@Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return NPC_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.0F;
    }

    // 获取当前饥饿值（客户端可读）
    public int getFoodLevel() {
        return this.entityData.get(DATA_FOOD_LEVEL);
    }

    // 设置饥饿值
    public void setFoodLevel(int foodLevel) {
        this.foodLevel = Math.min(foodLevel, 20);
        this.entityData.set(DATA_FOOD_LEVEL, this.foodLevel);
        this.lastFoodLevel = this.foodLevel;
    }

    // 增加饥饿值（当吃食物时）
    public void addFood(int food) {
        this.foodLevel = Math.min(this.foodLevel + food, 20);
        this.entityData.set(DATA_FOOD_LEVEL, this.foodLevel);
        this.lastFoodLevel = this.foodLevel;
    }
    
    // 接收物品的方法
    public void receiveItem(ItemStack stack) {
        this.heldItem = stack.copy();
        CompoundTag itemTag = new CompoundTag();
        heldItem.save(itemTag);
        this.entityData.set(DATA_HELD_ITEM, itemTag);
        
        // 如果物品是食物，立即吃掉
        FoodProperties foodProps = stack.getItem().getFoodProperties();
        if (foodProps != null) {
            int nutrition = foodProps.getNutrition();
            this.addFood(nutrition);
            this.memory += "玩家给了我" + stack.getDisplayName().getString() + "，我吃掉了它。";
        } else {
            this.memory += "玩家给了我" + stack.getDisplayName().getString() + "。";
        }
        this.entityData.set(DATA_MEMORY, this.memory);
    }
    
    
       
    public String talkToNpc(String playerMessage) {
              // 更新记忆
        memory += "玩家说：" + playerMessage + " ";
        this.entityData.set(DATA_MEMORY, memory);
    
         // 异步调用API，避免阻塞
        CompletableFuture.supplyAsync(() -> callDeepSeekAPI(playerMessage))
            .thenAccept(response -> {
            // 执行响应指令（延迟执行）
                executeCommandsFromResponse(response);
            // 更新记忆
                memory += "我回复：" + response + " ";
                if (memory.length() > 1000) {
                    memory = memory.substring(memory.length() - 1000);
                }
                this.entityData.set(DATA_MEMORY, memory);
            })
            .exceptionally(throwable -> {
            // 错误备用 - 返回 null 而不是 String
                String fallback = "抱歉，我现在无法思考。";
                executeCommandsFromResponse(fallback);
                memory += "我回复：" + fallback + " ";
                if (memory.length() > 1000) {
                memory = memory.substring(memory.length() - 1000);
                }
                this.entityData.set(DATA_MEMORY, memory);
               return null;  // 修正：返回 null 而不是 String
            });
    
           // 立即返回（API异步，后续更新）
        return "思考中...（响应即将到来）";
    }
    // 调用DeepSeek API的方法（完整实现，修正Gson array）
    private String callDeepSeekAPI(String message) {
        try {
String apiKey = getApiKey();
            if ("your_key_here".equals(apiKey)) {
                return "请先在配置文件中设置DeepSeek API密钥（/config/npcmod-common.toml）";
            }
            URL url = new URL("https://api.deepseek.com/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(5000);  // 5s超时
            conn.setReadTimeout(10000);    // 10s读超时

            // JSON请求体
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "deepseek-chat");
            
            // 修正：messages as JsonArray
            JsonArray messagesArray = new JsonArray();
            JsonObject contentObj = new JsonObject();
            contentObj.addProperty("role", "user");
            contentObj.addProperty("content", memory + message);
            messagesArray.add(contentObj);
            requestBody.add("messages", messagesArray);

            String json = gson.toJson(requestBody);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    // 解析JSON：假设choices[0].message.content
                    JsonObject jsonResponse = gson.fromJson(response.toString(), JsonObject.class);
                    if (jsonResponse.has("choices") && jsonResponse.getAsJsonArray("choices").size() > 0) {
                        return jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject()
                            .getAsJsonObject("message").get("content").getAsString();
                    }
                }
            } else {
                return "API错误: " + responseCode;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "抱歉，我现在无法思考。错误：" + e.getMessage();
        }
        return "未知响应";
    }
    
    // 从AI响应中解析并执行指令（用正则robust）
    private void executeCommandsFromResponse(String response) {
        Pattern movePat = Pattern.compile("\\[移动\\]", Pattern.CASE_INSENSITIVE);
        Pattern jumpPat = Pattern.compile("\\[跳跃\\]", Pattern.CASE_INSENSITIVE);
        Pattern hungerPat = Pattern.compile("\\[饥饿\\]", Pattern.CASE_INSENSITIVE);
        Pattern followPat = Pattern.compile("\\[跟随\\]", Pattern.CASE_INSENSITIVE);
        Pattern stopPat = Pattern.compile("\\[停止\\]", Pattern.CASE_INSENSITIVE);
        
        if (movePat.matcher(response).find()) {
            // 随机移动
            this.getNavigation().moveTo(this.getX() + (random.nextDouble() * 10 - 5), this.getY(), this.getZ() + (random.nextDouble() * 10 - 5), 1.0D);
        }
        if (jumpPat.matcher(response).find()) {
            if (this.onGround()) {
                this.jumpFromGround();
            }
        }
        if (hungerPat.matcher(response).find()) {
            if (this.foodLevel < 10) {
                this.memory += "我感到饥饿，正在寻找食物。";
                this.entityData.set(DATA_MEMORY, memory);
            }
        }
        if (followPat.matcher(response).find()) {
            Player nearestPlayer = this.level().getNearestPlayer(this, 10.0);
            if (nearestPlayer != null) {
                this.getNavigation().moveTo(nearestPlayer, 1.0D);
                this.memory += "我开始跟随玩家。";
                this.entityData.set(DATA_MEMORY, memory);
            }
        }
        if (stopPat.matcher(response).find()) {
            this.getNavigation().stop();
            this.memory += "我停止了当前动作。";
            this.entityData.set(DATA_MEMORY, memory);
        }
    }
    
    // 获取记忆
    public String getMemory() {
        return this.entityData.get(DATA_MEMORY);
    }
    
    // 获取持有的物品
    public ItemStack getHeldItem() {
        CompoundTag tag = this.entityData.get(DATA_HELD_ITEM);
        return ItemStack.of(tag);
    }

// 添加皮肤方法 (为Renderer)
public ResourceLocation getSkinLocation() {
        return new ResourceLocation("npcmod", "textures/entity/skinmc.png");  // 自定义皮肤
    }

    // 进食目标 - 当饥饿值低时寻找并吃食物（添加所有权检查）
    static class EatFoodGoal extends Goal {
        private final EntityNpc npc;
        private ItemEntity targetFood;
        private int eatingTime;

        public EatFoodGoal(EntityNpc npc) {
            this.npc = npc;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (npc.getFoodLevel() >= 10) {
                return false;
            }

            // 寻找附近的食物物品（仅无主物品）
            List<ItemEntity> items = npc.level().getEntitiesOfClass(ItemEntity.class, 
                npc.getBoundingBox().inflate(16.0D));
            
            for (ItemEntity item : items) {
                ItemStack stack = item.getItem();
                if (stack.getItem().getFoodProperties() != null && item.getOwner() == null) {  // 无主物品
                    targetFood = item;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return targetFood != null && 
                   targetFood.isAlive() && 
                   npc.getFoodLevel() < 20 && 
                   eatingTime < 100;
        }

        @Override
        public void start() {
            eatingTime = 0;
            if (targetFood != null) {
                npc.getNavigation().moveTo(targetFood, 1.0D);
            }
        }

        @Override
        public void stop() {
            targetFood = null;
            eatingTime = 0;
            npc.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (targetFood != null && targetFood.isAlive()) {
                npc.getLookControl().setLookAt(targetFood, 30.0F, 30.0F);
                
                if (npc.distanceToSqr(targetFood) < 4.0D) {
                    eatingTime++;
                    npc.getNavigation().stop();
                    
                    if (eatingTime >= 20) { // 1秒后吃掉
                        ItemStack stack = targetFood.getItem();
                        FoodProperties foodProps = stack.getItem().getFoodProperties();
                        
                        if (foodProps != null) {
                            int nutrition = foodProps.getNutrition();
                            npc.addFood(nutrition);
                            
                            stack.shrink(1);
                            if (stack.isEmpty()) {
                                targetFood.discard();
                            }
                        }
                        stop();
                    }
                } else {
                    npc.getNavigation().moveTo(targetFood, 1.0D);
                }
            } else {
                stop();
            }
        }
    }

    // 跳跃目标 - 简化触发（随机闲逛时）
    static class JumpGoal extends Goal {
        private final EntityNpc npc;
        private int jumpCooldown;

        public JumpGoal(EntityNpc npc) {
            this.npc = npc;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (jumpCooldown > 0) {
                jumpCooldown--;
                return false;
            }
            
            // 仅随机闲逛时，10%几率
            return npc.getRandom().nextFloat() < 0.1F && npc.getNavigation().isDone() && npc.onGround();
        }

        @Override
        public void start() {
            npc.jumpFromGround();
            jumpCooldown = 100; // 5秒冷却
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }
}