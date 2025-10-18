package com.phodal.ctxmesh.memory

import com.phodal.ctxmesh.context.ContextContent
import com.phodal.ctxmesh.context.ContextPriority
import com.phodal.ctxmesh.context.ContextType
import kotlinx.datetime.Instant

/**
 * Memory 系统的核心接口
 * 基于 README.md 中的记忆系统设计理念，提供统一的记忆管理抽象
 */
interface Memory {
    /**
     * 存储记忆内容
     * @param content 要存储的内容
     * @param metadata 元数据信息
     * @return 存储成功返回记忆ID，失败返回null
     */
    suspend fun store(content: String, metadata: Map<String, Any> = emptyMap()): String?

    /**
     * 根据查询检索相关记忆
     * @param query 查询内容
     * @param maxResults 最大返回结果数
     * @param threshold 相关性阈值
     * @return 检索到的记忆列表，按相关性排序
     */
    suspend fun retrieve(query: String, maxResults: Int = 5, threshold: Double = 0.0): List<MemoryItem>

    /**
     * 根据ID获取特定记忆
     * @param memoryId 记忆ID
     * @return 记忆项，不存在返回null
     */
    suspend fun get(memoryId: String): MemoryItem?

    /**
     * 更新记忆内容
     * @param memoryId 记忆ID
     * @param content 新内容
     * @param metadata 新元数据
     * @return 更新是否成功
     */
    suspend fun update(memoryId: String, content: String, metadata: Map<String, Any> = emptyMap()): Boolean

    /**
     * 删除记忆
     * @param memoryId 记忆ID
     * @return 删除是否成功
     */
    suspend fun delete(memoryId: String): Boolean

    /**
     * 清理过期或不相关的记忆
     * @param criteria 清理条件
     * @return 清理的记忆数量
     */
    suspend fun cleanup(criteria: CleanupCriteria = CleanupCriteria()): Int

    /**
     * 获取记忆统计信息
     * @return 记忆统计
     */
    suspend fun getStats(): MemoryStats

    /**
     * 清空所有记忆
     */
    suspend fun clear()
}

/**
 * 记忆项数据类
 */
data class MemoryItem(
    val id: String,
    val content: String,
    val metadata: Map<String, Any> = emptyMap(),
    val timestamp: Instant,
    val lastAccessed: Instant = timestamp,
    val accessCount: Int = 0,
    val relevanceScore: Double = 0.0
) {
    /**
     * 转换为 ContextContent
     */
    fun toContextContent(type: ContextType, priority: ContextPriority): ContextContent {
        return ContextContent(
            id = id,
            type = type,
            content = content,
            priority = priority,
            metadata = metadata,
            timestamp = timestamp.toEpochMilliseconds()
        )
    }
}

/**
 * 清理条件
 */
data class CleanupCriteria(
    val maxAge: Long? = null, // 最大年龄（毫秒）
    val minAccessCount: Int? = null, // 最小访问次数
    val maxItems: Int? = null, // 最大保留项数
    val relevanceThreshold: Double? = null // 相关性阈值
)

/**
 * 记忆统计信息
 */
data class MemoryStats(
    val totalItems: Int,
    val totalSize: Long, // 字节
    val oldestItem: Instant?,
    val newestItem: Instant?,
    val averageAccessCount: Double,
    val memoryType: MemoryType
)

/**
 * 记忆类型枚举
 */
enum class MemoryType {
    /** 短期记忆 - 会话级别，自动过期 */
    SHORT_TERM,
    
    /** 长期记忆 - 持久化存储，语义检索 */
    LONG_TERM,
    
    /** 工作记忆 - 临时计算状态 */
    WORKING,
    
    /** 情景记忆 - 特定事件或对话 */
    EPISODIC,
    
    /** 语义记忆 - 结构化知识 */
    SEMANTIC
}

/**
 * 记忆事件接口
 */
interface MemoryEvent {
    val timestamp: Instant
    val memoryId: String
    val eventType: MemoryEventType
}

/**
 * 记忆事件类型
 */
enum class MemoryEventType {
    STORED,
    RETRIEVED,
    UPDATED,
    DELETED,
    ACCESSED,
    EXPIRED
}

/**
 * 记忆监听器接口
 */
interface MemoryListener {
    suspend fun onMemoryEvent(event: MemoryEvent)
}
