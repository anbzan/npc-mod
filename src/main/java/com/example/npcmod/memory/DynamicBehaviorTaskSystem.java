package com.example.npcmod.memory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 动态行为与任务生成系统主控类，协调行为决策与任务生成流程。
 * 采用单例模式，确保全局唯一实例。
 */
public class DynamicBehaviorEngine {
    private static final Logger LOGGER = Logger.getLogger("NPC.Behavior");
    private static final DynamicBehaviorEngine INSTANCE = new DynamicBehaviorEngine();

    private final BehaviorDecisioner behaviorDecisioner;
    private final TaskGenerator taskGenerator;
    private final PersonalizationEngine personalizationEngine;
    private final BehaviorPersistenceManager persistenceManager;
    private final EventDrivenTaskListener eventListener;
    private final Set<String> initializedNpcs;

    private DynamicBehaviorEngine() {
        this.behaviorDecisioner = new BehaviorDecisioner();
        this.taskGenerator = new TaskGenerator();
        this.personalizationEngine = new PersonalizationEngine();
        this.persistenceManager = new BehaviorPersistenceManager();
        this.eventListener = new EventDrivenTaskListener(this);
        this.initializedNpcs = ConcurrentHashMap.newKeySet();
    }

    /**
     * 获取系统单例实例
     * @return DynamicBehaviorEngine 实例
     */
    public static DynamicBehaviorEngine getInstance() {
        return INSTANCE;
    }

    /**
     * 初始化系统，注册事件监听器并准备各子模块
     */
    public synchronized void initialize() {
        eventListener.registerWithEventBus();
        LOGGER.info("DynamicBehaviorEngine initialized successfully.");
    }

    /**
     * 主循环调用方法，执行NPC的行为决策与任务生成
     * @param npc 目标NPC对象
     */
    public void updateBehavior(NPC npc) {
        Objects.requireNonNull(npc, "NPC cannot be null");

        String npcId = npc.getId();
        try {
            // 恢复或初始化行为状态
            if (!initializedNpcs.contains(npcId)) {
                initializeNpcState(npc);
            }

            BehaviorState state = persistenceManager.loadState(npc);
            BehaviorAction action = behaviorDecisioner.decideNextAction(npc);

            if (action == BehaviorAction.GENERATE_TASK) {
                GameTask task = taskGenerator.generateTask(npc, selectTaskType(npc), determineDifficulty(npc));
                if (task != null) {
                    npc.addTask(task);
                    LOGGER.info("Generated task for NPC " + npcId + ": " + task.getTitle());
                    ContextAwareDialogueSystem.updateContext(npcId, "task_assigned");
                }
            } else if (action == BehaviorAction.EXECUTE_PENDING_TASKS && !state.getPendingTasks().isEmpty()) {
                // 任务执行逻辑由游戏系统处理，此处仅标记
                LOGGER.fine("NPC " + npcId + " has pending tasks to execute.");
            }

            // 更新状态
            state.setCurrentAction(action);
            state.setLastDecisionTime(System.currentTimeMillis());
            persistenceManager.saveState(npc, state);

        } catch (Exception e) {
            LOGGER.severe("Error updating behavior for NPC " + npcId + ": " + e.getMessage());
            throw new BehaviorException("Failed to update NPC behavior", e);
        }
    }

    /**
     * 响应游戏事件生成任务
     * @param event 触发事件
     * @param npc   目标NPC
     * @return 生成的游戏任务
     */
    public GameTask generateEventDrivenTask(GameEvent event, NPC npc) {
        Objects.requireNonNull(event, "GameEvent cannot be null");
        Objects.requireNonNull(npc, "NPC cannot be null");

        try {
            TaskType type = mapEventToTaskType(event);
            TaskDifficultyLevel difficulty = determineDifficulty(npc);
            GameTask task = taskGenerator.generateTask(npc, type, difficulty);

            if (task != null) {
                LOGGER.info("Event-driven task generated for NPC " + npc.getId() + " due to " + event.getType());
                NPCMemorySystem_Complete.getInstance().updateMemory(
                        npc.getId(),
                        new MemoryEntry("TASK_GENERATED", System.currentTimeMillis(), "positive", event.getType().name())
                );
            }

            return task;
        } catch (Exception e) {
            LOGGER.warning("Failed to generate event-driven task: " + e.getMessage());
            return null;
        }
    }

