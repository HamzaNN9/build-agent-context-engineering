package com.phodal.ctxmesh.memory

import com.phodal.ctxmesh.context.ContextContent
import com.phodal.ctxmesh.context.ContextPriority
import com.phodal.ctxmesh.context.ContextType
import com.phodal.ctxmesh.memory.impl.DefaultLongMemory
import com.phodal.ctxmesh.memory.impl.DefaultShortMemory
import com.phodal.ctxmesh.memory.impl.LongMemoryFactory
import com.phodal.ctxmesh.memory.impl.ShortMemoryFactory
import kotlinx.datetime.Clock

/**
 * 记忆管理器
 * 
 * 统一管理短期记忆和长期记忆，提供智能的记忆存储、检索和转移机制
 * 基于 README.md 中的记忆系统设计理念
 */
class MemoryManager(
    private val shortMemory: ShortMemory = ShortMemoryFactory.createDefault(),
    private val longMemory: LongMemory = LongMemoryFactory.createDefault(),
    private val config: MemoryManagerConfig = MemoryManagerConfig()
) {
    
    /**
     * 记忆监听器，用于处理记忆转移等事件
     */
    private val memoryTransferListener = MemoryTransferListener()
    
    init {
        // 注册记忆转移监听器
        shortMemory.addListener(memoryTransferListener)
    }

    /**
     * 存储记忆内容
     * 根据内容重要性自动决定存储到短期还是长期记忆
     */
    suspend fun store(content: String, metadata: Map<String, Any> = emptyMap()): String? {
        val importance = calculateContentImportance(content, metadata)
        
        return if (importance >= config.longTermThreshold) {
            // 直接存储到长期记忆
            longMemory.store(content, metadata + mapOf("autoStored" to true))
        } else {
            // 存储到短期记忆
            shortMemory.store(content, metadata + mapOf("importance" to importance))
        }
    }

    /**
     * 检索记忆内容
     * 同时从短期和长期记忆中检索，并合并结果
     */
    suspend fun retrieve(query: String, maxResults: Int = 10, threshold: Double = 0.0): List<MemoryItem> {
        // 并行检索短期和长期记忆
        val shortTermResults = shortMemory.retrieve(query, maxResults / 2, threshold)
        val longTermResults = longMemory.retrieve(query, maxResults / 2, threshold)
        
        // 合并结果并去重
        val allResults = (shortTermResults + longTermResults).distinctBy { it.id }
        
        // 重新排序，考虑记忆类型的权重
        val rerankedResults = allResults.map { item ->
            val typeWeight = if (item.metadata["memoryType"] == MemoryType.SHORT_TERM.name) {
                config.shortTermWeight
            } else {
                config.longTermWeight
            }
            
            item.copy(relevanceScore = item.relevanceScore * typeWeight)
        }
        
        return rerankedResults
            .sortedByDescending { it.relevanceScore }
            .take(maxResults)
    }

    /**
     * 根据ID获取记忆
     */
    suspend fun get(memoryId: String): MemoryItem? {
        // 首先尝试从短期记忆获取
        shortMemory.get(memoryId)?.let { return it }
        
        // 然后尝试从长期记忆获取
        return longMemory.get(memoryId)
    }

    /**
     * 更新记忆内容
     */
    suspend fun update(memoryId: String, content: String, metadata: Map<String, Any> = emptyMap()): Boolean {
        // 尝试更新短期记忆
        if (shortMemory.update(memoryId, content, metadata)) {
            return true
        }
        
        // 尝试更新长期记忆
        return longMemory.update(memoryId, content, metadata)
    }

    /**
     * 删除记忆
     */
    suspend fun delete(memoryId: String): Boolean {
        val shortDeleted = shortMemory.delete(memoryId)
        val longDeleted = longMemory.delete(memoryId)
        return shortDeleted || longDeleted
    }

    /**
     * 手动将短期记忆转移到长期记忆
     */
    suspend fun promoteToLongTerm(memoryId: String): Boolean {
        val shortTermItem = shortMemory.get(memoryId) ?: return false
        
        // 存储到长期记忆
        val longTermId = longMemory.store(
            shortTermItem.content,
            shortTermItem.metadata + mapOf("promotedFrom" to memoryId)
        )
        
        if (longTermId != null) {
            // 从短期记忆中删除
            shortMemory.delete(memoryId)
            return true
        }
        
        return false
    }

    /**
     * 自动记忆巩固
     * 将重要的短期记忆转移到长期记忆
     */
    suspend fun consolidateMemories(): Int {
        val shortTermStats = shortMemory.getStats()
        var consolidatedCount = 0
        
        // 获取所有短期记忆
        val allShortTermMemories = shortMemory.retrieve("", Int.MAX_VALUE, 0.0)
        
        // 筛选需要巩固的记忆
        val candidatesForConsolidation = allShortTermMemories.filter { item ->
            val importance = item.metadata["importance"] as? Double ?: 0.0
            val accessCount = item.accessCount
            
            // 基于重要性、访问次数和年龄决定是否巩固
            importance >= config.consolidationImportanceThreshold ||
            accessCount >= config.consolidationAccessThreshold
        }
        
        // 执行巩固
        candidatesForConsolidation.forEach { item ->
            if (promoteToLongTerm(item.id)) {
                consolidatedCount++
            }
        }
        
        return consolidatedCount
    }

    /**
     * 清理过期记忆
     */
    suspend fun cleanup(): MemoryCleanupResult {
        val shortTermCleaned = shortMemory.cleanup()
        val longTermCleaned = longMemory.cleanup()
        
        return MemoryCleanupResult(
            shortTermCleaned = shortTermCleaned,
            longTermCleaned = longTermCleaned,
            totalCleaned = shortTermCleaned + longTermCleaned
        )
    }

    /**
     * 获取记忆统计信息
     */
    suspend fun getStats(): MemoryManagerStats {
        val shortTermStats = shortMemory.getStats()
        val longTermStats = longMemory.getStats()
        
        return MemoryManagerStats(
            shortTermStats = shortTermStats,
            longTermStats = longTermStats,
            totalItems = shortTermStats.totalItems + longTermStats.totalItems,
            totalSize = shortTermStats.totalSize + longTermStats.totalSize
        )
    }

    /**
     * 转换记忆项为上下文内容
     */
    fun memoryToContext(memoryItem: MemoryItem): ContextContent {
        val contextType = when (memoryItem.metadata["memoryType"]) {
            MemoryType.SHORT_TERM.name -> ContextType.SHORT_TERM_MEMORY
            MemoryType.LONG_TERM.name -> ContextType.LONG_TERM_MEMORY
            else -> ContextType.EXTERNAL_KNOWLEDGE
        }
        
        val priority = when {
            memoryItem.relevanceScore >= 0.8 -> ContextPriority.HIGHEST
            memoryItem.relevanceScore >= 0.6 -> ContextPriority.HIGH
            memoryItem.relevanceScore >= 0.4 -> ContextPriority.MEDIUM
            memoryItem.relevanceScore >= 0.2 -> ContextPriority.LOW
            else -> ContextPriority.LOWEST
        }
        
        return memoryItem.toContextContent(contextType, priority)
    }

    /**
     * 清空所有记忆
     */
    suspend fun clear() {
        shortMemory.clear()
        longMemory.clear()
    }

    /**
     * 计算内容重要性
     */
    private fun calculateContentImportance(content: String, metadata: Map<String, Any>): Double {
        var importance = 0.0
        
        // 基于内容长度
        importance += kotlin.math.min(1.0, content.length / 500.0) * 0.2
        
        // 基于元数据中的优先级
        metadata["priority"]?.let { priority ->
            when (priority.toString().lowercase()) {
                "high" -> importance += 0.4
                "medium" -> importance += 0.2
                "low" -> importance += 0.1
            }
        }
        
        // 基于内容类型
        metadata["type"]?.let { type ->
            when (type.toString().lowercase()) {
                "knowledge", "definition", "concept" -> importance += 0.3
                "example", "code" -> importance += 0.2
                "note", "comment" -> importance += 0.1
            }
        }
        
        // 基于结构化程度
        val structureScore = calculateStructureScore(content)
        importance += structureScore * 0.1
        
        return kotlin.math.min(1.0, importance)
    }

    /**
     * 计算内容结构化程度
     */
    private fun calculateStructureScore(content: String): Double {
        val lines = content.lines()
        val structuredLines = lines.count { line ->
            line.trim().startsWith("-") || 
            line.trim().startsWith("*") || 
            line.trim().startsWith("1.") ||
            line.contains(":")
        }
        
        return if (lines.isNotEmpty()) {
            structuredLines.toDouble() / lines.size
        } else 0.0
    }

    /**
     * 记忆转移监听器
     */
    private inner class MemoryTransferListener : MemoryListener {
        override suspend fun onMemoryEvent(event: MemoryEvent) {
            when (event.eventType) {
                MemoryEventType.RETRIEVED -> {
                    // 检查是否需要自动巩固
                    val item = shortMemory.get(event.memoryId)
                    if (item != null && shouldAutoConsolidate(item)) {
                        promoteToLongTerm(event.memoryId)
                    }
                }
                else -> {
                    // 其他事件暂不处理
                }
            }
        }
        
        private fun shouldAutoConsolidate(item: MemoryItem): Boolean {
            val importance = item.metadata["importance"] as? Double ?: 0.0
            return importance >= config.autoConsolidationThreshold && 
                   item.accessCount >= config.autoConsolidationAccessThreshold
        }
    }
}

/**
 * 记忆管理器配置
 */
data class MemoryManagerConfig(
    val longTermThreshold: Double = 0.7,
    val consolidationImportanceThreshold: Double = 0.5,
    val consolidationAccessThreshold: Int = 3,
    val autoConsolidationThreshold: Double = 0.8,
    val autoConsolidationAccessThreshold: Int = 5,
    val shortTermWeight: Double = 1.2,
    val longTermWeight: Double = 1.0
)

/**
 * 记忆清理结果
 */
data class MemoryCleanupResult(
    val shortTermCleaned: Int,
    val longTermCleaned: Int,
    val totalCleaned: Int
)

/**
 * 记忆管理器统计信息
 */
data class MemoryManagerStats(
    val shortTermStats: MemoryStats,
    val longTermStats: MemoryStats,
    val totalItems: Int,
    val totalSize: Long
)
