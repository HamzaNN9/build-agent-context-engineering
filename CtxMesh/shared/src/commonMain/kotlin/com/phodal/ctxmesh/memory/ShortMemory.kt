package com.phodal.ctxmesh.memory

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.exp
import kotlin.math.max

/**
 * 短期记忆抽象类
 * 
 * 基于 README.md 中的短期记忆概念设计：
 * - 会话级别的上下文管理
 * - 时间衰减机制
 * - 自动清理过期内容
 * - 基于新近度和频率的优先级排序
 */
abstract class ShortMemory : Memory {
    
    /**
     * 默认配置
     */
    protected open val config = ShortMemoryConfig()
    
    /**
     * 内存存储
     */
    protected val memoryStore = mutableMapOf<String, MemoryItem>()
    
    /**
     * 访问历史记录
     */
    protected val accessHistory = mutableMapOf<String, MutableList<Instant>>()
    
    /**
     * 记忆监听器
     */
    protected val listeners = mutableListOf<MemoryListener>()

    override suspend fun store(content: String, metadata: Map<String, Any>): String? {
        // 检查容量限制
        if (memoryStore.size >= config.maxCapacity) {
            performCapacityCleanup()
        }
        
        val memoryId = generateMemoryId()
        val now = Clock.System.now()
        
        val memoryItem = MemoryItem(
            id = memoryId,
            content = content,
            metadata = metadata + mapOf("memoryType" to MemoryType.SHORT_TERM.name),
            timestamp = now,
            lastAccessed = now,
            accessCount = 1
        )
        
        memoryStore[memoryId] = memoryItem
        accessHistory[memoryId] = mutableListOf(now)
        
        // 触发事件
        notifyListeners(SimpleMemoryEvent(now, memoryId, MemoryEventType.STORED))
        
        return memoryId
    }

    override suspend fun retrieve(query: String, maxResults: Int, threshold: Double): List<MemoryItem> {
        // 清理过期记忆
        cleanupExpiredMemories()
        
        val now = Clock.System.now()
        val candidates = memoryStore.values.toList()
        
        // 计算相关性分数
        val scoredItems = candidates.map { item ->
            val relevanceScore = calculateRelevanceScore(query, item)
            val temporalScore = calculateTemporalScore(item, now)
            val accessScore = calculateAccessScore(item)
            
            val combinedScore = config.relevanceWeight * relevanceScore +
                              config.temporalWeight * temporalScore +
                              config.accessWeight * accessScore
            
            item.copy(relevanceScore = combinedScore)
        }
        
        // 过滤和排序
        val filteredItems = scoredItems
            .filter { it.relevanceScore >= threshold }
            .sortedByDescending { it.relevanceScore }
            .take(maxResults)
        
        // 更新访问记录
        filteredItems.forEach { item ->
            updateAccessRecord(item.id, now)
            notifyListeners(SimpleMemoryEvent(now, item.id, MemoryEventType.RETRIEVED))
        }
        
        return filteredItems
    }

    override suspend fun get(memoryId: String): MemoryItem? {
        val item = memoryStore[memoryId] ?: return null
        
        // 检查是否过期
        if (isExpired(item)) {
            delete(memoryId)
            return null
        }
        
        val now = Clock.System.now()
        updateAccessRecord(memoryId, now)
        notifyListeners(SimpleMemoryEvent(now, memoryId, MemoryEventType.ACCESSED))
        
        return item.copy(lastAccessed = now, accessCount = item.accessCount + 1)
    }

    override suspend fun update(memoryId: String, content: String, metadata: Map<String, Any>): Boolean {
        val existingItem = memoryStore[memoryId] ?: return false
        
        if (isExpired(existingItem)) {
            delete(memoryId)
            return false
        }
        
        val now = Clock.System.now()
        val updatedItem = existingItem.copy(
            content = content,
            metadata = metadata + mapOf("memoryType" to MemoryType.SHORT_TERM.name),
            lastAccessed = now,
            accessCount = existingItem.accessCount + 1
        )
        
        memoryStore[memoryId] = updatedItem
        updateAccessRecord(memoryId, now)
        notifyListeners(SimpleMemoryEvent(now, memoryId, MemoryEventType.UPDATED))
        
        return true
    }

    override suspend fun delete(memoryId: String): Boolean {
        val removed = memoryStore.remove(memoryId) != null
        accessHistory.remove(memoryId)
        
        if (removed) {
            val now = Clock.System.now()
            notifyListeners(SimpleMemoryEvent(now, memoryId, MemoryEventType.DELETED))
        }
        
        return removed
    }