    private void initializeNpcState(NPC npc) {
        String npcId = npc.getId();
        if (persistenceManager.isStateAvailable(npc)) {
            LOGGER.fine("Restoring behavior state for NPC " + npcId);
        } else {
            BehaviorState initialState = new BehaviorState();
            initialState.setNpcId(npcId);
            initialState.setPendingTasks(new ArrayList<>());
            initialState.setContextVariables(new HashMap<>());
            persistenceManager.saveState(npc, initialState);
            LOGGER.fine("Initialized default behavior state for NPC " + npcId);
        }
        initializedNpcs.add(npcId);
    }

    private TaskType selectTaskType(NPC npc) {
        List<TaskType> preferredTypes = personalizationEngine.getPreferredTaskTypes(npc);
        return preferredTypes.isEmpty() ? TaskType.COLLECTION : preferredTypes.get(0);
    }

    private TaskDifficultyLevel determineDifficulty(NPC npc) {
        int affectionLevel = AffectionBondsSystem.getAffectionLevel(npc.getId(), npc.getPlayerId());
        if (affectionLevel > 70) return TaskDifficultyLevel.ELITE;
        if (affectionLevel > 50) return TaskDifficultyLevel.HARD;
        if (affectionLevel > 30) return TaskDifficultyLevel.MEDIUM;
        return TaskDifficultyLevel.EASY;
    }

    private TaskType mapEventToTaskType(GameEvent event) {
        return switch (event.getType()) {
            case PLAYER_LEVEL_UP -> TaskType.COMBAT;
            case WEATHER_STORM -> TaskType.COLLECTION;
            case NPC_INJURED -> TaskType.ESCORT;
            case PLAYER_ENTERED_AREA -> TaskType.DIALOGUE;
            case TREASURE_FOUND -> TaskType.EXPLORATION;
            default -> TaskType.COLLECTION;
        };
    }
}

/**
 * 行为决策器，负责NPC自主行为决策逻辑
 */
class BehaviorDecisioner {
    private static final Logger LOGGER = Logger.getLogger("NPC.Behavior");

    /**
     * 根据NPC当前状态决定下一步行为
     * @param npc 目标NPC
     * @return 决策的行为动作
     */
    public BehaviorAction decideNextAction(NPC npc) {
        Objects.requireNonNull(npc, "NPC cannot be null");

        try {
            // 检查是否有待执行任务
            List<GameTask> tasks = npc.getTasks();
            if (tasks != null && !tasks.isEmpty()) {
                return BehaviorAction.EXECUTE_PENDING_TASKS;
            }

            double priority = evaluatePriority(npc);
            LOGGER.fine("Behavior priority score for NPC " + npc.getId() + ": " + priority);

            // 决策阈值可配置化
            return priority > 0.6 ? BehaviorAction.GENERATE_TASK : BehaviorAction.WAIT;
        } catch (Exception e) {
            LOGGER.severe("Error in behavior decision for NPC " + npc.getId() + ": " + e.getMessage());
            return BehaviorAction.WAIT;
        }
    }

    /**
     * 计算行为优先级分数
     * @param npc 目标NPC
     * @return 优先级分数 [0.0, 1.0]
     */
    public double evaluatePriority(NPC npc) {
        double urgency = calculateUrgencyFactor(npc);
        double affection = AffectionBondsSystem.getAffectionLevel(npc.getId(), npc.getPlayerId()) / 100.0;
        double memoryInfluence = calculateMemoryInfluence(npc);
        double contextMatch = calculateContextMatch(npc);

        // 权重可根据NPC性格配置调整
        return 0.3 * urgency + 0.4 * affection + 0.2 * memoryInfluence + 0.1 * contextMatch;
    }

    private double calculateUrgencyFactor(NPC npc) {
        // 简化实现：基于时间周期判断（如每日末尾提升活跃度）
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        return (hour >= 18 || hour <= 6) ? 0.8 : 0.3; // 晚间更可能行动
    }

