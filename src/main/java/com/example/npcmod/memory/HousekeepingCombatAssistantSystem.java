package com.example.npcmod.memory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.UUIDUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AI驱动的家政与战斗助手系统
 * 实现NPC的双重角色：家庭服务与战斗辅助，基于现有NPC系统架构深度集成
 * 支持环境感知、好感度决策、任务协同调度与状态持久化
 */
public class HousekeepingCombatAssistantSystem extends SavedData {

    // 系统实例单例
    private static HousekeepingCombatAssistantSystem instance;

    // NPC家政与战斗状态映射表
    private final Map<UUID, AssistantState> assistantStateMap = new ConcurrentHashMap<>();

    // 任务类型枚举扩展（与DynamicBehaviorTaskSystem集成）
    public enum TaskType {
        HOUSEHOLD_CLEANING,
        HOUSEHOLD_COOKING,
        HOUSEHOLD_ORGANIZING,
        COMBAT_GUARD,
        COMBAT_SCOUT,
        COMBAT_COOPERATE
    }

    // 家政任务数据结构
    public static class HouseholdTask {
        private String taskId;
        private TaskType type;
        private int requiredProgress;
        private int currentProgress;
        private long deadline;
        private boolean completed;

        public HouseholdTask(String taskId, TaskType type, int requiredProgress, long deadline) {
            this.taskId = taskId;
            this.type = type;
            this.requiredProgress = requiredProgress;
            this.currentProgress = 0;
            this.deadline = deadline;
            this.completed = false;
        }

        public void updateProgress(int amount) {
            if (!completed) {
                currentProgress = Math.min(requiredProgress, currentProgress + amount);
                completed = (currentProgress >= requiredProgress);
            }
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > deadline;
        }

        // 序列化到NBT
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("task_id", taskId);
            tag.putString("type", type.name());
            tag.putInt("required_progress", requiredProgress);
            tag.putInt("current_progress", currentProgress);
            tag.putLong("deadline", deadline);
            tag.putBoolean("completed", completed);
            return tag;
        }

        // 从NBT反序列化
        public static HouseholdTask deserializeNBT(CompoundTag tag) {
            String taskId = tag.getString("task_id");
            TaskType type = TaskType.valueOf(tag.getString("type"));
            int requiredProgress = tag.getInt("required_progress");
            long deadline = tag.getLong("deadline");
            HouseholdTask task = new HouseholdTask(taskId, type, requiredProgress, deadline);
            task.currentProgress = tag.getInt("current_progress");
            task.completed = tag.getBoolean("completed");
            return task;
        }

