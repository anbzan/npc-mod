package com.example.npcmod.memory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * ScriptEventEditorSystem - 脚本与事件编辑器系统核心实现
 * 提供完整的脚本与事件管理功能，与现有NPC系统深度集成
 * 支持脚本创建、事件定义、条件触发、持久化存储和统一API接口
 * 与NPCMemorySystem、ContextAwareDialogueSystem、AffectionBondsSystem、DynamicBehaviorTaskSystem、HousekeepingCombatAssistantSystem无缝集成
 */
public class ScriptEventEditorSystem extends SavedData {

    // 系统日志记录器
    private static final Logger LOGGER = Logger.getLogger("NPC.ScriptEvent");

    // 系统实例单例
    private static ScriptEventEditorSystem instance;

    // 脚本与事件数据映射表
    private final Map<String, ScriptData> scriptMap = new ConcurrentHashMap<>();
    private final Map<String, EventData> eventMap = new ConcurrentHashMap<>();

    // 事件监听器列表
    private final List<EventListener> eventListeners = new CopyOnWriteArrayList<>();

    // 加密配置（与NPCMemorySystem保持一致）
    private static final String ENCRYPTION_KEY = "9Fix4L4HB4PKeKWY";
    private static final String IV_PARAMETER = "pf69DL6GrWFyZcMK";

    // 数据压缩与加密开关
    private boolean compressionEnabled = true;
    private boolean encryptionEnabled = true;

    // 系统间集成引用
    private NPCMemorySystem memorySystem;
    private ContextAwareDialogueSystem dialogueSystem;
    private AffectionBondsSystem affectionSystem;
    private DynamicBehaviorTaskSystem behaviorSystem;
    private HousekeepingCombatAssistantSystem housekeepingSystem;

    /**
     * 获取系统实例（单例模式）
     */
    public static ScriptEventEditorSystem getInstance() {
        return instance;
    }

    /**
     * 在世界加载时初始化系统实例
     */
    public static ScriptEventEditorSystem getOrCreateInstance(ServerLevel level) {
        if (instance == null) {
            instance = level.getDataStorage().computeIfAbsent(
                tag -> new ScriptEventEditorSystem().load(tag),
                ScriptEventEditorSystem::new,
                "script_event_editor_system"
            );
            instance.markInitialized();
        }
        return instance;
    }

    /**
     * 构造函数
     */
    public ScriptEventEditorSystem() {
        super();
    }

    /**
     * 标记系统已初始化，建立与其他系统的集成
     */
    private void markInitialized() {
        try {
            this.memorySystem = NPCMemorySystem.getOrCreateInstance((ServerLevel) Level.OVERWORLD);
            this.dialogueSystem = ContextAwareDialogueSystem.getOrCreateInstance((ServerLevel) Level.OVERWORLD);
            this.affectionSystem = AffectionBondsSystem.getOrCreateInstance((ServerLevel) Level.OVERWORLD);
            this.behaviorSystem = DynamicBehaviorEngine.getInstance();
            this.housekeepingSystem = HousekeepingCombatAssistantSystem.getOrCreateInstance((ServerLevel) Level.OVERWORLD);
            LOGGER.info("ScriptEventEditorSystem initialized and integrated with all subsystems.");
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize ScriptEventEditorSystem integrations: " + e.getMessage());
        }
    }

    // ==================================================================================
    // 1. 脚本管理模块
    // ==================================================================================

    /**
     * 脚本数据结构定义
     */
    public static class ScriptData {
        private String scriptId;
        private String scriptName;
        private String description;
        private long createTime;
        private long modifyTime;
        private String content;
        private int version;
        private Map<String, Object> metadata;

        public ScriptData(String scriptId, String scriptName, String description) {
            this.scriptId = scriptId;
            this.scriptName = scriptName;
            this.description = description;
            this.createTime = System.currentTimeMillis();
            this.modifyTime = this.createTime;
            this.content = "";
            this.version = 1;
            this.metadata = new ConcurrentHashMap<>();
        }

        // Getters and Setters
        public String getScriptId() { return scriptId; }
        public void setScriptId(String scriptId) { this.scriptId = scriptId; }

        public String getScriptName() { return scriptName; }
        public void setScriptName(String scriptName) { this.scriptName = scriptName; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public long getCreateTime() { return createTime; }
        public void setCreateTime(long createTime) { this.createTime = createTime; }

        public long getModifyTime() { return modifyTime; }
        public void setModifyTime(long modifyTime) { this.modifyTime = modifyTime; }

        public String getContent() { return content; }
        public void setContent(String content) { 
            this.content = content; 
            this.modifyTime = System.currentTimeMillis();
            this.version++;
        }

        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

        public Object getMetadataValue(String key) { return metadata.get(key); }
        public void setMetadataValue(String key, Object value) { metadata.put(key, value); }

        /**
         * 序列化为NBT格式
         * @return NBT复合标签
         */
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("script_id", scriptId);
            tag.putString("script_name", scriptName);
            tag.putString("description", description);
            tag.putLong("create_time", createTime);
            tag.putLong("modify_time", modifyTime);
            tag.putString("content", content);
            tag.putInt("version", version);

            // 序列化元数据
            CompoundTag metadataTag = new CompoundTag();
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (entry.getValue() instanceof String) {
                    metadataTag.putString(entry.getKey(), (String) entry.getValue());
                } else if (entry.getValue() instanceof Integer) {
                    metadataTag.putInt(entry.getKey(), (Integer) entry.getValue());
                } else if (entry.getValue() instanceof Double) {
                    metadataTag.putDouble(entry.getKey(), (Double) entry.getValue());
                } else if (entry.getValue() instanceof Boolean) {
                    metadataTag.putBoolean(entry.getKey(), (Boolean) entry.getValue());
                } else if (entry.getValue() instanceof Long) {
                    metadataTag.putLong(entry.getKey(), (Long) entry.getValue());
                }
            }
            tag.put("metadata", metadataTag);

            return tag;
        }

