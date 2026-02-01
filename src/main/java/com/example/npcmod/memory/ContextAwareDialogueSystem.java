package com.example.npcmod.memory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 上下文感知对话系统
 * 与NPCMemorySystem深度集成，提供长期上下文记忆、动态响应生成和情感倾向识别功能
 */
public class ContextAwareDialogueSystem extends SavedData {
    
    // 情感关键词映射表
    private static final Map<String, Float> POSITIVE_EMOTION_WORDS = new ConcurrentHashMap<>();
    private static final Map<String, Float> NEGATIVE_EMOTION_WORDS = new ConcurrentHashMap<>();
    
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
        NEGATIVE_EMOTION_WORDS.put("讨厌", -0.7f);
    }
    
    // 对话上下文缓存
    private final Map<UUID, DialogueContext> dialogueContexts;
    
    /**
     * 构造函数
     */
    public ContextAwareDialogueSystem() {
        this.dialogueContexts = new ConcurrentHashMap<>();
    }
    
    /**
     * 获取或创建对话上下文
     * @param npcId NPC的UUID
     * @return 对话上下文
     */
    public DialogueContext getOrCreateContext(UUID npcId) {
        return dialogueContexts.computeIfAbsent(npcId, id -> new DialogueContext(id));
    }
    
    /**
     * 处理玩家消息并生成NPC响应
     * @param npcId NPC的UUID
     * @param playerId 玩家的UUID
     * @param playerMessage 玩家消息
     * @param memorySystem NPC记忆系统
     * @return NPC响应
     */
    public String processMessage(UUID npcId, UUID playerId, String playerMessage, NPCMemorySystem memorySystem) {
        DialogueContext context = getOrCreateContext(npcId);
        
        // 获取NPC记忆数据
        NPCMemorySystem.NPCMemoryData memoryData = memorySystem.getMemoryData(npcId);
        if (memoryData == null) {
            // 如果没有记忆数据，创建新的
            memoryData = new NPCMemorySystem.NPCMemoryData();
            memorySystem.setMemoryData(npcId, memoryData);
        }
        
        // 分析情感倾向
        float emotionScore = analyzeEmotion(playerMessage);
        
        // 提取关键词
        List<String> keywords = extractKeywords(playerMessage);
        
        // 创建交互记录
        NPCMemorySystem.InteractionRecord record = new NPCMemorySystem.InteractionRecord();
        record.timestamp = System.currentTimeMillis();
        record.player_message = playerMessage;
        record.emotion_score = emotionScore;
        record.keywords = new CopyOnWriteArrayList<>(keywords);
        
        // 添加到情节记忆
        memoryData.episodicMemory.addInteractionRecord(record);
        
        // 生成响应
        String response = generateResponse(context, memoryData, playerMessage, keywords, emotionScore);
        record.npc_response = response;
        
        // 标记数据为脏，触发保存
        setDirty();
        
        return response;
    }
    
    /**
     * 情感分析器
     * 基于关键词匹配的情感倾向识别
     * @param message 输入消息
     * @return 情感评分 (-1.0 ~ 1.0)
     */
    private float analyzeEmotion(String message) {
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
        
        // 如果没有匹配的情感词，返回0
        if (wordCount == 0) {
            return 0.0f;
        }
        
        // 计算平均情感评分，限制在-1.0到1.0范围内
        float averageScore = totalScore / wordCount;
        return Math.max(-1.0f, Math.min(1.0f, averageScore));
    }
    
    /**
     * 关键词提取器
     * 简单的关键词提取，基于常见词汇和标点符号分割
     * @param message 输入消息
     * @return 关键词列表
     */
    private List<String> extractKeywords(String message) {
        if (message == null || message.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        // 移除标点符号并分割
        String cleanMessage = message.replaceAll("[^\\\\p{L}\\\\p{N}\\\\s]", " ");
        String[] words = cleanMessage.trim().split("\\\\s+");
        
        // 过滤停用词和短词
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
     * 动态响应生成器
     * 基于上下文、记忆数据和情感分析生成个性化响应
     * @param context 对话上下文
     * @param memoryData NPC记忆数据
     * @param playerMessage 玩家消息
     * @param keywords 提取的关键词
     * @param emotionScore 情感评分
     * @return 生成的响应
     */
    private String generateResponse(DialogueContext context, NPCMemorySystem.NPCMemoryData memoryData, 
                                 String playerMessage, List<String> keywords, float emotionScore) {
        // 获取好感度等级
        NPCMemorySystem.AffinityLevel affinityLevel = NPCMemorySystem.AffinityLevel.fromScore(memoryData.affinityScore);
        
        // 获取最近的交互记录
        List<NPCMemorySystem.InteractionRecord> recentRecords = memoryData.episodicMemory.getInteractionRecords();
        
        // 基于情感评分调整语气
        String tonePrefix = "";
        if (emotionScore > 0.5f) {
            tonePrefix = "很高兴听到你说";
        } else if (emotionScore < -0.5f) {
            tonePrefix = "听起来你不太开心";
        } else {
            tonePrefix = "我明白了";
        }
        
        // 基于好感度等级调整响应
        String affinityResponse = "";
        switch (affinityLevel) {
            case STRANGER:
                affinityResponse = "陌生人，有什么我可以帮你的吗？";
                break;
            case ACQUAINTANCE:
                affinityResponse = "朋友，很高兴再次见到你！";
                break;
            case FRIENDLY:
                affinityResponse = "老朋友，今天过得怎么样？";
                break;
            case INTIMATE:
                affinityResponse = "亲爱的，有什么想和我分享的吗？";
                break;
            case BEST_FRIEND:
                affinityResponse = "最好的朋友，我一直都在这里等着你！";
                break;
        }
        
        // 检查是否有相关任务
        String taskResponse = "";
        if (!memoryData.taskProgressMap.isEmpty()) {
            // 获取正在进行的任务
            Optional<NPCMemorySystem.TaskProgress> inProgressTask = memoryData.taskProgressMap.values().stream()
                .filter(task -> task.status == NPCMemorySystem.TaskStatus.IN_PROGRESS)
                .findFirst();
            
            if (inProgressTask.isPresent()) {
                taskResponse = "关于你的任务，进展如何了？";
            }
        }
        
        // 基于关键词生成响应
        String keywordResponse = "";
        if (!keywords.isEmpty()) {
            String firstKeyword = keywords.get(0);
            keywordResponse = "关于\"" + firstKeyword + "\"，我想说";
        }
        
        // 组合响应
        StringBuilder response = new StringBuilder();
        if (emotionScore != 0.0f) {
            response.append(tonePrefix).append("。");
        }
        
        if (!keywordResponse.isEmpty()) {
            response.append(keywordResponse).append(" ");
        }
        
        // 如果有任务相关响应，优先使用
        if (!taskResponse.isEmpty()) {
            response.append(taskResponse);
        } else {
            response.append(affinityResponse);
        }
        
        // 如果响应为空，使用默认响应
        if (response.length() == 0) {
            response.append("你好！有什么我可以帮忙的吗？");
        }
        
        return response.toString();
    }
    
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
     * @return NBT复合标签
     */
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        
        ListTag contextsTag = new ListTag();
        for (Map.Entry<UUID, DialogueContext> entry : dialogueContexts.entrySet()) {
            CompoundTag contextTag = new CompoundTag();
            contextTag.putUUID("npc_id", entry.getKey());
            contextTag.put("context", entry.getValue().serializeNBT());
            contextsTag.add(contextTag);
        }
        tag.put("dialogue_contexts", contextsTag);
        
        return tag;
    }
    
    /**
     * 从NBT反序列化
     * @param tag NBT复合标签
     */
    public void deserializeNBT(CompoundTag tag) {
        dialogueContexts.clear();
        
        if (tag.contains("dialogue_contexts", 9)) {
            ListTag contextsTag = tag.getList("dialogue_contexts", 10);
            for (int i = 0; i < contextsTag.size(); i++) {
                CompoundTag contextTag = contextsTag.getCompound(i);
                UUID npcId = contextTag.getUUID("npc_id");
                DialogueContext context = DialogueContext.deserializeNBT(contextTag.getCompound("context"));
                dialogueContexts.put(npcId, context);
            }
        }
    }
    
    /**
     * 对话上下文管理器
     * 存储单个NPC的对话状态信息
     */
    public static class DialogueContext {
        private UUID npcId;
        private long lastInteractionTime;
        private String lastPlayerMessage;
        private String lastNpcResponse;
        private Map<String, Object> contextVariables;
        
        public DialogueContext(UUID npcId) {
            this.npcId = npcId;
            this.lastInteractionTime = 0;
            this.contextVariables = new ConcurrentHashMap<>();
        }
        
        public UUID getNpcId() {
            return npcId;
        }
        
        public long getLastInteractionTime() {
            return lastInteractionTime;
        }
        
        public void setLastInteractionTime(long lastInteractionTime) {
            this.lastInteractionTime = lastInteractionTime;
        }
        
        public String getLastPlayerMessage() {
            return lastPlayerMessage;
        }
        
        public void setLastPlayerMessage(String lastPlayerMessage) {
            this.lastPlayerMessage = lastPlayerMessage;
        }
        
        public String getLastNpcResponse() {
            return lastNpcResponse;
        }
        
        public void setLastNpcResponse(String lastNpcResponse) {
            this.lastNpcResponse = lastNpcResponse;
        }
        
        public Map<String, Object> getContextVariables() {
            return contextVariables;
        }
        
        public void setContextVariable(String key, Object value) {
            contextVariables.put(key, value);
        }
        
        public Object getContextVariable(String key) {
            return contextVariables.get(key);
        }
        
        /**
         * 序列化为NBT格式
         * @return NBT复合标签
         */
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("last_interaction_time", lastInteractionTime);
            if (lastPlayerMessage != null) {
                tag.putString("last_player_message", lastPlayerMessage);
            }
            if (lastNpcResponse != null) {
                tag.putString("last_npc_response", lastNpcResponse);
            }
            
            // 序列化上下文变量（仅支持字符串）
            CompoundTag variablesTag = new CompoundTag();
            for (Map.Entry<String, Object> entry : contextVariables.entrySet()) {
                if (entry.getValue() instanceof String) {
                    variablesTag.putString(entry.getKey(), (String) entry.getValue());
                }
            }
            tag.put("context_variables", variablesTag);
            
            return tag;
        }
        
        /**
         * 从NBT反序列化
         * @param tag NBT复合标签
         * @return 对话上下文
         */
        public static DialogueContext deserializeNBT(CompoundTag tag) {
            DialogueContext context = new DialogueContext(UUID.randomUUID()); // 临时ID，会被覆盖
            
            if (tag.contains("last_interaction_time", 4)) {
                context.lastInteractionTime = tag.getLong("last_interaction_time");
            }
            
            if (tag.contains("last_player_message", 8)) {
                context.lastPlayerMessage = tag.getString("last_player_message");
            }
            
            if (tag.contains("last_npc_response", 8)) {
                context.lastNpcResponse = tag.getString("last_npc_response");
            }
            
            if (tag.contains("context_variables", 10)) {
                CompoundTag variablesTag = tag.getCompound("context_variables");
                for (String key : variablesTag.getAllKeys()) {
                    context.contextVariables.put(key, variablesTag.getString(key));
                }
            }
            
            return context;
        }
    }
    
    /**
     * 记忆系统访问代理
     * 封装对NPCMemorySystem的访问
     */
    public static class MemorySystemProxy {
        private final NPCMemorySystem memorySystem;
        
        public MemorySystemProxy(NPCMemorySystem memorySystem) {
            this.memorySystem = memorySystem;
        }
        
        /**
         * 获取NPC的好感度等级
         * @param npcId NPC的UUID
         * @return 好感度等级
         */
        public NPCMemorySystem.AffinityLevel getAffinityLevel(UUID npcId) {
            NPCMemorySystem.NPCMemoryData memoryData = memorySystem.getMemoryData(npcId);
            if (memoryData != null) {
                return NPCMemorySystem.AffinityLevel.fromScore(memoryData.affinityScore);
            }
            return NPCMemorySystem.AffinityLevel.STRANGER;
        }
        
        /**
         * 获取交互历史记录
         * @param npcId NPC的UUID
         * @return 交互记录列表
         */
        public List<NPCMemorySystem.InteractionRecord> getInteractionRecords(UUID npcId) {
            NPCMemorySystem.NPCMemoryData memoryData = memorySystem.getMemoryData(npcId);
            if (memoryData != null && memoryData.episodicMemory != null) {
                return memoryData.episodicMemory.getInteractionRecords();
            }
            return new ArrayList<>();
        }
        
        /**
         * 获取任务状态
         * @param npcId NPC的UUID
         * @param taskId 任务ID
         * @return 任务进度
         */
        public NPCMemorySystem.TaskProgress getTaskProgress(UUID npcId, String taskId) {
            NPCMemorySystem.NPCMemoryData memoryData = memorySystem.getMemoryData(npcId);
            if (memoryData != null && memoryData.taskProgressMap != null) {
                return memoryData.taskProgressMap.get(taskId);
            }
            return null;
        }
        
        /**
         * 更新好感度分数
         * @param npcId NPC的UUID
         * @param scoreChange 分数变化
         */
        public void updateAffinityScore(UUID npcId, int scoreChange) {
            NPCMemorySystem.NPCMemoryData memoryData = memorySystem.getMemoryData(npcId);
            if (memoryData != null) {
                memoryData.affinityScore = Math.max(0, Math.min(100, memoryData.affinityScore + scoreChange));
                memorySystem.setDirty();
            }
        }
    }
}