    private double calculateMemoryInfluence(NPC npc) {
        List<MemoryEntry> memories = NPCMemorySystem_Complete.getInstance().getMemoryData(npc.getId());
        if (memories == null || memories.isEmpty()) return 0.1;

        // 统计近期积极记忆比例
        long recentCount = memories.stream()
                .filter(m -> System.currentTimeMillis() - m.getTimestamp() < 86400000) // 24小时内
                .count();

        long positiveCount = memories.stream()
                .filter(m -> "positive".equals(m.getEmotionTag()))
                .count();

        return recentCount > 0 ? (positiveCount / (double) recentCount) * 0.5 : 0.1;
    }

    private double calculateContextMatch(NPC npc) {
        Map<String, Object> context = ContextAwareDialogueSystem.getContext(npc.getId());
        if (context == null || context.isEmpty()) return 0.0;

        Object intent = context.get("player_intent");
        return "seek_help".equals(intent) ? 0.7 : 0.2;
    }
}

/**
 * 任务生成器，负责生成事件驱动与周期性任务
 */
class TaskGenerator {
    private static final Logger LOGGER = Logger.getLogger("NPC.Behavior");
    private final Map<TaskType, List<TaskTemplate>> templatePool;

    public TaskGenerator() {
        this.templatePool = initializeTemplatePool();
    }

    /**
     * 生成指定类型与难度的任务
     * @param npc        目标NPC
     * @param type       任务类型
     * @param difficulty 任务难度
     * @return 生成的游戏任务
     */
    public GameTask generateTask(NPC npc, TaskType type, TaskDifficultyLevel difficulty) {
        Objects.requireNonNull(npc, "NPC cannot be null");
        Objects.requireNonNull(type, "TaskType cannot be null");
        Objects.requireNonNull(difficulty, "Difficulty cannot be null");

        try {
            TaskTemplate template = selectTemplate(npc, type);
            if (template == null) {
                LOGGER.warning("No suitable template found for NPC " + npc.getId() + " and type " + type);
                return null;
            }

            GameTask task = new GameTask();
            task.setTitle(template.getTitle());
            task.setDescription(instantiateDescription(template.getDescription(), npc));
            task.setType(type);
            task.setDifficulty(difficulty);
            task.setReward(template.getBaseReward() * difficulty.getMultiplier());
            task.setTargetNpcId(npc.getId());

            LOGGER.info("Task generated: " + task.getTitle() + " [Type=" + type + ", Difficulty=" + difficulty + "]");
            return task;
        } catch (Exception e) {
            LOGGER.severe("Failed to generate task: " + e.getMessage());
            return null;
        }
    }

    /**
     * 生成周期性任务（如每日任务）
     * @param npc 目标NPC
     * @return 任务列表
     */
    public List<GameTask> generatePeriodicTask(NPC npc) {
        List<GameTask> tasks = new ArrayList<>();
        Random random = new Random();

        // 每日生成1-2个任务
        int count = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            TaskType type = TaskType.values()[random.nextInt(TaskType.values().length)];
            TaskDifficultyLevel difficulty = determinePeriodicDifficulty(npc);
            GameTask task = generateTask(npc, type, difficulty);
            if (task != null) tasks.add(task);
        }