        // Getters
        public String getTaskId() { return taskId; }
        public TaskType getType() { return type; }
        public int getRequiredProgress() { return requiredProgress; }
        public int getCurrentProgress() { return currentProgress; }
        public long getDeadline() { return deadline; }
        public boolean isCompleted() { return completed; }
    }

    // 战斗状态数据结构
    public static class CombatState {
        public enum Mode {
            IDLE, GUARDING, SCOUTING, COMBATING, RETREATING
        }

        private Mode currentMode;
        private UUID targetEnemyId;
        private long lastThreatDetectedTime;
        private float threatLevel;
        private int skillCooldownTicks;
        private boolean inPlayerProximity;

        public CombatState() {
            this.currentMode = Mode.IDLE;
            this.targetEnemyId = null;
            this.lastThreatDetectedTime = 0;
            this.threatLevel = 0.0f;
            this.skillCooldownTicks = 0;
            this.inPlayerProximity = false;
        }

        public void updateThreat(float level, UUID enemyId) {
            this.threatLevel = level;
            this.targetEnemyId = enemyId;
            this.lastThreatDetectedTime = System.currentTimeMillis();
            this.currentMode = level > 0.7 ? Mode.COMBATING : Mode.GUARDING;
        }

        public void reduceCooldown(int ticks) {
            skillCooldownTicks = Math.max(0, skillCooldownTicks - ticks);
        }

        public boolean isSkillReady() {
            return skillCooldownTicks == 0;
        }

        // 序列化到NBT
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("current_mode", currentMode.name());
            if (targetEnemyId != null) {
                tag.putUUID("target_enemy_id", targetEnemyId);
            }
            tag.putLong("last_threat_time", lastThreatDetectedTime);
            tag.putFloat("threat_level", threatLevel);
            tag.putInt("cooldown_ticks", skillCooldownTicks);
            tag.putBoolean("in_proximity", inPlayerProximity);
            return tag;
        }

        // 从NBT反序列化
        public static CombatState deserializeNBT(CompoundTag tag) {
            CombatState state = new CombatState();
            state.currentMode = Mode.valueOf(tag.getString("current_mode"));
            if (tag.contains("target_enemy_id")) {
                state.targetEnemyId = tag.getUUID("target_enemy_id");
            }
            state.lastThreatDetectedTime = tag.getLong("last_threat_time");
            state.threatLevel = tag.getFloat("threat_level");
            state.skillCooldownTicks = tag.getInt("cooldown_ticks");
            state.inPlayerProximity = tag.getBoolean("in_proximity");
            return state;
        }

        // Getters and Setters
        public Mode getCurrentMode() { return currentMode; }
        public void setCurrentMode(Mode mode) { this.currentMode = mode; }
        public UUID getTargetEnemyId() { return targetEnemyId; }
        public long getLastThreatDetectedTime() { return lastThreatDetectedTime; }
        public float getThreatLevel() { return threatLevel; }
        public int getSkillCooldownTicks() { return skillCooldownTicks; }
        public boolean isInPlayerProximity() { return inPlayerProximity; }
        public void setInPlayerProximity(boolean inPlayerProximity) { this.inPlayerProximity = inPlayerProximity; }
    }

    // 家政行为执行类
    public static class HouseholdBehavior {
        private final HouseholdTask currentTask;
        private final UUID npcId;
        private int workEfficiency;

        public HouseholdBehavior(UUID npcId, HouseholdTask task) {
            this.npcId = npcId;
            this.currentTask = task;
            this.workEfficiency = 1; // 基础效率
        }

        /**
         * 执行清洁行为
         * @return 进度增量
         */
        public int performCleaning() {
            if (currentTask == null || currentTask.getType() != TaskType.HOUSEHOLD_CLEANING) {
                return 0;
            }
            int progress = 5 * workEfficiency;
            currentTask.updateProgress(progress);
            return progress;
        }

        /**
         * 执行烹饪行为
         * @return 进度增量
         */
        public int performCooking() {
            if (currentTask == null || currentTask.getType() != TaskType.HOUSEHOLD_COOKING) {
                return 0;
            }
            int progress = 3 * workEfficiency;
            currentTask.updateProgress(progress);
            return progress;
        }

        /**
         * 执行整理行为
         * @return 进度增量
         */
        public int performOrganizing() {
            if (currentTask == null || currentTask.getType() != TaskType.HOUSEHOLD_ORGANIZING) {
                return 0;
            }
            int progress = 4 * workEfficiency;
            currentTask.updateProgress(progress);
            return progress;
        }

        /**
         * 评估环境清洁度
         * @return 清洁度评分 (0.0 - 1.0)
         */
        public float evaluateCleanliness() {
            // 简化实现：基于任务进度评估
            if (currentTask == null) return 0.5f;
            return currentTask.getCurrentProgress() / (float) currentTask.getRequiredProgress();
        }

        // Getters
        public HouseholdTask getCurrentTask() { return currentTask; }
        public int getWorkEfficiency() { return workEfficiency; }
        public void setWorkEfficiency(int workEfficiency) { this.workEfficiency = workEfficiency; }
    }

    // 战斗行为执行类
    public static class CombatBehavior {
        private final CombatState combatState;
        private final UUID npcId;
        private final UUID playerId;
        private int combatSkillLevel;

        public CombatBehavior(UUID npcId, UUID playerId, CombatState state) {
            this.npcId = npcId;
            this.playerId = playerId;
            this.combatState = state;
            this.combatSkillLevel = 1;
        }

        /**
         * 执行护卫行为
         * @param playerDistance 玩家距离
         * @return 防御姿态强度
         */
        public float performGuard(float playerDistance) {
            if (playerDistance > 10.0f) {
                combatState.setCurrentMode(CombatState.Mode.IDLE);
                return 0.0f;
            }
            combatState.setInPlayerProximity(true);
            combatState.setCurrentMode(CombatState.Mode.GUARDING);
            return 0.7f + (combatSkillLevel * 0.1f);
        }

        /**
         * 执行侦查行为
         * @param explorationRadius 侦查半径
         * @return 侦查覆盖度
         */
        public float performScouting(int explorationRadius) {
            combatState.setCurrentMode(CombatState.Mode.SCOUTING);
            return explorationRadius * 0.1f;
        }

        /**
         * 执行协同作战行为
         * @param enemyThreatLevel 敌人威胁等级
         * @return 协同贡献度
         */
        public float performCooperation(float enemyThreatLevel) {
            if (combatState.isSkillReady() && enemyThreatLevel > 0.3f) {
                combatState.updateThreat(enemyThreatLevel, UUID.randomUUID()); // 简化实现
                combatState.skillCooldownTicks = 20 * (5 - combatSkillLevel); // 冷却时间
                return 0.8f + (combatSkillLevel * 0.05f);
            }
            return 0.0f;
        }

        // Getters
        public CombatState getCombatState() { return combatState; }
        public int getCombatSkillLevel() { return combatSkillLevel; }
        public void setCombatSkillLevel(int combatSkillLevel) { this.combatSkillLevel = combatSkillLevel; }
    }

    // 自主决策引擎
    public static class DecisionEngine {
        private final UUID npcId;
        private final NPCMemorySystem.NPCMemoryData memoryData;
        private final AffectionBondsSystem affectionSystem;

        public DecisionEngine(UUID npcId, NPCMemorySystem.NPCMemoryData memoryData, AffectionBondsSystem affectionSystem) {
            this.npcId = npcId;
            this.memoryData = memoryData;
            this.affectionSystem = affectionSystem;
        }

        /**
         * 基于环境感知与好感度的决策
         * @param playerPosition 玩家位置
         * @param timeOfDay 时间（小时）
         * @param weather 天气
         * @param isPlayerInDanger 玩家是否处于危险
         * @return 推荐行为类型
         */
        public TaskType makeDecision(double[] playerPosition, int timeOfDay, String weather, boolean isPlayerInDanger) {
            // 获取好感度等级
            AffectionBondsSystem.AffinityLevel affinityLevel = AffectionBondsSystem.AffinityLevel.fromScore(memoryData.affinityScore);
            double affinityWeight = affinityLevel.ordinal() * 0.2; // 好感度越高，越愿意服务

            // 环境因素权重
            double householdUrgency = 0.0;
            double combatUrgency = 0.0;

            // 时间因素：早晚更可能做家政
            if (timeOfDay < 7 || timeOfDay > 19) {
                householdUrgency += 0.3;
            }

            // 天气因素：恶劣天气减少外出
            if ("storm".equals(weather)) {
                combatUrgency -= 0.4;
            }

            // 危险因素：玩家危险时优先战斗
            if (isPlayerInDanger) {
                combatUrgency += 0.8;
            }

            // 综合决策
            double householdScore = householdUrgency + affinityWeight;
            double combatScore = combatUrgency + affinityWeight;

            // 任务冲突解决：优先级规则
            if (combatScore > householdScore + 0.3) {
                // 战斗紧急
                if (combatScore > 0.5) return TaskType.COMBAT_GUARD;
                else return TaskType.COMBAT_SCOUT;
            } else if (householdScore > combatScore + 0.2) {
                // 家政紧急
                return TaskType.HOUSEHOLD_CLEANING; // 默认家政任务
            } else {
                // 平衡状态，基于好感度倾向
                return affinityLevel.ordinal() >= 2 ? TaskType.HOUSEHOLD_CLEANING : TaskType.COMBAT_SCOUT;
            }
        }
    }

    // 状态持久化管理器
    public static class StatePersistenceManager {
        private final HousekeepingCombatAssistantSystem system;

        public StatePersistenceManager(HousekeepingCombatAssistantSystem system) {
            this.system = system;
        }

        /**
         * 保存NPC状态
         * @param npcId NPC的UUID
         * @param state 助手状态
         */
        public void saveState(UUID npcId, AssistantState state) {
            system.assistantStateMap.put(npcId, state);
            system.setDirty();
        }

        /**
         * 加载NPC状态
         * @param npcId NPC的UUID
         * @return 助手状态，若不存在则返回新实例
         */
        public AssistantState loadState(UUID npcId) {
            return system.assistantStateMap.computeIfAbsent(npcId, id -> new AssistantState());
        }

        /**
         * 清除NPC状态
         * @param npcId NPC的UUID
         */
        public void clearState(UUID npcId) {
            system.assistantStateMap.remove(npcId);
            system.setDirty();
        }
    }

    // 接口适配器（与其他系统集成）
    public static class InterfaceAdapter {
        private final HousekeepingCombatAssistantSystem assistantSystem;
        private final NPCMemorySystem memorySystem;
        private final ContextAwareDialogueSystem dialogueSystem;
        private final AffectionBondsSystem affectionSystem;
        private final DynamicBehaviorEngine behaviorEngine;

        public InterfaceAdapter(HousekeepingCombatAssistantSystem assistantSystem,
                              NPCMemorySystem memorySystem,
                              ContextAwareDialogueSystem dialogueSystem,
                              AffectionBondsSystem affectionSystem,
                              DynamicBehaviorEngine behaviorEngine) {
            this.assistantSystem = assistantSystem;
            this.memorySystem = memorySystem;
            this.dialogueSystem = dialogueSystem;
            this.affectionSystem = affectionSystem;
            this.behaviorEngine = behaviorEngine;
        }

        /**
         * 家政任务完成事件
         * @param npcId NPC的UUID
         * @param task 完成的任务
         */
        public void onHouseholdTaskCompleted(UUID npcId, HouseholdTask task) {
            // 更新记忆系统
            NPCMemorySystem.NPCMemoryData memoryData = memorySystem.getMemoryData(npcId);
            if (memoryData != null) {
                memoryData.episodicMemory.addBehaviorLog("家政任务完成: " + task.getType());
            }

            // 提升好感度
            affectionSystem.handleInteractionEvent(npcId, AffectionBondsSystem.InteractionType.TASK_COMPLETED, null);

            // 触发对话反馈
            dialogueSystem.getOrCreateContext(npcId).setContextVariable("last_household_task", task.getType().name());
            dialogueSystem.getOrCreateContext(npcId).setContextVariable("task_result", "success");

            // 标记脏数据
            memorySystem.setDirty();
            affectionSystem.setDirty();
        }

        /**
         * 战斗失误事件
         * @param npcId NPC的UUID
         */
        public void onCombatFailure(UUID npcId) {
            // 降低好感度
            affectionSystem.applyPunishment(npcId, AffectionBondsSystem.PunishmentType.WRONG_CHOICE);

            // 更新记忆
            NPCMemorySystem.NPCMemoryData memoryData = memorySystem.getMemoryData(npcId);
            if (memoryData != null) {
                memoryData.episodicMemory.addBehaviorLog("战斗失误");
            }

            // 触发对话反馈
            dialogueSystem.getOrCreateContext(npcId).setContextVariable("combat_result", "failure");
        }

        /**
         * 生成家政任务
         * @param npc NPC对象
         * @return 生成的任务
         */
        public GameTask generateHouseholdTask(NPC npc) {
            TaskType type = TaskType.HOUSEHOLD_CLEANING;
            TaskDifficultyLevel difficulty = determineDifficulty(npc);
            return behaviorEngine.getTaskGenerator().generateTask(npc, type, difficulty);
        }

        private TaskDifficultyLevel determineDifficulty(NPC npc) {
            int affectionLevel = affectionSystem.getAffectionLevel(npc.getId(), npc.getPlayerId());
            if (affectionLevel > 70) return TaskDifficultyLevel.ELITE;
            if (affectionLevel > 50) return TaskDifficultyLevel.HARD;
            return TaskDifficultyLevel.MEDIUM;
        }
    }

    // 助手状态数据类（存储于NPCMemoryData）
    public static class AssistantState {
        private HouseholdTask currentHouseholdTask;
        private CombatState combatState;
        private long lastUpdateTime;
        private Map<String, Object> contextVariables;

        public AssistantState() {
            this.currentHouseholdTask = null;
            this.combatState = new CombatState();
            this.lastUpdateTime = System.currentTimeMillis();
            this.contextVariables = new ConcurrentHashMap<>();
        }

        // 序列化到NBT
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            if (currentHouseholdTask != null) {
                tag.put("household_task", currentHouseholdTask.serializeNBT());
            }
            tag.put("combat_state", combatState.serializeNBT());
            tag.putLong("last_update_time", lastUpdateTime);

            // 序列化上下文变量（仅支持基本类型）
            CompoundTag contextTag = new CompoundTag();
            for (Map.Entry<String, Object> entry : contextVariables.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    contextTag.putString(entry.getKey(), (String) value);
                } else if (value instanceof Integer) {
                    contextTag.putInt(entry.getKey(), (Integer) value);
                } else if (value instanceof Float) {
                    contextTag.putFloat(entry.getKey(), (Float) value);
                }
            }
            tag.put("context_variables", contextTag);

            return tag;
        }

        // 从NBT反序列化
        public static AssistantState deserializeNBT(CompoundTag tag) {
            AssistantState state = new AssistantState();
            if (tag.contains("household_task", 10)) {
                state.currentHouseholdTask = HouseholdTask.deserializeNBT(tag.getCompound("household_task"));
            }
            state.combatState = CombatState.deserializeNBT(tag.getCompound("combat_state"));
            state.lastUpdateTime = tag.getLong("last_update_time");

            if (tag.contains("context_variables", 10)) {
                CompoundTag contextTag = tag.getCompound("context_variables");
                for (String key : contextTag.getAllKeys()) {
                    switch (contextTag.getId(key)) {
                        case 8: // STRING
                            state.contextVariables.put(key, contextTag.getString(key));
                            break;
                        case 3: // INT
                            state.contextVariables.put(key, contextTag.getInt(key));
                            break;
                        case 5: // FLOAT
                            state.contextVariables.put(key, contextTag.getFloat(key));
                            break;
                    }
                }
            }

            return state;
        }

        // Getters and Setters
        public HouseholdTask getCurrentHouseholdTask() { return currentHouseholdTask; }
        public void setCurrentHouseholdTask(HouseholdTask currentHouseholdTask) { this.currentHouseholdTask = currentHouseholdTask; }
        public CombatState getCombatState() { return combatState; }
        public void setCombatState(CombatState combatState) { this.combatState = combatState; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
        public Map<String, Object> getContextVariables() { return contextVariables; }
        public void setContextVariable(String key, Object value) { this.contextVariables.put(key, value); }
        public Object getContextVariable(String key) { return this.contextVariables.get(key); }
    }

    /**
     * 获取系统实例（单例模式）
     */
    public static HousekeepingCombatAssistantSystem getInstance() {
        return instance;
    }

    /**
     * 在世界加载时初始化系统实例
     */
    public static HousekeepingCombatAssistantSystem getOrCreateInstance(ServerLevel level) {
        if (instance == null) {
            instance = level.getDataStorage().computeIfAbsent(
                tag -> new HousekeepingCombatAssistantSystem(),
                HousekeepingCombatAssistantSystem::new,
                "housekeeping_combat_assistant_system"
            );
        }
        return instance;
    }

    /**
     * 构造函数
     */
    public HousekeepingCombatAssistantSystem() {
        super();
    }

    /**
     * 获取指定NPC的助手状态，若不存在则创建新实例
     */
    public AssistantState getOrCreateAssistantState(UUID npcId) {
        return assistantStateMap.computeIfAbsent(npcId, id -> new AssistantState());
    }

    /**
     * 序列化为NBT格式
     * @return NBT复合标签
     */
    @Override
    public CompoundTag save(CompoundTag tag) {
        return serializeNBT();
    }

    /**
     * 从NBT反序列化
     * @param tag NBT复合标签
     */
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

        ListTag statesTag = new ListTag();
        for (Map.Entry<UUID, AssistantState> entry : assistantStateMap.entrySet()) {
            CompoundTag stateTag = new CompoundTag();
            stateTag.putUUID("npc_id", entry.getKey());
            stateTag.put("state", entry.getValue().serializeNBT());
            statesTag.add(stateTag);
        }
        tag.put("assistant_states", statesTag);

        return tag;
    }

    /**
     * 从NBT反序列化
     * @param tag NBT复合标签
     */
    public void deserializeNBT(CompoundTag tag) {
        assistantStateMap.clear();

        if (tag.contains("assistant_states", 9)) {
            ListTag statesTag = tag.getList("assistant_states", 10);
            for (int i = 0; i < statesTag.size(); i++) {
                CompoundTag stateTag = statesTag.getCompound(i);
                UUID npcId = stateTag.getUUID("npc_id");
                AssistantState state = AssistantState.deserializeNBT(stateTag.getCompound("state"));
                assistantStateMap.put(npcId, state);
            }
        }
    }
}