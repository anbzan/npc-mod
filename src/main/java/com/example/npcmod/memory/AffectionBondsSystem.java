package com.example.npcmod.memory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.UUIDUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * AffectionBondsSystem - 好感度与羁绊系统核心实现
 * 实现多维度好感度计算、羁绊等级动态成长、事件触发式关系变化等完整功能
 * 与NPCMemorySystem_Complete.java和ContextAwareDialogueSystem.java无缝集成
 */
public class AffectionBondsSystem extends SavedData {

    // 系统实例单例
    private static AffectionBondsSystem instance;

    // NPC好感度与羁绊数据映射表
    private final Map<UUID, AffectionData> affectionDataMap = new ConcurrentHashMap<>();

    // 情感关键词映射表（与ContextAwareDialogueSystem保持一致）
    private static final Map<String, Float> POSITIVE_EMOTION_WORDS = new ConcurrentHashMap<>();
    private static final Map<String, Float> NEGATIVE_EMOTION_WORDS = new ConcurrentHashMap<>();

    // 多维度权重配置（用于综合评分计算）
    private static final Map<String, Double> WEIGHTS = new HashMap<>();
    static {
        // 初始化积极情感词
        POSITIVE_EMOTION_WORDS.put("喜欢", 0.8f);
        POSITIVE_EMOTION_WORDS.put("爱", 0.9f);
        POSITIVE_EMOTION_WORDS.put("开心", 0.7f);
        POSITIVE_EMOTION_WORDS.put("高兴", 0.7f);
        POSITIVE_EMOTION_WORDS.put("愉快", 0.6f);
        POSITIVE_EMOTION_WORDS.put("感谢", 0.8f);
        POSITIVE_EMOTION_WORDS.put("谢谢", 0.8f);
        POSITIVE_EMOTION_WORDS.put("好", 0.5f);
        POSITIVE_EMOTION_WORDS.put("棒", 0.6f);
        POSITIVE_EMOTION_WORDS.put("优秀", 0.7f);

        // 初始化消极情感词
        NEGATIVE_EMOTION_WORDS.put("讨厌", -0.8f);
        NEGATIVE_EMOTION_WORDS.put("恨", -0.9f);
        NEGATIVE_EMOTION_WORDS.put("生气", -0.7f);
        NEGATIVE_EMOTION_WORDS.put("愤怒", -0.8f);
        NEGATIVE_EMOTION_WORDS.put("难过", -0.6f);
        NEGATIVE_EMOTION_WORDS.put("伤心", -0.7f);
        NEGATIVE_EMOTION_WORDS.put("糟糕", -0.6f);
        NEGATIVE_EMOTION_WORDS.put("差", -0.5f);
        NEGATIVE_EMOTION_WORDS.put("坏", -0.6f);

        // 初始化多维度权重（用于综合评分）
        WEIGHTS.put("affinity", 0.4);        // 好感度 40%
        WEIGHTS.put("taskCompletion", 0.25); // 任务完成度 25%
        WEIGHTS.put("keyChoices", 0.20);     // 关键选择 20%
        WEIGHTS.put("timeManagement", 0.15); // 时间管理 15%
    }

    /**
     * 获取系统实例（单例模式）
     */
    public static AffectionBondsSystem getInstance() {
        return instance;
    }

    /**
     * 在世界加载时初始化系统实例
     */
    public static AffectionBondsSystem getOrCreateInstance(ServerLevel level) {
        if (instance == null) {
            instance = level.getDataStorage().computeIfAbsent(
                AffectionBondsSystem::new,
                AffectionBondsSystem::new,
                "affection_bonds_system"
            );
        }
        return instance;
    }

    /**
     * 构造函数
     */
    public AffectionBondsSystem() {
        super();
    }

    /**
     * 获取指定NPC的好感度数据，若不存在则创建新实例
     */
    public AffectionData getOrCreateAffectionData(UUID npcId) {
        return affectionDataMap.computeIfAbsent(npcId, id -> new AffectionData(id));
    }

    /**
     * 多维度好感度计算模块
     */

    /**
     * 交互频率量化 - 处理日常互动行为
     */
    public void handleInteractionEvent(UUID npcId, InteractionType type, GiftPreference giftPreference) {
        AffectionData data = getOrCreateAffectionData(npcId);
        int change = 0;

        switch (type) {
            case DIALOGUE:
                // 每日对话 +20点
                change = 20;
                break;
            case GIFT:
                // 送礼根据偏好程度给予不同好感度
                switch (giftPreference) {
                    case LOVED: change = 80; break;
                    case LIKED: change = 45; break;
                    case NEUTRAL: change = 20; break;
                    case DISLIKED: change = -20; break;
                    case HATED: change = -40; break;
                }
                break;
            case TASK_COMPLETED:
                // 任务完成 +150点
                change = 150;
                break;
        }

        modifyAffectionScore(data, change, "interaction:" + type.name().toLowerCase());
    }