        LOGGER.info("Generated " + tasks.size() + " periodic tasks for NPC " + npc.getId());
        return tasks;
    }

    /**
     * 基于NPC特征选择任务模板
     * @param npc  目标NPC
     * @param type 任务类型
     * @return 选中的模板
     */
    public TaskTemplate selectTemplate(NPC npc, TaskType type) {
        List<TaskTemplate> templates = templatePool.getOrDefault(type, Collections.emptyList());
        if (templates.isEmpty()) return null;

        // 简单随机选择，可扩展为基于推荐分数选择
        return templates.get(new Random().nextInt(templates.size()));
    }

    private TaskDifficultyLevel determinePeriodicDifficulty(NPC npc) {
        int level = AffectionBondsSystem.getAffectionLevel(npc.getId(), npc.getPlayerId());
        return level > 60 ? TaskDifficultyLevel.HARD : TaskDifficultyLevel.MEDIUM;
    }

    private String instantiateDescription(String template, NPC npc) {
        return template.replace("{npcName}", npc.getName())
                .replace("{playerName}", npc.getPlayerName());
    }

    private Map<TaskType, List<TaskTemplate>> initializeTemplatePool() {
        Map<TaskType, List<TaskTemplate>> pool = new HashMap<>();

        // COLLECTION templates
        pool.put(TaskType.COLLECTION, Arrays.asList(
                new TaskTemplate("收集{npcName}需要的材料", "请帮{npcName}找到3个蓝蘑菇", 50, TaskType.COLLECTION),
                new TaskTemplate("采集草药", "在森林中采集5株治疗草药带给{npcName}", 40, TaskType.COLLECTION)
        ));

        // ESCORT templates
        pool.put(TaskType.ESCORT, Arrays.asList(
                new TaskTemplate("护送安全", "{npcName}需要前往村庄，请护送他", 80, TaskType.ESCORT)
        ));

        // DIALOGUE templates
        pool.put(TaskType.DIALOGUE, Arrays.asList(
                new TaskTemplate("倾听故事", "与{npcName}对话，了解他的过去", 30, TaskType.DIALOGUE)
        ));

        // COMBAT templates
        pool.put(TaskType.COMBAT, Arrays.asList(
                new TaskTemplate("清除威胁", "清除山洞中的狼群，保护{npcName}", 100, TaskType.COMBAT)
        ));

        // EXPLORATION templates
        pool.put(TaskType.EXPLORATION, Arrays.asList(
                new TaskTemplate("发现秘密", "探索北部山脉，找到隐藏洞穴", 90, TaskType.EXPLORATION)
        ));

        return pool;
    }
}

/**
 * 个性化引擎，基于好感度与历史交互进行任务推荐
 */
class PersonalizationEngine {
    private static final Logger LOGGER = Logger.getLogger("NPC.Behavior");
    private static final double AFFECTION_WEIGHT = 0.4;
    private static final double HISTORY_WEIGHT = 0.3;
    private static final double PREFERENCE_WEIGHT = 0.2;
    private static final double CONTEXT_WEIGHT = 0.1;