        /**
         * 从NBT反序列化
         * @param tag NBT复合标签
         */
        public static ScriptData deserializeNBT(CompoundTag tag) {
            String scriptId = tag.getString("script_id");
            String scriptName = tag.getString("script_name");
            String description = tag.getString("description");
            ScriptData script = new ScriptData(scriptId, scriptName, description);
            script.createTime = tag.getLong("create_time");
            script.modifyTime = tag.getLong("modify_time");
            script.content = tag.getString("content");
            script.version = tag.getInt("version");

            // 反序列化元数据
            if (tag.contains("metadata", 10)) {
                CompoundTag metadataTag = tag.getCompound("metadata");
                for (String key : metadataTag.getAllKeys()) {
                    switch (metadataTag.getId(key)) {
                        case 8: // STRING
                            script.metadata.put(key, metadataTag.getString(key));
                            break;
                        case 3: // INT
                            script.metadata.put(key, metadataTag.getInt(key));
                            break;
                        case 6: // DOUBLE
                            script.metadata.put(key, metadataTag.getDouble(key));
                            break;
                        case 1: // BYTE
                            script.metadata.put(key, metadataTag.getBoolean(key));
                            break;
                        case 4: // LONG
                            script.metadata.put(key, metadataTag.getLong(key));
                            break;
                    }
                }
            }

            return script;
        }
    }

    /**
     * 创建新脚本
     * @param scriptId 脚本ID
     * @param scriptName 脚本名称
     * @param description 描述
     * @return 脚本数据对象
     */
    public ScriptData createScript(String scriptId, String scriptName, String description) {
        try {
            if (scriptMap.containsKey(scriptId)) {
                throw new IllegalArgumentException("Script ID already exists: " + scriptId);
            }

            ScriptData script = new ScriptData(scriptId, scriptName, description);
            scriptMap.put(scriptId, script);
            setDirty();
            LOGGER.info("Created new script: " + scriptId);

            // 触发事件
            fireEvent(new ScriptEvent(ScriptEventType.SCRIPT_CREATED, scriptId, script));

            return script;
        } catch (Exception e) {
            LOGGER.severe("Failed to create script " + scriptId + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * 编辑脚本内容
     * @param scriptId 脚本ID
     * @param content 新内容
     * @return 更新后的脚本数据
     */
    public ScriptData editScript(String scriptId, String content) {
        try {
            ScriptData script = scriptMap.get(scriptId);
            if (script == null) {
                throw new IllegalArgumentException("Script not found: " + scriptId);
            }

            script.setContent(content);
            setDirty();
            LOGGER.info("Edited script: " + scriptId);

            // 触发事件
            fireEvent(new ScriptEvent(ScriptEventType.SCRIPT_EDITED, scriptId, script));

            return script;
        } catch (Exception e) {
            LOGGER.severe("Failed to edit script " + scriptId + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * 保存脚本
     * @param scriptId 脚本ID
     */
    public void saveScript(String scriptId) {
        try {
            ScriptData script = scriptMap.get(scriptId);
            if (script == null) {
                throw new IllegalArgumentException("Script not found: " + scriptId);
            }

            script.setModifyTime(System.currentTimeMillis());
            setDirty();
            LOGGER.info("Saved script: " + scriptId);

            // 触发事件
            fireEvent(new ScriptEvent(ScriptEventType.SCRIPT_SAVED, scriptId, script));
        } catch (Exception e) {
            LOGGER.severe("Failed to save script " + scriptId + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * 加载脚本
     * @param scriptId 脚本ID
     * @return 脚本数据对象
     */
    public ScriptData loadScript(String scriptId) {
        try {
            ScriptData script = scriptMap.get(scriptId);
            if (script == null) {
                throw new IllegalArgumentException("Script not found: " + scriptId);
            }

            LOGGER.info("Loaded script: " + scriptId);

            // 触发事件
            fireEvent(new ScriptEvent(ScriptEventType.SCRIPT_LOADED, scriptId, script));

            return script;
        } catch (Exception e) {
            LOGGER.severe("Failed to load script " + scriptId + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * 导出脚本到文件
     * @param scriptId 脚本ID
     * @param filePath 导出文件路径
     */
    public void exportScript(String scriptId, String filePath) {
        try {
            ScriptData script = scriptMap.get(scriptId);
            if (script == null) {
                throw new IllegalArgumentException("Script not found: " + scriptId);
            }

            CompoundTag tag = script.serializeNBT();
            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent());

            // 压缩和加密
            byte[] data = tag.getAsString().getBytes();
            if (compressionEnabled) {
                data = compressData(data);
            }
            if (encryptionEnabled) {
                data = encryptData(data);
            }

            Files.write(path, data);
            LOGGER.info("Exported script " + scriptId + " to " + filePath);
        } catch (Exception e) {
            LOGGER.severe("Failed to export script " + scriptId + " to " + filePath + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 导入脚本从文件
     * @param filePath 导入文件路径
     * @return 导入的脚本ID
     */
    public String importScript(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("File not found: " + filePath);
            }

            byte[] data = Files.readAllBytes(path);

            // 解密和解压
            if (encryptionEnabled) {
                data = decryptData(data);
            }
            if (compressionEnabled) {
                data = decompressData(data);
            }

            String json = new String(data);
            CompoundTag tag = CompoundTag.valueOf(json);
            ScriptData script = ScriptData.deserializeNBT(tag);

            scriptMap.put(script.getScriptId(), script);
            setDirty();

            LOGGER.info("Imported script " + script.getScriptId() + " from " + filePath);

            // 触发事件
            fireEvent(new ScriptEvent(ScriptEventType.SCRIPT_IMPORTED, script.getScriptId(), script));

            return script.getScriptId();
        } catch (Exception e) {
            LOGGER.severe("Failed to import script from " + filePath + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ==================================================================================
    // 2. 事件定义模块
    // ==================================================================================

    /**
     * 事件数据结构定义
     */
    public static class EventData {
        private String eventId;
        private String eventName;
        private EventType eventType;
        private TriggerCondition triggerCondition;
        private ExecutionLogic executionLogic;
        private int priority;
        private boolean active;
        private long lastTriggerTime;

        public EventData(String eventId, String eventName, EventType eventType) {
            this.eventId = eventId;
            this.eventName = eventName;
            this.eventType = eventType;
            this.priority = 0;
            this.active = true;
            this.lastTriggerTime = 0;
            this.triggerCondition = new TriggerCondition();
            this.executionLogic = new ExecutionLogic();
        }

        // Getters and Setters
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getEventName() { return eventName; }
        public void setEventName(String eventName) { this.eventName = eventName; }

        public EventType getEventType() { return eventType; }
        public void setEventType(EventType eventType) { this.eventType = eventType; }

        public TriggerCondition getTriggerCondition() { return triggerCondition; }
        public void setTriggerCondition(TriggerCondition triggerCondition) { this.triggerCondition = triggerCondition; }

        public ExecutionLogic getExecutionLogic() { return executionLogic; }
        public void setExecutionLogic(ExecutionLogic executionLogic) { this.executionLogic = executionLogic; }

        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }

        public long getLastTriggerTime() { return lastTriggerTime; }
        public void setLastTriggerTime(long lastTriggerTime) { this.lastTriggerTime = lastTriggerTime; }

        /**
         * 序列化为NBT格式
         * @return NBT复合标签
         */
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("event_id", eventId);
            tag.putString("event_name", eventName);
            tag.putString("event_type", eventType.name());
            tag.putInt("priority", priority);
            tag.putBoolean("active", active);
            tag.putLong("last_trigger_time", lastTriggerTime);
            tag.put("trigger_condition", triggerCondition.serializeNBT());
            tag.put("execution_logic", executionLogic.serializeNBT());
            return tag;
        }

        /**
         * 从NBT反序列化
         * @param tag NBT复合标签
         */
        public static EventData deserializeNBT(CompoundTag tag) {
            String eventId = tag.getString("event_id");
            String eventName = tag.getString("event_name");
            EventType eventType = EventType.valueOf(tag.getString("event_type"));
            EventData event = new EventData(eventId, eventName, eventType);
            event.priority = tag.getInt("priority");
            event.active = tag.getBoolean("active");
            event.lastTriggerTime = tag.getLong("last_trigger_time");

            if (tag.contains("trigger_condition", 10)) {
                event.triggerCondition = TriggerCondition.deserializeNBT(tag.getCompound("trigger_condition"));
            }
            if (tag.contains("execution_logic", 10)) {
                event.executionLogic = ExecutionLogic.deserializeNBT(tag.getCompound("execution_logic"));
            }

            return event;
        }
    }

    /**
     * 事件类型枚举
     */
    public enum EventType {
        DIALOGUE_TRIGGER,    // 对话触发
        TASK_COMPLETED,     // 任务完成
        AFFECTION_CHANGE,   // 好感度变化
        TIME_EVENT,         // 时间事件
        PLAYER_ACTION,      // 玩家行为
        SYSTEM_EVENT        // 系统事件
    }

    /**
     * 触发条件数据结构
     */
    public static class TriggerCondition {
        private PlayerBehaviorCondition playerBehavior;
        private TimeCondition timeCondition;
        private AffectionCondition affectionCondition;
        private TaskCondition taskCondition;
        private LogicOperator logicOperator;
        private double weight;

        public TriggerCondition() {
            this.playerBehavior = new PlayerBehaviorCondition();
            this.timeCondition = new TimeCondition();
            this.affectionCondition = new AffectionCondition();
            this.taskCondition = new TaskCondition();
            this.logicOperator = LogicOperator.AND;
            this.weight = 1.0;
        }

        // Getters and Setters
        public PlayerBehaviorCondition getPlayerBehavior() { return playerBehavior; }
        public void setPlayerBehavior(PlayerBehaviorCondition playerBehavior) { this.playerBehavior = playerBehavior; }

        public TimeCondition getTimeCondition() { return timeCondition; }
        public void setTimeCondition(TimeCondition timeCondition) { this.timeCondition = timeCondition; }

        public AffectionCondition getAffectionCondition() { return affectionCondition; }
        public void setAffectionCondition(AffectionCondition affectionCondition) { this.affectionCondition = affectionCondition; }

        public TaskCondition getTaskCondition() { return taskCondition; }
        public void setTaskCondition(TaskCondition taskCondition) { this.taskCondition = taskCondition; }

        public LogicOperator getLogicOperator() { return logicOperator; }
        public void setLogicOperator(LogicOperator logicOperator) { this.logicOperator = logicOperator; }

        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }

        /**
         * 序列化为NBT格式
         * @return NBT复合标签
         */
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.put("player_behavior", playerBehavior.serializeNBT());
            tag.put("time_condition", timeCondition.serializeNBT());
            tag.put("affection_condition", affectionCondition.serializeNBT());
            tag.put("task_condition", taskCondition.serializeNBT());
            tag.putString("logic_operator", logicOperator.name());
            tag.putDouble("weight", weight);
            return tag;
        }

        /**
         * 从NBT反序列化
         * @param tag NBT复合标签
         */
        public static TriggerCondition deserializeNBT(CompoundTag tag) {
            TriggerCondition condition = new TriggerCondition();

            if (tag.contains("player_behavior", 10)) {
                condition.playerBehavior = PlayerBehaviorCondition.deserializeNBT(tag.getCompound("player_behavior"));
            }
            if (tag.contains("time_condition", 10)) {
                condition.timeCondition = TimeCondition.deserializeNBT(tag.getCompound("time_condition"));
            }
            if (tag.contains("affection_condition", 10)) {
                condition.affectionCondition = AffectionCondition.deserializeNBT(tag.getCompound("affection_condition"));
            }
            if (tag.contains("task_condition", 10)) {
                condition.taskCondition = TaskCondition.deserializeNBT(tag.getCompound("task_condition"));
            }
            condition.logicOperator = LogicOperator.valueOf(tag.getString("logic_operator"));
            condition.weight = tag.getDouble("weight");

            return condition;
        }
    }

    /**
     * 逻辑操作符枚举
     */
    public enum LogicOperator {
        AND, OR, NOT
    }

    /**
     * 执行逻辑数据结构
     */
    public static class ExecutionLogic {
        private List<String> scriptIds;
        private List<EventAction> actions;
        private Map<String, Object> context;

        public ExecutionLogic() {
            this.scriptIds = new ArrayList<>();
            this.actions = new ArrayList<>();
            this.context = new ConcurrentHashMap<>();
        }

        // Getters and Setters
        public List<String> getScriptIds() { return scriptIds; }
        public void setScriptIds(List<String> scriptIds) { this.scriptIds = scriptIds; }

        public List<EventAction> getActions() { return actions; }
        public void setActions(List<EventAction> actions) { this.actions = actions; }

        public Map<String, Object> getContext() { return context; }
        public void setContext(Map<String, Object> context) { this.context = context; }

        public void addAction(EventAction action) { this.actions.add(action); }

        /**
         * 序列化为NBT格式
         * @return NBT复合标签
         */
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();

            // 序列化脚本ID列表
            ListTag scriptIdsTag = new ListTag();
            for (String scriptId : scriptIds) {
                scriptIdsTag.add(StringTag.valueOf(scriptId));
            }
            tag.put("script_ids", scriptIdsTag);

            // 序列化动作列表
            ListTag actionsTag = new ListTag();
            for (EventAction action : actions) {
                actionsTag.add(action.serializeNBT());
            }
            tag.put("actions", actionsTag);

            // 序列化上下文
            CompoundTag contextTag = new CompoundTag();
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                if (entry.getValue() instanceof String) {
                    contextTag.putString(entry.getKey(), (String) entry.getValue());
                } else if (entry.getValue() instanceof Integer) {
                    contextTag.putInt(entry.getKey(), (Integer) entry.getValue());
                } else if (entry.getValue() instanceof Double) {
                    contextTag.putDouble(entry.getKey(), (Double) entry.getValue());
                } else if (entry.getValue() instanceof Boolean) {
                    contextTag.putBoolean(entry.getKey(), (Boolean) entry.getValue());
                } else if (entry.getValue() instanceof Long) {
                    contextTag.putLong(entry.getKey(), (Long) entry.getValue());
                }
            }
            tag.put("context", contextTag);

            return tag;
        }

        /**
         * 从NBT反序列化
         * @param tag NBT复合标签
         */
        public static ExecutionLogic deserializeNBT(CompoundTag tag) {
            ExecutionLogic logic = new ExecutionLogic();

            // 反序列化脚本ID列表
            if (tag.contains("script_ids", 9)) {
                ListTag scriptIdsTag = tag.getList("script_ids", 8);
                for (Tag scriptIdTag : scriptIdsTag) {
                    logic.scriptIds.add(scriptIdTag.getAsString());
                }
            }

            // 反序列化动作列表
            if (tag.contains("actions", 9)) {
                ListTag actionsTag = tag.getList("actions", 10);
                for (Tag actionTag : actionsTag) {
                    if (actionTag instanceof CompoundTag) {
                        logic.actions.add(EventAction.deserializeNBT((CompoundTag) actionTag));
                    }
                }
            }

            // 反序列化上下文
            if (tag.contains("context", 10)) {
                CompoundTag contextTag = tag.getCompound("context");
                for (String key : contextTag.getAllKeys()) {
                    switch (contextTag.getId(key)) {
                        case 8: // STRING
                            logic.context.put(key, contextTag.getString(key));
                            break;
                        case 3: // INT
                            logic.context.put(key, contextTag.getInt(key));
                            break;
                        case 6: // DOUBLE
                            logic.context.put(key, contextTag.getDouble(key));
                            break;
                        case 1: // BYTE
                            logic.context.put(key, contextTag.getBoolean(key));
                            break;
                        case 4: // LONG
                            logic.context.put(key, contextTag.getLong(key));
                            break;
                    }
                }
            }

            return logic;
        }
    }

    /**
     * 事件动作枚举
     */
    public enum EventActionType {
        RUN_SCRIPT,           // 执行脚本
        TRIGGER_DIALOGUE,     // 触发对话
        MODIFY_AFFECTION,     // 修改好感度
        GENERATE_TASK,        // 生成任务
        PERFORM_HOUSEHOLD,    // 执行家政
        PERFORM_COMBAT,       // 执行战斗
        UPDATE_MEMORY,        // 更新记忆
        SEND_NOTIFICATION     // 发送通知
    }

    /**
     * 事件动作数据结构
     */
    public static class EventAction {
        private EventActionType type;
        private String targetId;
        private String content;
        private int value;
        private Map<String, Object> parameters;

        public EventAction(EventActionType type) {
            this.type = type;
            this.parameters = new ConcurrentHashMap<>();
        }

        // Getters and Setters
        public EventActionType getType() { return type; }
        public void setType(EventActionType type) { this.type = type; }

        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }

        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

        public Object getParameter(String key) { return parameters.get(key); }
        public void setParameter(String key, Object value) { parameters.put(key, value); }

        /**
         * 序列化为NBT格式
         * @return NBT复合标签
         */
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", type.name());
            if (targetId != null) {
                tag.putString("target_id", targetId);
            }
            if (content != null) {
                tag.putString("content", content);
            }
            tag.putInt("value", value);

            // 序列化参数
            CompoundTag parametersTag = new CompoundTag();
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                if (entry.getValue() instanceof String) {
                    parametersTag.putString(entry.getKey(), (String) entry.getValue());
                } else if (entry.getValue() instanceof Integer) {
                    parametersTag.putInt(entry.getKey(), (Integer) entry.getValue());
                } else if (entry.getValue() instanceof Double) {
                    parametersTag.putDouble(entry.getKey(), (Double) entry.getValue());
                } else if (entry.getValue() instanceof Boolean) {
                    parametersTag.putBoolean(entry.getKey(), (Boolean) entry.getValue());
                } else if (entry.getValue() instanceof Long) {
                    parametersTag.putLong(entry.getKey(), (Long) entry.getValue());
                }
            }
            tag.put("parameters", parametersTag);

            return tag;
        }

        /**
         * 从NBT反序列化
         * @param tag NBT复合标签
         */
        public static EventAction deserializeNBT(CompoundTag tag) {
            EventActionType type = EventActionType.valueOf(tag.getString("type"));
            EventAction action = new EventAction(type);
            action.targetId = tag.contains("target_id") ? tag.getString("target_id") : null;
            action.content = tag.contains("content") ? tag.getString("content") : null;
            action.value = tag.getInt("value");

            // 反序列化参数
            if (tag.contains("parameters", 10)) {
                CompoundTag parametersTag = tag.getCompound("parameters");
                for (String key : parametersTag.getAllKeys()) {
                    switch (parametersTag.getId(key)) {
                        case 8: // STRING
                            action.parameters.put(key, parametersTag.getString(key));
                            break;
                        case 3: // INT
                            action.parameters.put(key, parametersTag.getInt(key));
                            break;
                        case 6: // DOUBLE
                            action.parameters.put(key, parametersTag.getDouble(key));
                            break;
                        case 1: // BYTE
                            action.parameters.put(key, parametersTag.getBoolean(key));
                            break;
                        case 4: // LONG
                            action.parameters.put(key, parametersTag.getLong(key));
                            break;
                    }
                }
            }

            return action;
        }
    }

    /**
     * 定义新事件
     * @param eventId 事件ID
     * @param eventName 事件名称
     * @param eventType 事件类型
     * @return 事件数据对象
     */
    public EventData defineEvent(String eventId, String eventName, EventType eventType) {
        try {
            if (eventMap.containsKey(eventId)) {
                throw new IllegalArgumentException("Event ID already exists: " + eventId);
            }

            EventData event = new EventData(eventId, eventName, eventType);
            eventMap.put(eventId, event);
            setDirty();
            LOGGER.info("Defined new event: " + eventId);

            // 触发事件
            fireEvent(new ScriptEvent(ScriptEventType.EVENT_DEFINED, eventId, event));

            return event;
        } catch (Exception e) {
            LOGGER.severe("Failed to define event " + eventId + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * 设置事件触发条件
     * @param eventId 事件ID
     * @param condition 触发条件
     */
    public void setTriggerCondition(String eventId, TriggerCondition condition) {
        try {
            EventData event = eventMap.get(eventId);
            if (event == null) {
                throw new IllegalArgumentException("Event not found: " + eventId);
            }

            event.setTriggerCondition(condition);
            setDirty();
            LOGGER.info("Set trigger condition for event: " + eventId);
        } catch (Exception e) {
            LOGGER.severe("Failed to set trigger condition for event " + eventId + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * 设置事件执行逻辑
     * @param eventId 事件ID
     * @param logic 执行逻辑
     */
    public void setExecutionLogic(String eventId, ExecutionLogic logic) {
        try {
            EventData event = eventMap.get(eventId);
            if (event == null) {
                throw new IllegalArgumentException("Event not found: " + eventId);
            }

            event.setExecutionLogic(logic);
            setDirty();
            LOGGER.info("Set execution logic for event: " + eventId);
        } catch (Exception e) {
            LOGGER.severe("Failed to set execution logic for event " + eventId + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * 激活事件
     * @param eventId 事件ID
     */
    public void activateEvent(String eventId) {
        try {
            EventData event = eventMap.get(eventId);
            if (event == null) {
                throw new IllegalArgumentException("Event not found: " + eventId);
            }

            event.setActive(true);
            setDirty();
            LOGGER.info("Activated event: " + eventId);

            // 触发事件
            fireEvent(new ScriptEvent(ScriptEventType.EVENT_ACTIVATED, eventId, event));
        } catch (Exception e) {
            LOGGER.severe("Failed to activate event " + eventId + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * 停用事件
     * @param eventId 事件ID
     */
    public void deactivateEvent(String eventId) {
        try {
            EventData event = eventMap.get(eventId);
            if (event == null) {
                throw new IllegalArgumentException("Event not found: " + eventId);
            }

            event.setActive(false);
            setDirty();
            LOGGER.info("Deactivated event: " + eventId);

            // 触发事件
            fireEvent(new ScriptEvent(ScriptEventType.EVENT_DEACTIVATED, eventId, event));
        } catch (Exception e) {
            LOGGER.severe("Failed to deactivate event " + eventId + ": " + e.getMessage());
            throw e;
        }
    }

    // ==================================================================================
    // 3. 多维度条件触发引擎
    // ==================================================================================

    /**
     * 玩家行为条件
     */
    public static class PlayerBehaviorCondition {
        private List<String> interactionTypes;
        private int minInteractionCount;
        private List<String> requiredItems;
        private boolean mustHaveItem;
        private String dialogueKeyword;

        public PlayerBehaviorCondition() {
            this.interactionTypes = new ArrayList<>();
            this.minInteractionCount = 0;
            this.requiredItems = new ArrayList<>();
            this.mustHaveItem = false;
            this.dialogueKeyword = "";
        }

        // Getters and Setters
        public List<String> getInteractionTypes() { return interactionTypes; }
        public void setInteractionTypes(List<String> interactionTypes) { this.interactionTypes = interactionTypes; }

        public int getMinInteractionCount() { return minInteractionCount; }
        public void setMinInteractionCount(int minInteractionCount) { this.minInteractionCount = minInteractionCount; }

        public List<String> getRequiredItems() { return requiredItems; }
        public void setRequiredItems(List<String> requiredItems) { this.requiredItems = requiredItems; }

        public boolean isMustHaveItem() { return mustHaveItem; }
        public void setMustHaveItem(boolean mustHaveItem) { this.mustHaveItem = mustHaveItem; }

        public String getDialogueKeyword() { return dialogueKeyword; }
        public void setDialogueKeyword(String dialogueKeyword) { this.dialogueKeyword = dialogueKeyword; }

        /**
         * 序列化为NBT格式
         * @return NBT复合标签
         */
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();

            // 序列化交互类型
            ListTag interactionTypesTag = new ListTag();
            for (String type : interactionTypes) {
                interactionTypesTag.add(StringTag.valueOf(type));
            }
            tag.put("interaction_types", interactionTypesTag);

            tag.putInt("min_interaction_count", minInteractionCount);

            // 序列化必需物品
            ListTag requiredItemsTag = new ListTag();
            for (String item : requiredItems) {
                requiredItemsTag.add(StringTag.valueOf(item));
            }
            tag.put("required_items", requiredItemsTag);

            tag.putBoolean("must_have_item", mustHaveItem);
            tag.putString("dialogue_keyword", dialogueKeyword);

            return tag;
        }

        /**
         * 从NBT反序列化
         * @param tag NBT复合标签
         */
        public static PlayerBehaviorCondition deserializeNBT(CompoundTag tag) {
            PlayerBehaviorCondition condition = new PlayerBehaviorCondition();

            // 反序列化交互类型
            if (tag.contains("interaction_types", 9)) {
                ListTag interactionTypesTag = tag.getList("interaction_types", 8);
                for (Tag interactionTypeTag : interactionTypesTag) {
                    condition.interactionTypes.add(interactionTypeTag.getAsString());
                }
            }

            condition.minInteractionCount = tag.getInt("min_interaction_count");

            // 反序列化必需物品
            if (tag.contains("required_items", 9)) {
                ListTag requiredItemsTag = tag.getList("required_items", 8);
                for (Tag requiredItemTag : requiredItemsTag) {
                    condition.requiredItems.add(requiredItemTag.getAsString());
                }
            }

            condition.mustHaveItem = tag.getBoolean("must_have_item");
            condition.dialogueKeyword = tag.getString("dialogue_keyword");

            return condition;
        }
    }

    /**
     * 时间条件
     */
    public static class TimeCondition {
        private int gameHourStart;
        private int gameHourEnd;
        private int gameDayOfWeek;
        private int gameDayOfMonth;
        private boolean periodicTrigger;
        private int triggerIntervalMinutes;

        public TimeCondition() {
            this.gameHourStart = 0;
            this.gameHourEnd = 23;
            this.gameDayOfWeek = -1; // -1表示不限制
            this.gameDayOfMonth = -1; // -1表示不限制
            this.periodicTrigger = false;
            this.triggerIntervalMinutes = 60;
        }

        // Getters and Setters
        public int getGameHourStart() { return gameHourStart; }
        public void setGameHourStart(int gameHourStart) { this.gameHourStart = gameHourStart; }

        public int getGameHourEnd() { return gameHourEnd; }
        public void setGameHourEnd(int gameHourEnd) { this.gameHourEnd = gameHourEnd; }

        public int getGameDayOfWeek() { return gameDayOfWeek; }
        public void setGameDayOfWeek(int gameDayOfWeek) { this.gameDayOfWeek = gameDayOfWeek; }

        public int getGameDayOfMonth() { return gameDayOfMonth; }
        public void setGameDayOfMonth(int gameDayOfMonth) { this.gameDayOfMonth = gameDayOfMonth; }

        public boolean isPeriodicTrigger() { return periodicTrigger; }
        public void setPeriodicTrigger(boolean periodicTrigger) { this.periodicTrigger = periodicTrigger; }

        public int getTriggerIntervalMinutes() { return triggerIntervalMinutes; }
        public void setTriggerIntervalMinutes(int triggerIntervalMinutes) { this.triggerIntervalMinutes = triggerIntervalMinutes; }

        /**
         * 序列化为NBT格式
         * @return NBT复合标签
         */
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("game_hour_start", gameHourStart);
            tag.putInt("game_hour_end", gameHourEnd);
            tag.putInt("game_day_of_week", gameDayOfWeek);
            tag.putInt("game_day_of_month", gameDayOfMonth);
            tag.putBoolean("periodic_trigger", periodicTrigger);
            tag.putInt("trigger_interval_minutes", triggerIntervalMinutes);
            return tag;
        }

        /**
         * 从NBT反序列化
         * @param tag NBT复合标签
         */
        public static TimeCondition deserializeNBT(CompoundTag tag) {
            TimeCondition condition = new TimeCondition();
            condition.gameHourStart = tag.getInt("game_hour_start");
            condition.gameHourEnd = tag.getInt("game_hour_end");
            condition.gameDayOfWeek = tag.getInt("game_day_of_week");
            condition.gameDayOfMonth = tag.getInt("game_day_of_month");
            condition.periodicTrigger = tag.getBoolean("periodic_trigger");
            condition.triggerIntervalMinutes = tag.getInt("trigger_interval_minutes");
            return condition;
        }
    }

    /**
     * 好感度条件
     */
    public static class AffectionCondition {
        private int minAffectionScore;
        private int maxAffectionScore;
        private AffectionBondsSystem.AffinityLevel minAffectionLevel;
        private AffectionBondsSystem.AffinityLevel maxAffectionLevel;
        private int affectionChangeThreshold;
        private boolean mustIncrease;
        private boolean mustDecrease;

        public AffectionCondition() {
            this.minAffectionScore = 0;
            this.maxAffectionScore = 100;
            this.minAffectionLevel = AffectionBondsSystem.AffinityLevel.STRANGER;
            this.maxAffectionLevel = AffectionBondsSystem.AffinityLevel.BEST_FRIEND;
            this.affectionChangeThreshold = 0;
            this.mustIncrease = false;
            this.mustDecrease = false;
        }

        // Getters and Setters
        public int getMinAffectionScore() { return minAffectionScore; }
        public void setMinAffectionScore(int minAffectionScore) { this.minAffectionScore = minAffectionScore; }

        public int getMaxAffectionScore() { return maxAffectionScore; }
        public void setMaxAffectionScore(int maxAffectionScore) { this.maxAffectionScore = maxAffectionScore; }

        public AffectionBondsSystem.AffinityLevel getMinAffectionLevel() { return minAffectionLevel; }
        public void setMinAffectionLevel(AffectionBondsSystem.AffinityLevel minAffectionLevel) { this.minAffectionLevel = minAffectionLevel; }

        public AffectionBondsSystem.AffinityLevel getMaxAffectionLevel() { return maxAffectionLevel; }
        public void setMaxAffectionLevel(AffectionBondsSystem.AffinityLevel maxAffectionLevel) { this.maxAffectionLevel = maxAffectionLevel; }

        public int getAffectionChangeThreshold() { return affectionChangeThreshold; }
        public void setAffectionChangeThreshold(int affectionChangeThreshold) { this.affectionChangeThreshold = affectionChangeThreshold; }

        public boolean isMustIncrease() { return mustIncrease; }
        public void setMustIncrease(boolean mustIncrease) { this.mustIncrease = mustIncrease; }

        public boolean isMustDecrease() { return mustDecrease; }
        public void setMustDecrease(boolean mustDecrease) { this.mustDecrease = mustDecrease; }

        /**
         * 序列化为NBT格式
         * @return NBT复合标签
         */
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("min_affection_score", minAffectionScore);
            tag.putInt("max_affection_score", maxAffectionScore);
            tag.putString("min_affection_level", minAffectionLevel.name());
            tag.putString("max_affection_level", maxAffectionLevel.name());
            tag.putInt("affection_change_threshold", affectionChangeThreshold);
            tag.putBoolean("must_increase", mustIncrease);
            tag.putBoolean("must_decrease", mustDecrease);
            return tag;
        }

        /**
         * 从NBT反序列化
         * @param tag NBT复合标签
         */
        public static AffectionCondition deserializeNBT(CompoundTag tag) {
            AffectionCondition condition = new AffectionCondition();
            condition.minAffectionScore = tag.getInt("min_affection_score");
            condition.maxAffectionScore = tag.getInt("max_affection_score");
            condition.minAffectionLevel = AffectionBondsSystem.AffinityLevel.valueOf(tag.getString("min_affection_level"));
            condition.maxAffectionLevel = AffectionBondsSystem.AffinityLevel.valueOf(tag.getString("max_affection_level"));
            condition.affectionChangeThreshold = tag.getInt("affection_change_threshold");
            condition.mustIncrease = tag.getBoolean("must_increase");
            condition.mustDecrease = tag.getBoolean("must_decrease");
            return condition;
        }
    }

    /**
     * 任务条件
     */
    public static class TaskCondition {
        private List<String> requiredTaskIds;
        private NPCMemorySystem.TaskProgress.TaskStatus requiredStatus;
        private int minCompletionCount;
        private int maxCompletionCount;
        private boolean mustComplete;
        private boolean mustFail;

        public TaskCondition() {
            this.requiredTaskIds = new ArrayList<>();
            this.requiredStatus = NPCMemorySystem.TaskProgress.TaskStatus.COMPLETED;
            this.minCompletionCount = 0;
            this.maxCompletionCount = Integer.MAX_VALUE;
            this.mustComplete = false;
            this.mustFail = false;
        }

        // Getters and Setters
        public List<String> getRequiredTaskIds() { return requiredTaskIds; }
        public void setRequiredTaskIds(List<String> requiredTaskIds) { this.requiredTaskIds = requiredTaskIds; }

        public NPCMemorySystem.TaskProgress.TaskStatus getRequiredStatus() { return requiredStatus; }
        public void setRequiredStatus(NPCMemorySystem.TaskProgress.TaskStatus requiredStatus) { this.requiredStatus = requiredStatus; }

        public int getMinCompletionCount() { return minCompletionCount; }
        public void setMinCompletionCount(int minCompletionCount) { this.minCompletionCount = minCompletionCount; }

        public int getMaxCompletionCount() { return maxCompletionCount; }
        public void setMaxCompletionCount(int maxCompletionCount) { this.maxCompletionCount = maxCompletionCount; }

        public boolean isMustComplete() { return mustComplete; }
        public void setMustComplete(boolean mustComplete) { this.mustComplete = mustComplete; }

        public boolean isMustFail() { return mustFail; }
        public void setMustFail(boolean mustFail) { this.mustFail = mustFail; }

        /**
         * 序列化为NBT格式
         * @return NBT复合标签
         */
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();

            // 序列化任务ID列表
            ListTag requiredTaskIdsTag = new ListTag();
            for (String taskId : requiredTaskIds) {
                requiredTaskIdsTag.add(StringTag.valueOf(taskId));
            }
            tag.put("required_task_ids", requiredTaskIdsTag);

            tag.putString("required_status", requiredStatus.name());
            tag.putInt("min_completion_count", minCompletionCount);
            tag.putInt("max_completion_count", maxCompletionCount);
            tag.putBoolean("must_complete", mustComplete);
            tag.putBoolean("must_fail", mustFail);

            return tag;
        }

        /**
         * 从NBT反序列化
         * @param tag NBT复合标签
         */
        public static TaskCondition deserializeNBT(CompoundTag tag) {
            TaskCondition condition = new TaskCondition();

            // 反序列化任务ID列表
            if (tag.contains("required_task_ids", 9)) {
                ListTag requiredTaskIdsTag = tag.getList("required_task_ids", 8);
                for (Tag requiredTaskIdTag : requiredTaskIdsTag) {
                    condition.requiredTaskIds.add(requiredTaskIdTag.getAsString());
                }
            }

            condition.requiredStatus = NPCMemorySystem.TaskProgress.TaskStatus.valueOf(tag.getString("required_status"));
            condition.minCompletionCount = tag.getInt("min_completion_count");
            condition.maxCompletionCount = tag.getInt("max_completion_count");
            condition.mustComplete = tag.getBoolean("must_complete");
            condition.mustFail = tag.getBoolean("must_fail");

            return condition;
        }
    }

    /**
     * 检查玩家行为条件
     * @param npcId NPC的UUID
     * @param condition 玩家行为条件
     * @return 是否满足条件
     */
    public boolean checkPlayerBehavior(UUID npcId, PlayerBehaviorCondition condition) {
        try {
            NPCMemorySystem.NPCMemoryData memoryData = memorySystem.getMemoryData(npcId);
            if (memoryData == null) {
                return false;
            }

            // 检查交互次数
            if (condition.getMinInteractionCount() > 0) {
                List<NPCMemorySystem.InteractionRecord> records = memoryData.episodicMemory.getInteractionRecords();
                if (records.size() < condition.getMinInteractionCount()) {
                    return false;
                }
            }

            // 检查必需物品
            if (condition.isMustHaveItem() && !condition.getRequiredItems().isEmpty()) {
                // 简化实现：假设玩家物品数据在其他系统中
                boolean hasItem = false; // 需要集成玩家物品系统
                if (!hasItem) {
                    return false;
                }
            }

            // 检查对话关键词
            if (!condition.getDialogueKeyword().isEmpty()) {
                List<NPCMemorySystem.InteractionRecord> records = memoryData.episodicMemory.getInteractionRecords();
                boolean hasKeyword = records.stream()
                    .anyMatch(record -> record.playerMessage.contains(condition.getDialogueKeyword()) ||
                                     record.npcResponse.contains(condition.getDialogueKeyword()));
                if (!hasKeyword) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            LOGGER.warning("Error checking player behavior condition for NPC " + npcId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查时间条件
     * @param condition 时间条件
     * @return 是否满足条件
     */
    public boolean checkTimeCondition(TimeCondition condition) {
        try {
            // 获取当前游戏时间（简化实现）
            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY);
            int dayOfWeek = now.get(Calendar.DAY_OF_WEEK);
            int dayOfMonth = now.get(Calendar.DAY_OF_MONTH);

            // 检查小时范围
            if (hour < condition.getGameHourStart() || hour > condition.getGameHourEnd()) {
                return false;
            }

            // 检查星期
            if (condition.getGameDayOfWeek() != -1 && dayOfWeek != condition.getGameDayOfWeek()) {
                return false;
            }

            // 检查日期
            if (condition.getGameDayOfMonth() != -1 && dayOfMonth != condition.getGameDayOfMonth()) {
                return false;
            }

            // 检查周期性触发
            if (condition.isPeriodicTrigger()) {
                long currentTime = System.currentTimeMillis();
                long lastCheckTime = (long) condition.getParameters().getOrDefault("last_check_time", 0L);
                if (currentTime - lastCheckTime < condition.getTriggerIntervalMinutes() * 60 * 1000) {
                    return false;
                }
                condition.getParameters().put("last_check_time", currentTime);
            }

            return true;
        } catch (Exception e) {
            LOGGER.warning("Error checking time condition: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查好感度条件
     * @param npcId NPC的UUID
     * @param condition 好感度条件
     * @return 是否满足条件
     */
    public boolean checkAffectionCondition(UUID npcId, AffectionCondition condition) {
        try {
            int affectionScore = affectionSystem.getAffectionScore(npcId);
            AffectionBondsSystem.AffinityLevel affectionLevel = AffectionBondsSystem.AffinityLevel.fromScore(affectionScore);

            // 检查好感度分数范围
            if (affectionScore < condition.getMinAffectionScore() || affectionScore > condition.getMaxAffectionScore()) {
                return false;
            }

            // 检查好感度等级范围
            if (affectionLevel.ordinal() < condition.getMinAffectionLevel().ordinal() ||
                affectionLevel.ordinal() > condition.getMaxAffectionLevel().ordinal()) {
                return false;
            }

            // 检查好感度变化阈值
            if (condition.getAffectionChangeThreshold() > 0) {
                List<AffectionBondsSystem.AffectionChangeRecord> history = affectionSystem.getOrCreateAffectionData(npcId).affectionHistory;
                if (!history.isEmpty()) {
                    AffectionBondsSystem.AffectionChangeRecord lastRecord = history.get(history.size() - 1);
                    if (Math.abs(lastRecord.change) < condition.getAffectionChangeThreshold()) {
                        return false;
                    }
                }
            }

            // 检查必须增加
            if (condition.isMustIncrease()) {
                if (!history.isEmpty()) {
                    AffectionBondsSystem.AffectionChangeRecord lastRecord = history.get(history.size() - 1);
                    if (lastRecord.change <= 0) {
                        return false;
                    }
                }
            }

            // 检查必须减少
            if (condition.isMustDecrease()) {
                if (!history.isEmpty()) {
                    AffectionBondsSystem.AffectionChangeRecord lastRecord = history.get(history.size() - 1);
                    if (lastRecord.change >= 0) {
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            LOGGER.warning("Error checking affection condition for NPC " + npcId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查任务条件
     * @param npcId NPC的UUID
     * @param condition 任务条件
     * @return 是否满足条件
     */
    public boolean checkTaskCondition(UUID npcId, TaskCondition condition) {
        try {
            List<NPCMemorySystem.TaskProgress> taskProgressList = memorySystem.getAllTaskProgress(npcId);

            // 检查必需任务状态
            if (!condition.getRequiredTaskIds().isEmpty()) {
                boolean allSatisfied = condition.getRequiredTaskIds().stream()
                    .allMatch(taskId -> {
                        NPCMemorySystem.TaskProgress progress = memorySystem.getTaskProgress(npcId, taskId);
                        return progress != null && progress.status == condition.getRequiredStatus();
                    });
                if (!allSatisfied) {
                    return false;
                }
            }

            // 检查任务完成数量
            long completedCount = taskProgressList.stream()
                .filter(progress -> progress.status == NPCMemorySystem.TaskProgress.TaskStatus.COMPLETED)
                .count();
            if (completedCount < condition.getMinCompletionCount() || completedCount > condition.getMaxCompletionCount()) {
                return false;
            }

            // 检查必须完成
            if (condition.isMustComplete()) {
                if (taskProgressList.isEmpty()) {
                    return false;
                }
                boolean hasCompleted = taskProgressList.stream()
                    .anyMatch(progress -> progress.status == NPCMemorySystem.TaskProgress.TaskStatus.COMPLETED);
                if (!hasCompleted) {
                    return false;
                }
            }

            // 检查必须失败
            if (condition.isMustFail()) {
                if (taskProgressList.isEmpty()) {
                    return false;
                }
                boolean hasFailed = taskProgressList.stream()
                    .anyMatch(progress -> progress.status == NPCMemorySystem.TaskProgress.TaskStatus.NOT_STARTED ||
                                         progress.status == NPCMemorySystem.TaskProgress.TaskStatus.IN_PROGRESS);
                if (!hasFailed) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            LOGGER.warning("Error checking task condition for NPC " + npcId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 评估复合条件
     * @param npcId NPC的UUID
     * @param condition 触发条件
     * @return 复合条件评估结果
     */
    public boolean evaluateCompositeCondition(UUID npcId, TriggerCondition condition) {
        try {
            boolean playerBehaviorResult = checkPlayerBehavior(npcId, condition.getPlayerBehavior());
            boolean timeResult = checkTimeCondition(condition.getTimeCondition());
            boolean affectionResult = checkAffectionCondition(npcId, condition.getAffectionCondition());
            boolean taskResult = checkTaskCondition(npcId, condition.getTaskCondition());

            switch (condition.getLogicOperator()) {
                case AND:
                    return playerBehaviorResult && timeResult && affectionResult && taskResult;
                case OR:
                    return playerBehaviorResult || timeResult || affectionResult || taskResult;
                case NOT:
                    return !(playerBehaviorResult && timeResult && affectionResult && taskResult);
                default:
                    return false;
            }
        } catch (Exception e) {
            LOGGER.warning("Error evaluating composite condition for NPC " + npcId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 计算条件优先级和权重
     * @param condition 触发条件
     * @return 优先级分数
     */
    public double calculateConditionPriority(TriggerCondition condition) {
        double basePriority = condition.getWeight();
        double timeFactor = 1.0;
        double affectionFactor = 1.0;
        double taskFactor = 1.0;

        // 时间条件权重
        TimeCondition timeCondition = condition.getTimeCondition();
        if (timeCondition.isPeriodicTrigger()) {
            timeFactor = 0.8;
        }

        // 好感度条件权重
        AffectionCondition affectionCondition = condition.getAffectionCondition();
        if (affectionCondition.getMinAffectionScore() > 50) {
            affectionFactor = 1.2;
        }

        // 任务条件权重
        TaskCondition taskCondition = condition.getTaskCondition();
        if (taskCondition.isMustComplete()) {
            taskFactor = 1.3;
        }

        return basePriority * timeFactor * affectionFactor * taskFactor;
    }

    // ==================================================================================
    // 4. 持久化存储模块
    // ==================================================================================

    /**
     * 压缩数据
     * @param data 原始数据
     * @return 压缩后的数据
     */
    private byte[] compressData(byte[] data) {
        try {
            Deflater deflater = new Deflater();
            deflater.setInput(data);
            deflater.finish();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            outputStream.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            LOGGER.warning("Failed to compress data: " + e.getMessage());
            return data;
        }
    }

    /**
     * 解压数据
     * @param data 压缩后的数据
     * @return 解压后的数据
     */
    private byte[] decompressData(byte[] data) {
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(data);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            outputStream.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            LOGGER.warning("Failed to decompress data: " + e.getMessage());
            return data;
        }
    }

    /**
     * 加密数据
     * @param data 原始数据
     * @return 加密后的数据
     */
    private byte[] encryptData(byte[] data) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(IV_PARAMETER.getBytes());
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            LOGGER.warning("Failed to encrypt data: " + e.getMessage());
            return data;
        }
    }

    /**
     * 解密数据
     * @param data 加密后的数据
     * @return 解密后的数据
     */
    private byte[] decryptData(byte[] data) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(IV_PARAMETER.getBytes());
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            LOGGER.warning("Failed to decrypt data: " + e.getMessage());
            return data;
        }
    }

    /**
     * 数据备份
     * @param backupPath 备份路径
     */
    public void backupData(String backupPath) {
        try {
            CompoundTag tag = serializeNBT();
            Path path = Paths.get(backupPath);
            Files.createDirectories(path.getParent());

            // 写入备份文件
            Files.write(path, tag.getAsString().getBytes());
            LOGGER.info("Data backup completed: " + backupPath);
        } catch (Exception e) {
            LOGGER.severe("Failed to backup data to " + backupPath + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 数据恢复
     * @param backupPath 备份路径
     */
    public void restoreData(String backupPath) {
        try {
            Path path = Paths.get(backupPath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Backup file not found: " + backupPath);
            }

            byte[] data = Files.readAllBytes(path);
            if (encryptionEnabled) {
                data = decryptData(data);
            }
            if (compressionEnabled) {
                data = decompressData(data);
            }

            String json = new String(data);
            CompoundTag tag = CompoundTag.valueOf(json);
            deserializeNBT(tag);
            setDirty();
            LOGGER.info("Data restore completed from: " + backupPath);
        } catch (Exception e) {
            LOGGER.severe("Failed to restore data from " + backupPath + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ==================================================================================
    // 5. 统一API接口层
    // ==================================================================================

    /**
     * 事件监听器接口
     */
    public interface EventListener {
        void onEvent(ScriptEvent event);
    }

    /**
     * 脚本事件类型
     */
    public enum ScriptEventType {
        SCRIPT_CREATED, SCRIPT_EDITED, SCRIPT_SAVED, SCRIPT_LOADED, SCRIPT_IMPORTED,
        EVENT_DEFINED, EVENT_ACTIVATED, EVENT_DEACTIVATED, EVENT_TRIGGERED
    }

    /**
     * 脚本事件类
     */
    public static class ScriptEvent {
        private ScriptEventType type;
        private String targetId;
        private Object data;

        public ScriptEvent(ScriptEventType type, String targetId, Object data) {
            this.type = type;
            this.targetId = targetId;
            this.data = data;
        }

        // Getters
        public ScriptEventType getType() { return type; }
        public String getTargetId() { return targetId; }
        public Object getData() { return data; }
    }

    /**
     * 注册事件监听器
     * @param listener 事件监听器
     */
    public void addEventListener(EventListener listener) {
        eventListeners.add(listener);
        LOGGER.info("Event listener registered.");
    }

    /**
     * 移除事件监听器
     * @param listener 事件监听器
     */
    public void removeEventListener(EventListener listener) {
        eventListeners.remove(listener);
        LOGGER.info("Event listener removed.");
    }

    /**
     * 触发事件
     * @param event 事件对象
     */
    private void fireEvent(ScriptEvent event) {
        for (EventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOGGER.warning("Error in event listener: " + e.getMessage());
            }
        }
    }

    /**
     * 创建脚本（API）
     * @param scriptId 脚本ID
     * @param scriptName 脚本名称
     * @param description 描述
     * @return 脚本数据对象
     */
    public ScriptData createScriptAPI(String scriptId, String scriptName, String description) {
        return createScript(scriptId, scriptName, description);
    }

    /**
     * 编辑脚本（API）
     * @param scriptId 脚本ID
     * @param content 新内容
     * @return 更新后的脚本数据
     */
    public ScriptData editScriptAPI(String scriptId, String content) {
        return editScript(scriptId, content);
    }

    /**
     * 保存脚本（API）
     * @param scriptId 脚本ID
     */
    public void saveScriptAPI(String scriptId) {
        saveScript(scriptId);
    }

    /**
     * 加载脚本（API）
     * @param scriptId 脚本ID
     * @return 脚本数据对象
     */
    public ScriptData loadScriptAPI(String scriptId) {
        return loadScript(scriptId);
    }

    /**
     * 定义事件（API）
     * @param eventId 事件ID
     * @param eventName 事件名称
     * @param eventType 事件类型
     * @return 事件数据对象
     */
    public EventData defineEventAPI(String eventId, String eventName, EventType eventType) {
        return defineEvent(eventId, eventName, eventType);
    }

    /**
     * 设置触发条件（API）
     * @param eventId 事件ID
     * @param condition 触发条件
     */
    public void setTriggerConditionAPI(String eventId, TriggerCondition condition) {
        setTriggerCondition(eventId, condition);
    }

    /**
     * 设置执行逻辑（API）
     * @param eventId 事件ID
     * @param logic 执行逻辑
     */
    public void setExecutionLogicAPI(String eventId, ExecutionLogic logic) {
        setExecutionLogic(eventId, logic);
    }

    /**
     * 激活事件（API）
     * @param eventId 事件ID
     */
    public void activateEventAPI(String eventId) {
        activateEvent(eventId);
    }

    /**
     * 检查玩家行为条件（API）
     * @param npcId NPC的UUID
     * @param condition 玩家行为条件
     * @return 是否满足条件
     */
    public boolean checkPlayerBehaviorAPI(UUID npcId, PlayerBehaviorCondition condition) {
        return checkPlayerBehavior(npcId, condition);
    }

    /**
     * 检查时间条件（API）
     * @param condition 时间条件
     * @return 是否满足条件
     */
    public boolean checkTimeConditionAPI(TimeCondition condition) {
        return checkTimeCondition(condition);
    }

    /**
     * 检查好感度条件（API）
     * @param npcId NPC的UUID
     * @param condition 好感度条件
     * @return 是否满足条件
     */
    public boolean checkAffectionConditionAPI(UUID npcId, AffectionCondition condition) {
        return checkAffectionCondition(npcId, condition);
    }

    /**
     * 检查任务条件（API）
     * @param npcId NPC的UUID
     * @param condition 任务条件
     * @return 是否满足条件
     */
    public boolean checkTaskConditionAPI(UUID npcId, TaskCondition condition) {
        return checkTaskCondition(npcId, condition);
    }

    /**
     * 与记忆系统集成
     * @param npcId NPC的UUID
     * @param memoryData 记忆数据
     */
    public void integrateWithMemorySystem(UUID npcId, NPCMemorySystem.NPCMemoryData memoryData) {
        try {
            // 从记忆系统读取数据，用于条件判断
            List<NPCMemorySystem.InteractionRecord> records = memoryData.episodicMemory.getInteractionRecords();
            int affectionScore = memoryData.affinityScore;
            LOGGER.fine("Integrated with memory system for NPC " + npcId + 
                       ", interaction count: " + records.size() + 
                       ", affection score: " + affectionScore);
        } catch (Exception e) {
            LOGGER.warning("Failed to integrate with memory system for NPC " + npcId + ": " + e.getMessage());
        }
    }

    /**
     * 与对话系统集成
     * @param npcId NPC的UUID
     * @param dialogueContext 对话上下文
     */
    public void integrateWithDialogueSystem(UUID npcId, ContextAwareDialogueSystem.DialogueContext dialogueContext) {
        try {
            // 监听对话事件，触发脚本和事件
            String lastMessage = dialogueContext.getLastPlayerMessage();
            if (lastMessage != null && !lastMessage.isEmpty()) {
                // 检查是否有对话触发事件
                eventMap.values().stream()
                    .filter(event -> event.isActive() && 
                                 event.getEventType() == EventType.DIALOGUE_TRIGGER)
                    .forEach(event -> {
                        // 评估触发条件
                        if (evaluateCompositeCondition(npcId, event.getTriggerCondition())) {
                            triggerEvent(event.getEventId(), npcId);
                        }
                    });
            }
            LOGGER.fine("Integrated with dialogue system for NPC " + npcId);
        } catch (Exception e) {
            LOGGER.warning("Failed to integrate with dialogue system for NPC " + npcId + ": " + e.getMessage());
        }
    }

    /**
     * 与好感度系统集成
     * @param npcId NPC的UUID
     * @param affectionData 好感度数据
     */
    public void integrateWithAffectionSystem(UUID npcId, AffectionBondsSystem.AffectionData affectionData) {
        try {
            // 监听好感度变化，触发事件
            int currentScore = affectionData.affectionScore;
            int lastScore = (int) affectionData.getContextVariable("last_affection_score");
            affectionData.setContextVariable("last_affection_score", currentScore);

            if (Math.abs(currentScore - lastScore) >= 10) {
                // 好感度变化超过阈值，检查触发条件
                eventMap.values().stream()
                    .filter(event -> event.isActive() && 
                                 event.getEventType() == EventType.AFFECTION_CHANGE)
                    .forEach(event -> {
                        if (evaluateCompositeCondition(npcId, event.getTriggerCondition())) {
                            triggerEvent(event.getEventId(), npcId);
                        }
                    });
            }
            LOGGER.fine("Integrated with affection system for NPC " + npcId);
        } catch (Exception e) {
            LOGGER.warning("Failed to integrate with affection system for NPC " + npcId + ": " + e.getMessage());
        }
    }

    /**
     * 与任务系统集成
     * @param npcId NPC的UUID
     * @param taskProgress 任务进度
     */
    public void integrateWithTaskSystem(UUID npcId, NPCMemorySystem.TaskProgress taskProgress) {
        try {
            // 监听任务状态变化，触发事件
            if (taskProgress.status == NPCMemorySystem.TaskProgress.TaskStatus.COMPLETED) {
                eventMap.values().stream()
                    .filter(event -> event.isActive() && 
                                 event.getEventType() == EventType.TASK_COMPLETED)
                    .forEach(event -> {
                        if (evaluateCompositeCondition(npcId, event.getTriggerCondition())) {
                            triggerEvent(event.getEventId(), npcId);
                        }
                    });
            }
            LOGGER.fine("Integrated with task system for NPC " + npcId);
        } catch (Exception e) {
            LOGGER.warning("Failed to integrate with task system for NPC " + npcId + ": " + e.getMessage());
        }
    }

    /**
     * 与家政战斗系统集成
     * @param npcId NPC的UUID
     * @param assistantState 助手状态
     */
    public void integrateWithHousekeepingSystem(UUID npcId, HousekeepingCombatAssistantSystem.AssistantState assistantState) {
        try {
            // 监听家政/战斗行为，触发事件
            HousekeepingCombatAssistantSystem.HouseholdTask currentTask = assistantState.getCurrentHouseholdTask();
            HousekeepingCombatAssistantSystem.CombatState combatState = assistantState.getCombatState();

            if (currentTask != null && currentTask.isCompleted()) {
                // 家政任务完成
                eventMap.values().stream()
                    .filter(event -> event.isActive() && 
                                 event.getEventType() == EventType.PLAYER_ACTION &&
                                 event.getTriggerCondition().getPlayerBehavior().getInteractionTypes().contains("HOUSEHOLD_COMPLETE"))
                    .forEach(event -> {
                        if (evaluateCompositeCondition(npcId, event.getTriggerCondition())) {
                            triggerEvent(event.getEventId(), npcId);
                        }
                    });
            }

            if (combatState.getCurrentMode() == HousekeepingCombatAssistantSystem.CombatState.Mode.COMBATING) {
                // 正在战斗
                eventMap.values().stream()
                    .filter(event -> event.isActive() && 
                                 event.getEventType() == EventType.PLAYER_ACTION &&
                                 event.getTriggerCondition().getPlayerBehavior().getInteractionTypes().contains("COMBAT_START"))
                    .forEach(event -> {
                        if (evaluateCompositeCondition(npcId, event.getTriggerCondition())) {
                            triggerEvent(event.getEventId(), npcId);
                        }
                    });
            }
            LOGGER.fine("Integrated with housekeeping system for NPC " + npcId);
        } catch (Exception e) {
            LOGGER.warning("Failed to integrate with housekeeping system for NPC " + npcId + ": " + e.getMessage());
        }
    }

    /**
     * 触发事件
     * @param eventId 事件ID
     * @param npcId NPC的UUID
     */
    public void triggerEvent(String eventId, UUID npcId) {
        try {
            EventData event = eventMap.get(eventId);
            if (event == null || !event.isActive()) {
                return;
            }

            // 检查触发条件
            if (!evaluateCompositeCondition(npcId, event.getTriggerCondition())) {
                return;
            }

            // 更新最后触发时间
            event.setLastTriggerTime(System.currentTimeMillis());
            setDirty();

            // 执行执行逻辑
            executeEventLogic(event, npcId);

            LOGGER.info("Event triggered: " + eventId + " for NPC " + npcId);

            // 触发事件
            fireEvent(new ScriptEvent(ScriptEventType.EVENT_TRIGGERED, eventId, event));
        } catch (Exception e) {
            LOGGER.severe("Failed to trigger event " + eventId + " for NPC " + npcId + ": " + e.getMessage());
        }
    }

    /**
     * 执行事件逻辑
     * @param event 事件数据
     * @param npcId NPC的UUID
     */
    private void executeEventLogic(EventData event, UUID npcId) {
        try {
            ExecutionLogic logic = event.getExecutionLogic();
            for (EventAction action : logic.getActions()) {
                switch (action.getType()) {
                    case RUN_SCRIPT:
                        if (action.getTargetId() != null) {
                            ScriptData script = scriptMap.get(action.getTargetId());
                            if (script != null) {
                                // 执行脚本逻辑（简化实现）
                                LOGGER.info("Executing script: " + script.getScriptName());
                            }
                        }
                        break;
                    case TRIGGER_DIALOGUE:
                        if (npcId != null && action.getContent() != null) {
                            // 触发对话
                            String response = dialogueSystem.processMessage(
                                npcId, 
                                UUID.randomUUID(), 
                                action.getContent(), 
                                memorySystem
                            );
                            LOGGER.info("Dialogue response: " + response);
                        }
                        break;
                    case MODIFY_AFFECTION:
                        if (npcId != null) {
                            affectionSystem.modifyAffectionScore(
                                affectionSystem.getOrCreateAffectionData(npcId), 
                                action.getValue(), 
                                "script_event:" + event.getEventId()
                            );
                        }
                        break;
                    case GENERATE_TASK:
                        if (npcId != null) {
                            // 生成任务（简化实现）
                            LOGGER.info("Generating task for NPC: " + npcId);
                        }
                        break;
                    case PERFORM_HOUSEHOLD:
                        if (npcId != null) {
                            // 执行家政任务
                            int progress = housekeepingSystem.performCleaning(npcId);
                            LOGGER.info("Household progress: " + progress);
                        }
                        break;
                    case PERFORM_COMBAT:
                        if (npcId != null) {
                            // 执行战斗行为
                            float guardStrength = housekeepingSystem.performGuard(npcId, 5.0f);
                            LOGGER.info("Combat guard strength: " + guardStrength);
                        }
                        break;
                    case UPDATE_MEMORY:
                        if (npcId != null && action.getContent() != null) {
                            memorySystem.updateMemory(npcId, new NPCMemorySystem.MemoryEntry(
                                "SCRIPT_EVENT", 
                                System.currentTimeMillis(), 
                                action.getValue() > 0 ? "positive" : "negative", 
                                action.getContent()
                            ));
                        }
                        break;
                    case SEND_NOTIFICATION:
                        if (action.getContent() != null) {
                            LOGGER.info("Notification: " + action.getContent());
                        }
                        break;
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to execute event logic for event " + event.getEventId() + ": " + e.getMessage());
        }
    }

    // ==================================================================================
    // SavedData 覆盖方法
    // ==================================================================================

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

        // 序列化脚本映射
        ListTag scriptListTag = new ListTag();
        for (Map.Entry<String, ScriptData> entry : scriptMap.entrySet()) {
            CompoundTag scriptTag = new CompoundTag();
            scriptTag.putString("key", entry.getKey());
            scriptTag.put("value", entry.getValue().serializeNBT());
            scriptListTag.add(scriptTag);
        }
        tag.put("script_map", scriptListTag);

        // 序列化事件映射
        ListTag eventListTag = new ListTag();
        for (Map.Entry<String, EventData> entry : eventMap.entrySet()) {
            CompoundTag eventTag = new CompoundTag();
            eventTag.putString("key", entry.getKey());
            eventTag.put("value", entry.getValue().serializeNBT());
            eventListTag.add(eventTag);
        }
        tag.put("event_map", eventListTag);

        // 序列化系统配置
        CompoundTag configTag = new CompoundTag();
        configTag.putBoolean("compression_enabled", compressionEnabled);
        configTag.putBoolean("encryption_enabled", encryptionEnabled);
        tag.put("config", configTag);

        return tag;
    }

    /**
     * 从NBT反序列化
     * @param tag NBT复合标签
     */
    public void deserializeNBT(CompoundTag tag) {
        scriptMap.clear();
        eventMap.clear();

        // 反序列化脚本映射
        if (tag.contains("script_map", 9)) {
            ListTag scriptListTag = tag.getList("script_map", 10);
            for (Tag scriptTag : scriptListTag) {
                if (scriptTag instanceof CompoundTag) {
                    CompoundTag scriptEntry = (CompoundTag) scriptTag;
                    String key = scriptEntry.getString("key");
                    ScriptData value = ScriptData.deserializeNBT(scriptEntry.getCompound("value"));
                    scriptMap.put(key, value);
                }
            }
        }

        // 反序列化事件映射
        if (tag.contains("event_map", 9)) {
            ListTag eventListTag = tag.getList("event_map", 10);
            for (Tag eventTag : eventListTag) {
                if (eventTag instanceof CompoundTag) {
                    CompoundTag eventEntry = (CompoundTag) eventTag;
                    String key = eventEntry.getString("key");
                    EventData value = EventData.deserializeNBT(eventEntry.getCompound("value"));
                    eventMap.put(key, value);
                }
            }
        }

        // 反序列化系统配置
        if (tag.contains("config", 10)) {
            CompoundTag configTag = tag.getCompound("config");
            compressionEnabled = configTag.getBoolean("compression_enabled");
            encryptionEnabled = configTag.getBoolean("encryption_enabled");
        }
    }
}