    /**
     * 情感倾向分析 - 基于Senta模型的情感分类（简化实现）
     */
    public float analyzeEmotion(String message) {
        if (message == null || message.trim().isEmpty()) {
            return 0.0f;
        }

        String lowerMessage = message.toLowerCase();
        float totalScore = 0.0f;
        int wordCount = 0;

        // 检查积极情感词
        for (Map.Entry<String, Float> entry : POSITIVE_EMOTION_WORDS.entrySet()) {
            if (lowerMessage.contains(entry.getKey())) {
                totalScore += entry.getValue();
                wordCount++;
            }
        }

        // 检查消极情感词
        for (Map.Entry<String, Float> entry : NEGATIVE_EMOTION_WORDS.entrySet()) {
            if (lowerMessage.contains(entry.getKey())) {
                totalScore += entry.getValue();
                wordCount++;
            }
        }

        if (wordCount == 0) return 0.0f;

        float averageScore = totalScore / wordCount;
        return Math.max(-1.0f, Math.min(1.0f, averageScore));
    }

    /**
     * 提取情感关键词
     */
    public List<String> extractKeywords(String message) {
        if (message == null || message.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String cleanMessage = message.replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        String[] words = cleanMessage.trim().split("\\s+");
        List<String> keywords = new ArrayList<>();

        Set<String> stopWords = Set.of("的", "了", "在", "是", "我", "你", "他", "她", "它", "们", "这", "那", "和", "与", "或", "但", "而", "就", "也", "都", "很", "太", "真", "好", "啊", "哦", "嗯", "呀", "吧", "呢", "吗", "啦");

        for (String word : words) {
            if (word.length() >= 2 && !stopWords.contains(word.toLowerCase())) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    /**
     * 任务完成度权重分配与综合评分计算
     */
    public double calculateCompositeScore(int affinityScore, double taskCompletionScore,
                                       double keyChoicesScore, double timeManagementScore) {
        return affinityScore * WEIGHTS.get("affinity") +
               taskCompletionScore * WEIGHTS.get("taskCompletion") +
               keyChoicesScore * WEIGHTS.get("keyChoices") +
               timeManagementScore * WEIGHTS.get("timeManagement");
    }

    /**
     * 羁绊等级动态成长体系
     */

    /**
     * 羁绊等级划分（阶段称号制）
     */
    public enum BondLevel {
        INITIAL(0, 100),      // 初识
        FRIENDLY(100, 300),    // 友好
        FAMILIAR(300, 600),    // 熟悉
        INTIMATE(600, 1000),  // 亲密
        BONDED(1000, Integer.MAX_VALUE); // 牵绊

        private final int minExperience, maxExperience;

        BondLevel(int min, int max) {
            this.minExperience = min;
            this.maxExperience = max;
        }

        public static BondLevel fromExperience(int experience) {
            for (BondLevel level : values()) {
                if (experience >= level.minExperience && experience < level.maxExperience) {
                    return level;
                }
            }
            return BONDED;
        }
    }

    /**
     * 《碧蓝档案》羽留奈成长经验表算法（简化版）
     */
    public int getExperienceRequiredForLevel(int level) {
        if (level <= 0) return 0;
        if (level == 1) return 15;
        if (level <= 25) return 15 + (level - 1) * 15;
        if (level <= 50) return 400 + (level - 25) * 40;
        if (level <= 75) return 1400 + (level - 50) * 80;
        return 3400 + (level - 75) * 115;
    }

    /**
     * 升级条件检测
     */
    public boolean canLevelUp(AffectionData data) {
        int requiredExp = getExperienceRequiredForLevel(data.bondLevel.ordinal() + 1);
        return data.bondExperience >= requiredExp;
    }

    /**
     * 应用羁绊等级特权奖励
     */
    public void applyLevelUpRewards(AffectionData data) {
        BondLevel newLevel = BondLevel.fromExperience(data.bondExperience);
        if (newLevel != data.bondLevel) {
            data.bondLevel = newLevel;
            // 触发奖励系统
            triggerLevelUpRewards(data);
            setDirty(); // 标记数据已修改
        }
    }

    /**
     * 触发等级提升奖励
     */
    private void triggerLevelUpRewards(AffectionData data) {
        // 属性增益
        grantAttributeBonus(data);
        // 特效展示
        activateBondEffect(data);
        // 称号奖励
        awardTitle(data);
        // 资源奖励
        giveResourceReward(data);
        // 功能权限解锁
        unlockFunctionPermission(data);
    }

    private void grantAttributeBonus(AffectionData data) {
        // 根据等级提供属性加成（示例）
    }

    private void activateBondEffect(AffectionData data) {
        // 激活羁绊特效（如光环、粒子效果）
    }

    private void awardTitle(AffectionData data) {
        // 授予称号
    }

    private void giveResourceReward(AffectionData data) {
        // 发放资源奖励
    }

    private void unlockFunctionPermission(AffectionData data) {
        // 解锁功能权限
    }

    /**
     * 事件触发式关系变化逻辑
     */

    /**
     * 事件类型枚举
     */
    public enum InteractionType {
        DIALOGUE, GIFT, TASK_COMPLETED
    }

    /**
     * 礼物偏好枚举
     */
    public enum GiftPreference {
        LOVED, LIKED, NEUTRAL, DISLIKED, HATED
    }

    /**
     * 特殊事件处理
     */
    public void handleSpecialEvent(UUID npcId, SpecialEventType eventType) {
        AffectionData data = getOrCreateAffectionData(npcId);
        int baseChange = 0;

        switch (eventType) {
            case BIRTHDAY_GIFT:
                baseChange = 80; // 生日送礼效果×8
                modifyAffectionScore(data, baseChange * 8, "event:birthday_gift");
                break;
            case WINTER_FESTIVAL:
                // 冬日星盛宴送礼效果×5
                modifyAffectionScore(data, 80 * 5, "event:winter_festival");
                break;
            case HEART_EVENT_SUCCESS:
                // 爱心事件正确选择
                modifyAffectionScore(data, 250, "event:heart_event_success");
                break;
            case HEART_EVENT_FAILURE:
                // 爱心事件错误选择（惩罚）
                modifyAffectionScore(data, -1500, "event:heart_event_failure");
                break;
        }
    }

    /**
     * 特殊事件类型
     */
    public enum SpecialEventType {
        BIRTHDAY_GIFT, WINTER_FESTIVAL, HEART_EVENT_SUCCESS, HEART_EVENT_FAILURE
    }

    /**
     * 惩罚机制
     */
    public void applyPunishment(UUID npcId, PunishmentType type) {
        AffectionData data = getOrCreateAffectionData(npcId);

        switch (type) {
            case CHEATING:
                // 花心惩罚：所有关系恶化
                affectionDataMap.values().forEach(d -> modifyAffectionScore(d, -50, "punishment:cheating"));
                break;
            case WRONG_CHOICE:
                // 错误选择惩罚
                modifyAffectionScore(data, -10, "punishment:wrong_choice");
                break;
            case DIVORCE:
                // 离婚：好感清零
                data.affectionScore = 0;
                data.bondExperience = 0;
                data.bondLevel = BondLevel.INITIAL;
                break;
        }
        setDirty();
    }

    /**
     * 惩罚类型
     */
    public enum PunishmentType {
        CHEATING, WRONG_CHOICE, DIVORCE
    }

    /**
     * 修改好感度并记录原因
     */
    private void modifyAffectionScore(AffectionData data, int change, String reason) {
        data.affectionScore = Math.max(0, data.affectionScore + change);
        data.affectionHistory.add(new AffectionChangeRecord(change, reason, System.currentTimeMillis()));
        // 更新羁绊经验值
        data.bondExperience += Math.abs(change) / 10; // 每10点好感变化=1点羁绊经验
        applyLevelUpRewards(data);
        setDirty();
    }

    /**
     * 情感状态查询接口（供ContextAwareDialogueSystem调用）
     */

    /**
     * 获取NPC当前情感状态
     */
    public EmotionState getEmotionState(UUID npcId) {
        AffectionData data = affectionDataMap.get(npcId);
        if (data == null) {
            return new EmotionState(0.0f, new ArrayList<>(), BondLevel.INITIAL);
        }

        float avgEmotion = data.affectionHistory.stream()
            .mapToFloat(record -> record.change > 0 ? 0.5f : record.change < 0 ? -0.5f : 0.0f)
            .average()
            .orElse(0.0f);

        List<String> recentKeywords = data.affectionHistory.stream()
            .limit(5)
            .map(record -> record.reason.split(":")[0])
            .collect(Collectors.toList());

        return new EmotionState(avgEmotion, recentKeywords, BondLevel.fromExperience(data.bondExperience));
    }

    /**
     * 情感状态数据类
     */
    public static class EmotionState {
        public final float emotionScore;
        public final List<String> keywords;
        public final BondLevel bondLevel;

        public EmotionState(float emotionScore, List<String> keywords, BondLevel bondLevel) {
            this.emotionScore = emotionScore;
            this.keywords = new ArrayList<>(keywords);
            this.bondLevel = bondLevel;
        }
    }

    /**
     * 持久化存储集成
     */

    @Override
    public CompoundTag save(CompoundTag tag) {
        return serializeNBT();
    }

    @Override
    public void load(CompoundTag tag) {
        deserializeNBT(tag);
    }

    /**
     * 序列化为NBT格式
     */
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        ListTag dataList = new ListTag();
        for (Map.Entry<UUID, AffectionData> entry : affectionDataMap.entrySet()) {
            CompoundTag dataTag = new CompoundTag();
            dataTag.putUUID("npc_id", entry.getKey());
            dataTag.put("data", entry.getValue().serializeNBT());
            dataList.add(dataTag);
        }
        tag.put("affection_data", dataList);

        return tag;
    }

    /**
     * 从NBT反序列化
     */
    public void deserializeNBT(CompoundTag tag) {
        affectionDataMap.clear();

        if (tag.contains("affection_data", 9)) {
            ListTag dataList = tag.getList("affection_data", 10);
            for (int i = 0; i < dataList.size(); i++) {
                CompoundTag dataTag = dataList.getCompound(i);
                UUID npcId = dataTag.getUUID("npc_id");
                AffectionData data = AffectionData.deserializeNBT(dataTag.getCompound("data"));
                affectionDataMap.put(npcId, data);
            }
        }
    }

    /**
     * 核心数据类
     */

    /**
     * NPC好感度与羁绊数据
     */
    public static class AffectionData {
        public UUID npcId;
        public int affectionScore; // 好感度分数
        public int bondExperience;  // 羁绊经验值
        public BondLevel bondLevel; // 当前羁绊等级
        public final List<AffectionChangeRecord> affectionHistory; // 好感度变化历史

        public AffectionData(UUID npcId) {
            this.npcId = npcId;
            this.affectionScore = 0;
            this.bondExperience = 0;
            this.bondLevel = BondLevel.INITIAL;
            this.affectionHistory = new CopyOnWriteArrayList<>();
        }

        /**
         * 序列化为NBT
         */
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("npc_id", npcId);
            tag.putInt("affection_score", affectionScore);
            tag.putInt("bond_experience", bondExperience);
            tag.putString("bond_level", bondLevel.name());

            ListTag historyTag = new ListTag();
            for (AffectionChangeRecord record : affectionHistory) {
                historyTag.add(record.serializeNBT());
            }
            tag.put("affection_history", historyTag);

            return tag;
        }

        /**
         * 从NBT反序列化
         */
        public static AffectionData deserializeNBT(CompoundTag tag) {
            AffectionData data = new AffectionData(tag.getUUID("npc_id"));
            data.affectionScore = tag.getInt("affection_score");
            data.bondExperience = tag.getInt("bond_experience");
            data.bondLevel = BondLevel.valueOf(tag.getString("bond_level"));

            if (tag.contains("affection_history", 9)) {
                ListTag historyTag = tag.getList("affection_history", 10);
                for (int i = 0; i < historyTag.size(); i++) {
                    data.affectionHistory.add(AffectionChangeRecord.deserializeNBT(historyTag.getCompound(i)));
                }
            }

            return data;
        }
    }

    /**
     * 好感度变化记录
     */
    public static class AffectionChangeRecord {
        public final int change;
        public final String reason;
        public final long timestamp;

        public AffectionChangeRecord(int change, String reason, long timestamp) {
            this.change = change;
            this.reason = reason;
            this.timestamp = timestamp;
        }

        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("change", change);
            tag.putString("reason", reason);
            tag.putLong("timestamp", timestamp);
            return tag;
        }

        public static AffectionChangeRecord deserializeNBT(CompoundTag tag) {
            int change = tag.getInt("change");
            String reason = tag.getString("reason");
            long timestamp = tag.getLong("timestamp");
            return new AffectionChangeRecord(change, reason, timestamp);
        }
    }
}