    /**
     * 计算任务推荐权重
     * @param npc       目标NPC
     * @param template  任务模板
     * @return 推荐得分 [0.0, 1.0]
     */
    public double calculateRecommendationScore(NPC npc, TaskTemplate template) {
        Objects.requireNonNull(npc, "NPC cannot be null");
        Objects.requireNonNull(template, "TaskTemplate cannot be null");

        try {
            double affectionLevel = AffectionBondsSystem.getAffectionLevel(npc.getId(), npc.getPlayerId()) / 100.0;
            double historyScore = calculateCompletionHistoryScore(npc, template);
            double preferenceScore = calculateTypePreferenceScore(npc, template.getType());
            double contextScore = calculateContextSimilarity(npc, template);

            double score = AFFECTION_WEIGHT * affectionLevel +
                    HISTORY_WEIGHT * historyScore +
                    PREFERENCE_WEIGHT * preferenceScore +
                    CONTEXT_WEIGHT * contextScore;

            LOGGER.fine("Recommendation score for NPC " + npc.getId() +
                    " and template '" + template.getTitle() + "': " + score);

            return Math.min(score, 1.0);
        } catch (Exception e) {
            LOGGER.warning("Error calculating recommendation score: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * 获取NPC偏好的任务类型列表
     * @param npc 目标NPC
     * @return 偏好类型列表（按优先级排序）
     */
    public List<TaskType> getPreferredTaskTypes(NPC npc) {
        List<TaskType> preferences = new ArrayList<>();
        String bondType = AffectionBondsSystem.getBondType(npc.getId(), npc.getPlayerId());

        switch (bondType) {
            case "friend" -> {
                preferences.add(TaskType.DIALOGUE);
                preferences.add(TaskType.ESCORT);
            }
            case "ally" -> {
                preferences.add(TaskType.COMBAT);
                preferences.add(TaskType.EXPLORATION);
            }
            case "enemy" -> preferences.add(TaskType.COMBAT);
            default -> preferences.add(TaskType.COLLECTION);
        }

        return preferences;
    }

    /**
     * 判断该任务类型是否曾被接受
     * @param npc      目标NPC
     * @param template 任务模板
     * @return 是否曾接受
     */
    public boolean isTaskHistoricallyAccepted(NPC npc, TaskTemplate template) {
        List<MemoryEntry> memories = NPCMemorySystem_Complete.getInstance().getMemoryData(npc.getId());
        if (memories == null) return false;

        return memories.stream()
                .anyMatch(m -> m.getEventType().equals("TASK_COMPLETED") &&
                        m.getDetails().contains(template.getType().name()));
    }

    private double calculateCompletionHistoryScore(NPC npc, TaskTemplate template) {
        List<MemoryEntry> memories = NPCMemorySystem_Complete.getInstance().getMemoryData(npc.getId());
        if (memories == null) return 0.1;

        long total = memories.stream().filter(m -> m.getEventType().equals("TASK_COMPLETED")).count();
        long matched = memories.stream()
                .filter(m -> m.getEventType().equals("TASK_COMPLETED") &&
                        m.getDetails().contains(template.getType().name()))
                .count();

        return total > 0 ? (matched / (double) total) : 0.1;
    }

    private double calculateTypePreferenceScore(NPC npc, TaskType type) {
        List<TaskType> preferred = getPreferredTaskTypes(npc);
        return preferred.contains(type) ? 0.8 : 0.3;
    }

    private double calculateContextSimilarity(NPC npc, TaskTemplate template) {
        Map<String, Object> context = ContextAwareDialogueSystem.getContext(npc.getId());
        if (context == null) return 0.0;

        Object topic = context.get("current_topic");
        return topic != null && template.getTitle().toLowerCase().contains(topic.toString().toLowerCase()) ? 0.6 : 0.1;
    }
}

/**
 * 行为状态持久化管理器
 */
class BehaviorPersistenceManager {
    private static final Logger LOGGER = Logger.getLogger("NPC.Behavior");

    /**
     * 持久化行为状态
     * @param npc   目标NPC
     * @param state 行为状态
     */
    public void saveState(NPC npc, BehaviorState state) {
        Objects.requireNonNull(npc, "NPC cannot be null");
        Objects.requireNonNull(state, "BehaviorState cannot be null");

        try {
            // 模拟序列化存储
            String serialized = serializeState(state);
            StorageSystem.save("behavior_state_" + npc.getId(), serialized);
            LOGGER.fine("Behavior state saved for NPC " + npc.getId());
        } catch (Exception e) {
            LOGGER.warning("Failed to save behavior state for NPC " + npc.getId() + ": " + e.getMessage());
        }
    }

    /**
     * 恢复行为状态
     * @param npc 目标NPC
     * @return 行为状态
     */
    public BehaviorState loadState(NPC npc) {
        Objects.requireNonNull(npc, "NPC cannot be null");

        try {
            String data = StorageSystem.load("behavior_state_" + npc.getId());
            if (data == null || data.isEmpty()) {
                LOGGER.fine("No saved state found for NPC " + npc.getId());
                return createDefaultState(npc);
            }

            BehaviorState state = deserializeState(data);
            validateState(state, npc);
            LOGGER.fine("Behavior state loaded for NPC " + npc.getId());
            return state;
        } catch (Exception e) {
            LOGGER.warning("Failed to load behavior state for NPC " + npc.getId() + ": " + e.getMessage());
            return createDefaultState(npc);
        }
    }

    /**
     * 检查是否存在已保存的状态
     * @param npc 目标NPC
     * @return 是否存在
     */
    public boolean isStateAvailable(NPC npc) {
        return StorageSystem.exists("behavior_state_" + npc.getId());
    }

    private String serializeState(BehaviorState state) {
        // 简化JSON风格序列化
        return String.format("{\"npcId\":\"%s\",\"lastDecisionTime\":%d,\"currentAction\":\"%s\"}",
                state.getNpcId(), state.getLastDecisionTime(), state.getCurrentAction());
    }

    private BehaviorState deserializeState(String data) {
        // 简化反序列化（实际应使用JSON库）
        BehaviorState state = new BehaviorState();
        state.setNpcId(extractValue(data, "npcId"));
        state.setLastDecisionTime(Long.parseLong(extractValue(data, "lastDecisionTime")));
        state.setCurrentAction(BehaviorAction.valueOf(extractValue(data, "currentAction")));
        return state;
    }

    private String extractValue(String data, String key) {
        String pattern = "\"" + key + "\":\"?([^,\"]+)\"?";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(data);
        return m.find() ? m.group(1) : "";
    }

    private void validateState(BehaviorState state, NPC npc) {
        if (!npc.getId().equals(state.getNpcId())) {
            throw new BehaviorException("State NPC ID mismatch");
        }
    }

    private BehaviorState createDefaultState(NPC npc) {
        BehaviorState state = new BehaviorState();
        state.setNpcId(npc.getId());
        state.setPendingTasks(new ArrayList<>());
        state.setContextVariables(new HashMap<>());
        state.setLastDecisionTime(System.currentTimeMillis());
        state.setCurrentAction(BehaviorAction.WAIT);
        return state;
    }
}

/**
 * 事件驱动任务监听器
 */
class EventDrivenTaskListener {
    private final DynamicBehaviorEngine engine;

    public EventDrivenTaskListener(DynamicBehaviorEngine engine) {
        this.engine = Objects.requireNonNull(engine);
    }

    /**
     * 注册到游戏事件总线
     */
    public void registerWithEventBus() {
        GameEventBus.getInstance().registerListener(this::onGameEvent);
        LOGGER.info("EventDrivenTaskListener registered with GameEventBus");
    }

    private void onGameEvent(GameEvent event) {
        try {
            // 获取所有活跃NPC并尝试生成任务
            List<NPC> npcs = NPCRegistry.getActiveNPCs();
            for (NPC npc : npcs) {
                // 根据事件类型决定是否触发
                if (shouldTriggerForEvent(event.getType())) {
                    engine.generateEventDrivenTask(event, npc);
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error handling game event: " + e.getMessage());
        }
    }

    private boolean shouldTriggerForEvent(GameEventType type) {
        return switch (type) {
            case PLAYER_LEVEL_UP, WEATHER_STORM, NPC_INJURED, PLAYER_ENTERED_AREA -> true;
            default -> false;
        };
    }
}

/**
 * 任务模板基类
 */
class TaskTemplate {
    private String title;
    private String description;
    private int baseReward;
    private TaskType type;

    public TaskTemplate() {}

    public TaskTemplate(String title, String description, int baseReward, TaskType type) {
        this.title = title;
        this.description = description;
        this.baseReward = baseReward;
        this.type = type;
    }

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getBaseReward() { return baseReward; }
    public void setBaseReward(int baseReward) { this.baseReward = baseReward; }

    public TaskType getType() { return type; }
    public void setType(TaskType type) { this.type = type; }
}

/**
 * 任务难度等级枚举
 */
enum TaskDifficultyLevel {
    EASY(1.0),
    MEDIUM(1.5),
    HARD(2.0),
    ELITE(3.0);

    private final double multiplier;

    TaskDifficultyLevel(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }
}

/**
 * 任务类型枚举
 */
enum TaskType {
    COLLECTION, ESCORT, DIALOGUE, COMBAT, EXPLORATION
}

/**
 * 行为状态数据结构
 */
class BehaviorState {
    private String npcId;
    private List<GameTask> pendingTasks;
    private BehaviorAction currentAction;
    private long lastDecisionTime;
    private Map<String, Object> contextVariables;
    private String serializedMemorySnapshot;

    // Getters and Setters
    public String getNpcId() { return npcId; }
    public void setNpcId(String npcId) { this.npcId = npcId; }

    public List<GameTask> getPendingTasks() { return pendingTasks; }
    public void setPendingTasks(List<GameTask> pendingTasks) { this.pendingTasks = pendingTasks; }

    public BehaviorAction getCurrentAction() { return currentAction; }
    public void setCurrentAction(BehaviorAction currentAction) { this.currentAction = currentAction; }

    public long getLastDecisionTime() { return lastDecisionTime; }
    public void setLastDecisionTime(long lastDecisionTime) { this.lastDecisionTime = lastDecisionTime; }

    public Map<String, Object> getContextVariables() { return contextVariables; }
    public void setContextVariables(Map<String, Object> contextVariables) { this.contextVariables = contextVariables; }

    public String getSerializedMemorySnapshot() { return serializedMemorySnapshot; }
    public void setSerializedMemorySnapshot(String serializedMemorySnapshot) { this.serializedMemorySnapshot = serializedMemorySnapshot; }
}

/**
 * 行为动作枚举
 */
enum BehaviorAction {
    WAIT, GENERATE_TASK, EXECUTE_PENDING_TASKS, REST, MOVE
}

/**
 * 自定义行为异常
 */
class BehaviorException extends RuntimeException {
    public BehaviorException(String message) {
        super(message);
    }

    public BehaviorException(String message, Throwable cause) {
        super(message, cause);
    }
}

// 假设的外部系统接口（仅用于编译通过）
class NPCMemorySystem_Complete {
    private static final NPCMemorySystem_Complete INSTANCE = new NPCMemorySystem_Complete();

    public static NPCMemorySystem_Complete getInstance() {
        return INSTANCE;
    }

    public List<MemoryEntry> getMemoryData(String npcId) {
        return new ArrayList<>();
    }

    public void updateMemory(String npcId, MemoryEntry entry) {}
}

class ContextAwareDialogueSystem {
    public static Map<String, Object> getContext(String npcId) {
        return new HashMap<>();
    }

    public static void updateContext(String npcId, String state) {}
}

class AffectionBondsSystem {
    public static int getAffectionLevel(String npcId, String playerId) {
        return 50;
    }

    public static String getBondType(String npcId, String playerId) {
        return "neutral";
    }
}

class StorageSystem {
    private static final Map<String, String> STORAGE = new HashMap<>();

    public static void save(String key, String data) {
        STORAGE.put(key, data);
    }

    public static String load(String key) {
        return STORAGE.get(key);
    }

    public static boolean exists(String key) {
        return STORAGE.containsKey(key);
    }
}

// 假设的数据模型类
class NPC {
    private String id;
    private String name;
    private String playerId;
    private String playerName;
    private List<GameTask> tasks;

    public NPC(String id, String name, String playerId) {
        this.id = id;
        this.name = name;
        this.playerId = playerId;
        this.tasks = new ArrayList<>();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public List<GameTask> getTasks() { return tasks; }
    public void setTasks(List<GameTask> tasks) { this.tasks = tasks; }
    public void addTask(GameTask task) { this.tasks.add(task); }
}

class GameTask {
    private String title;
    private String description;
    private TaskType type;
    private TaskDifficultyLevel difficulty;
    private int reward;
    private String targetNpcId;

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskType getType() { return type; }
    public void setType(TaskType type) { this.type = type; }

    public TaskDifficultyLevel getDifficulty() { return difficulty; }
    public void setDifficulty(TaskDifficultyLevel difficulty) { this.difficulty = difficulty; }

    public int getReward() { return reward; }
    public void setReward(int reward) { this.reward = reward; }

    public String getTargetNpcId() { return targetNpcId; }
    public void setTargetNpcId(String targetNpcId) { this.targetNpcId = targetNpcId; }
}

class MemoryEntry {
    private String eventType;
    private long timestamp;
    private String emotionTag;
    private String details;

    public MemoryEntry() {}

    public MemoryEntry(String eventType, long timestamp, String emotionTag, String details) {
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.emotionTag = emotionTag;
        this.details = details;
    }

    // Getters and Setters
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getEmotionTag() { return emotionTag; }
    public void setEmotionTag(String emotionTag) { this.emotionTag = emotionTag; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}

enum GameEventType {
    PLAYER_LEVEL_UP, WEATHER_STORM, NPC_INJURED, PLAYER_ENTERED_AREA, TREASURE_FOUND
}

class GameEvent {
    private GameEventType type;
    private Map<String, Object> data;

    public GameEvent(GameEventType type) {
        this.type = type;
        this.data = new HashMap<>();
    }

    // Getters and Setters
    public GameEventType getType() { return type; }
    public void setType(GameEventType type) { this.type = type; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}

class GameEventBus {
    private static final GameEventBus INSTANCE = new GameEventBus();
    private final List<java.util.function.Consumer<GameEvent>> listeners = new ArrayList<>();

    public static GameEventBus getInstance() {
        return INSTANCE;
    }

    public void registerListener(java.util.function.Consumer<GameEvent> listener) {
        listeners.add(listener);
    }

    public void fireEvent(GameEvent event) {
        listeners.forEach(l -> l.accept(event));
    }
}

class NPCRegistry {
    public static List<NPC> getActiveNPCs() {
        return new ArrayList<>();
    }
}