    override suspend fun cleanup(criteria: CleanupCriteria): Int {
        val now = Clock.System.now()
        var cleanedCount = 0
        
        val toRemove = mutableListOf<String>()
        
        memoryStore.forEach { (id, item) ->
            var shouldRemove = false
            
            // 检查年龄
            criteria.maxAge?.let { maxAge ->
                if (now.toEpochMilliseconds() - item.timestamp.toEpochMilliseconds() > maxAge) {
                    shouldRemove = true
                }
            }
            
            // 检查访问次数
            criteria.minAccessCount?.let { minCount ->
                if (item.accessCount < minCount) {
                    shouldRemove = true
                }
            }
            
            // 检查相关性
            criteria.relevanceThreshold?.let { threshold ->
                if (item.relevanceScore < threshold) {
                    shouldRemove = true
                }
            }
            
            // 检查是否过期
            if (isExpired(item)) {
                shouldRemove = true
            }
            
            if (shouldRemove) {
                toRemove.add(id)
            }
        }
        
        // 处理最大项数限制
        criteria.maxItems?.let { maxItems ->
            if (memoryStore.size - toRemove.size > maxItems) {
                val excess = memoryStore.size - toRemove.size - maxItems
                val sortedByPriority = memoryStore.values
                    .filter { !toRemove.contains(it.id) }
                    .sortedBy { calculatePriority(it, now) }
                    .take(excess)
                
                toRemove.addAll(sortedByPriority.map { it.id })
            }
        }
        
        // 执行删除
        toRemove.forEach { id ->
            delete(id)
            cleanedCount++
        }
        
        return cleanedCount
    }

    override suspend fun getStats(): MemoryStats {
        val items = memoryStore.values
        val now = Clock.System.now()
        
        return MemoryStats(
            totalItems = items.size,
            totalSize = items.sumOf { it.content.length.toLong() },
            oldestItem = items.minByOrNull { it.timestamp }?.timestamp,
            newestItem = items.maxByOrNull { it.timestamp }?.timestamp,
            averageAccessCount = if (items.isNotEmpty()) items.map { it.accessCount }.average() else 0.0,
            memoryType = MemoryType.SHORT_TERM
        )
    }

    override suspend fun clear() {
        memoryStore.clear()
        accessHistory.clear()
    }

    /**
     * 添加记忆监听器
     */
    fun addListener(listener: MemoryListener) {
        listeners.add(listener)
    }

    /**
     * 移除记忆监听器
     */
    fun removeListener(listener: MemoryListener) {
        listeners.remove(listener)
    }

    // 抽象方法，由具体实现类提供
    protected abstract fun generateMemoryId(): String
    protected abstract fun calculateRelevanceScore(query: String, item: MemoryItem): Double

    // 受保护的辅助方法
    protected fun isExpired(item: MemoryItem): Boolean {
        val now = Clock.System.now()
        val age = now.toEpochMilliseconds() - item.lastAccessed.toEpochMilliseconds()
        return age > config.maxAge
    }

    protected fun calculateTemporalScore(item: MemoryItem, now: Instant): Double {
        val age = now.toEpochMilliseconds() - item.lastAccessed.toEpochMilliseconds()
        val normalizedAge = age.toDouble() / config.maxAge
        return exp(-config.decayRate * normalizedAge)
    }

    protected fun calculateAccessScore(item: MemoryItem): Double {
        return max(0.0, 1.0 - 1.0 / (1.0 + item.accessCount))
    }

    protected fun calculatePriority(item: MemoryItem, now: Instant): Double {
        val temporalScore = calculateTemporalScore(item, now)
        val accessScore = calculateAccessScore(item)
        return config.temporalWeight * temporalScore + config.accessWeight * accessScore
    }

    protected fun updateAccessRecord(memoryId: String, timestamp: Instant) {
        val item = memoryStore[memoryId] ?: return
        val updatedItem = item.copy(
            lastAccessed = timestamp,
            accessCount = item.accessCount + 1
        )
        memoryStore[memoryId] = updatedItem
        
        accessHistory.getOrPut(memoryId) { mutableListOf() }.add(timestamp)
    }

    protected suspend fun notifyListeners(event: MemoryEvent) {
        listeners.forEach { it.onMemoryEvent(event) }
    }

    protected suspend fun cleanupExpiredMemories() {
        val criteria = CleanupCriteria(maxAge = config.maxAge)
        cleanup(criteria)
    }

    protected suspend fun performCapacityCleanup() {
        val criteria = CleanupCriteria(maxItems = (config.maxCapacity * 0.8).toInt())
        cleanup(criteria)
    }
}

/**
 * 短期记忆配置
 */
data class ShortMemoryConfig(
    val maxCapacity: Int = 1000,
    val maxAge: Long = 3600000, // 1小时
    val decayRate: Double = 2.0,
    val relevanceWeight: Double = 0.5,
    val temporalWeight: Double = 0.3,
    val accessWeight: Double = 0.2
)

/**
 * 简单记忆事件实现
 */
data class SimpleMemoryEvent(
    override val timestamp: Instant,
    override val memoryId: String,
    override val eventType: MemoryEventType
) : MemoryEvent
