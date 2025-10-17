package com.phodal.ctxmesh.memory

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.max
import kotlin.math.min

/**
 * 长期记忆抽象类
 * 
 * 基于 README.md 中的长期记忆概念设计：
 * - 持久化存储机制
 * - 语义检索和向量搜索
 * - 知识图谱和关联关系
 * - 重要性评估和记忆巩固
 * - 分层存储架构
 */
abstract class LongMemory : Memory {
    
    /**
     * 默认配置
     */
    protected open val config = LongMemoryConfig()
    
    /**
     * 记忆索引 - 用于快速检索
     */
    protected val memoryIndex = mutableMapOf<String, MemoryItem>()
    
    /**
     * 语义索引 - 用于语义搜索
     */
    protected val semanticIndex = mutableMapOf<String, List<Double>>()
    
    /**
     * 关联图谱 - 记忆之间的关联关系
     */
    protected val associationGraph = mutableMapOf<String, MutableSet<String>>()
    
    /**
     * 重要性分数缓存
     */
    protected val importanceCache = mutableMapOf<String, Double>()
    
    /**
     * 记忆监听器
     */
    protected val listeners = mutableListOf<MemoryListener>()

    override suspend fun store(content: String, metadata: Map<String, Any>): String? {
        val memoryId = generateMemoryId()
        val now = Clock.System.now()
        
        // 计算重要性分数
        val importance = calculateImportance(content, metadata)
        
        // 检查是否值得长期存储
        if (importance < config.minImportanceThreshold) {
            return null
        }
        
        val memoryItem = MemoryItem(
            id = memoryId,
            content = content,
            metadata = metadata + mapOf(
                "memoryType" to MemoryType.LONG_TERM.name,
                "importance" to importance,
                "consolidationLevel" to 0
            ),
            timestamp = now,
            lastAccessed = now,
            accessCount = 1,
            relevanceScore = importance
        )
        
        // 存储到索引
        memoryIndex[memoryId] = memoryItem
        
        // 生成语义向量
        val semanticVector = generateSemanticVector(content)
        semanticIndex[memoryId] = semanticVector
        
        // 建立关联关系
        establishAssociations(memoryId, content, metadata)
        
        // 持久化存储
        persistMemory(memoryItem)
        
        // 更新重要性缓存
        importanceCache[memoryId] = importance
        
        // 触发事件
        notifyListeners(SimpleMemoryEvent(now, memoryId, MemoryEventType.STORED))
        
        return memoryId
    }

    override suspend fun retrieve(query: String, maxResults: Int, threshold: Double): List<MemoryItem> {
        val now = Clock.System.now()
        
        // 多种检索策略组合
        val keywordResults = performKeywordSearch(query, maxResults)
        val semanticResults = performSemanticSearch(query, maxResults)
        val associativeResults = performAssociativeSearch(query, maxResults)
        
        // 合并和去重
        val allResults = (keywordResults + semanticResults + associativeResults)
            .distinctBy { it.id }
        
        // 重新计算综合相关性分数
        val scoredResults = allResults.map { item ->
            val keywordScore = calculateKeywordRelevance(query, item)
            val semanticScore = calculateSemanticRelevance(query, item)
            val associativeScore = calculateAssociativeRelevance(query, item)
            val importanceScore = getImportanceScore(item.id)
            val temporalScore = calculateTemporalRelevance(item, now)
            
            val combinedScore = config.keywordWeight * keywordScore +
                              config.semanticWeight * semanticScore +
                              config.associativeWeight * associativeScore +
                              config.importanceWeight * importanceScore +
                              config.temporalWeight * temporalScore
            
            item.copy(relevanceScore = combinedScore)
        }
        
        // 过滤、排序和限制结果
        val finalResults = scoredResults
            .filter { it.relevanceScore >= threshold }
            .sortedByDescending { it.relevanceScore }
            .take(maxResults)
        
        // 更新访问记录和巩固记忆
        finalResults.forEach { item ->
            updateAccessRecord(item.id, now)
            consolidateMemory(item.id)
            notifyListeners(SimpleMemoryEvent(now, item.id, MemoryEventType.RETRIEVED))
        }
        
        return finalResults
    }

    override suspend fun get(memoryId: String): MemoryItem? {
        val item = memoryIndex[memoryId] ?: loadMemoryFromPersistence(memoryId)
        
        if (item != null) {
            val now = Clock.System.now()
            updateAccessRecord(memoryId, now)
            consolidateMemory(memoryId)
            notifyListeners(SimpleMemoryEvent(now, memoryId, MemoryEventType.ACCESSED))
        }
        
        return item
    }

    override suspend fun update(memoryId: String, content: String, metadata: Map<String, Any>): Boolean {
        val existingItem = memoryIndex[memoryId] ?: return false
        
        val now = Clock.System.now()
        val importance = calculateImportance(content, metadata)
        
        val updatedItem = existingItem.copy(
            content = content,
            metadata = metadata + mapOf(
                "memoryType" to MemoryType.LONG_TERM.name,
                "importance" to importance,
                "lastModified" to now.toEpochMilliseconds()
            ),
            lastAccessed = now,
            accessCount = existingItem.accessCount + 1
        )
        
        // 更新索引
        memoryIndex[memoryId] = updatedItem
        
        // 重新生成语义向量
        val semanticVector = generateSemanticVector(content)
        semanticIndex[memoryId] = semanticVector
        
        // 重新建立关联关系
        clearAssociations(memoryId)
        establishAssociations(memoryId, content, metadata)
        
        // 持久化更新
        persistMemory(updatedItem)
        
        // 更新重要性缓存
        importanceCache[memoryId] = importance
        
        notifyListeners(SimpleMemoryEvent(now, memoryId, MemoryEventType.UPDATED))
        
        return true
    }

    override suspend fun delete(memoryId: String): Boolean {
        val removed = memoryIndex.remove(memoryId) != null
        
        if (removed) {
            semanticIndex.remove(memoryId)
            clearAssociations(memoryId)
            importanceCache.remove(memoryId)
            deleteFromPersistence(memoryId)
            
            val now = Clock.System.now()
            notifyListeners(SimpleMemoryEvent(now, memoryId, MemoryEventType.DELETED))
        }
        
        return removed
    }

    override suspend fun cleanup(criteria: CleanupCriteria): Int {
        val now = Clock.System.now()
        var cleanedCount = 0
        
        val toRemove = mutableListOf<String>()
        
        memoryIndex.forEach { (id, item) ->
            var shouldRemove = false
            
            // 检查年龄（长期记忆通常不基于年龄清理）
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
            
            // 检查重要性
            criteria.relevanceThreshold?.let { threshold ->
                val importance = getImportanceScore(id)
                if (importance < threshold) {
                    shouldRemove = true
                }
            }
            
            if (shouldRemove) {
                toRemove.add(id)
            }
        }
        
        // 处理最大项数限制
        criteria.maxItems?.let { maxItems ->
            if (memoryIndex.size - toRemove.size > maxItems) {
                val excess = memoryIndex.size - toRemove.size - maxItems
                val sortedByImportance = memoryIndex.values
                    .filter { !toRemove.contains(it.id) }
                    .sortedBy { getImportanceScore(it.id) }
                    .take(excess)
                
                toRemove.addAll(sortedByImportance.map { it.id })
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
        val items = memoryIndex.values
        
        return MemoryStats(
            totalItems = items.size,
            totalSize = items.sumOf { it.content.length.toLong() },
            oldestItem = items.minByOrNull { it.timestamp }?.timestamp,
            newestItem = items.maxByOrNull { it.timestamp }?.timestamp,
            averageAccessCount = if (items.isNotEmpty()) items.map { it.accessCount }.average() else 0.0,
            memoryType = MemoryType.LONG_TERM
        )
    }

    override suspend fun clear() {
        memoryIndex.clear()
        semanticIndex.clear()
        associationGraph.clear()
        importanceCache.clear()
        clearPersistence()
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

    /**
     * 获取记忆的关联记忆
     */
    suspend fun getAssociatedMemories(memoryId: String, maxResults: Int = 5): List<MemoryItem> {
        val associations = associationGraph[memoryId] ?: return emptyList()
        
        return associations.mapNotNull { associatedId ->
            memoryIndex[associatedId]
        }.sortedByDescending { getImportanceScore(it.id) }
         .take(maxResults)
    }

    // 抽象方法，由具体实现类提供
    protected abstract fun generateMemoryId(): String
    protected abstract suspend fun generateSemanticVector(content: String): List<Double>
    protected abstract suspend fun persistMemory(memory: MemoryItem)
    protected abstract suspend fun loadMemoryFromPersistence(memoryId: String): MemoryItem?
    protected abstract suspend fun deleteFromPersistence(memoryId: String)
    protected abstract suspend fun clearPersistence()

    // 受保护的辅助方法
    protected fun calculateImportance(content: String, metadata: Map<String, Any>): Double {
        var importance = 0.0
        
        // 基于内容长度
        importance += min(1.0, content.length / 1000.0) * 0.2
        
        // 基于元数据
        metadata["priority"]?.let { priority ->
            when (priority.toString().lowercase()) {
                "high" -> importance += 0.3
                "medium" -> importance += 0.2
                "low" -> importance += 0.1
            }
        }
        
        // 基于关键词密度
        val keywordDensity = calculateKeywordDensity(content)
        importance += keywordDensity * 0.3
        
        // 基于结构化程度
        val structureScore = calculateStructureScore(content)
        importance += structureScore * 0.2
        
        return min(1.0, importance)
    }

    protected fun calculateKeywordDensity(content: String): Double {
        // 简化实现：基于特殊字符和格式化的密度
        val specialChars = content.count { it in "{}[]()\"'`" }
        return min(1.0, specialChars.toDouble() / content.length)
    }

    protected fun calculateStructureScore(content: String): Double {
        // 简化实现：基于换行符和缩进的结构化程度
        val lines = content.lines()
        val indentedLines = lines.count { it.startsWith(" ") || it.startsWith("\t") }
        return if (lines.isNotEmpty()) indentedLines.toDouble() / lines.size else 0.0
    }

    protected fun getImportanceScore(memoryId: String): Double {
        return importanceCache[memoryId] ?: 0.0
    }

    protected suspend fun establishAssociations(memoryId: String, content: String, metadata: Map<String, Any>) {
        // 基于内容相似性建立关联
        val similarMemories = findSimilarMemories(content, 5)
        val associations = associationGraph.getOrPut(memoryId) { mutableSetOf() }
        
        similarMemories.forEach { similarMemory ->
            if (similarMemory.id != memoryId) {
                associations.add(similarMemory.id)
                // 双向关联
                associationGraph.getOrPut(similarMemory.id) { mutableSetOf() }.add(memoryId)
            }
        }
    }

    protected fun clearAssociations(memoryId: String) {
        val associations = associationGraph[memoryId] ?: return
        
        // 清除双向关联
        associations.forEach { associatedId ->
            associationGraph[associatedId]?.remove(memoryId)
        }
        
        associationGraph.remove(memoryId)
    }

    protected suspend fun consolidateMemory(memoryId: String) {
        val item = memoryIndex[memoryId] ?: return
        val currentLevel = item.metadata["consolidationLevel"] as? Int ?: 0
        
        if (item.accessCount > config.consolidationThreshold && currentLevel < config.maxConsolidationLevel) {
            val newLevel = min(config.maxConsolidationLevel, currentLevel + 1)
            val updatedMetadata = item.metadata + mapOf("consolidationLevel" to newLevel)
            val updatedItem = item.copy(metadata = updatedMetadata)
            
            memoryIndex[memoryId] = updatedItem
            persistMemory(updatedItem)
        }
    }

    protected fun updateAccessRecord(memoryId: String, timestamp: Instant) {
        val item = memoryIndex[memoryId] ?: return
        val updatedItem = item.copy(
            lastAccessed = timestamp,
            accessCount = item.accessCount + 1
        )
        memoryIndex[memoryId] = updatedItem
    }

    protected suspend fun notifyListeners(event: MemoryEvent) {
        listeners.forEach { it.onMemoryEvent(event) }
    }

    // 抽象的检索方法，由具体实现提供
    protected abstract suspend fun performKeywordSearch(query: String, maxResults: Int): List<MemoryItem>
    protected abstract suspend fun performSemanticSearch(query: String, maxResults: Int): List<MemoryItem>
    protected abstract suspend fun performAssociativeSearch(query: String, maxResults: Int): List<MemoryItem>
    protected abstract suspend fun findSimilarMemories(content: String, maxResults: Int): List<MemoryItem>
    protected abstract fun calculateKeywordRelevance(query: String, item: MemoryItem): Double
    protected abstract fun calculateSemanticRelevance(query: String, item: MemoryItem): Double
    protected abstract fun calculateAssociativeRelevance(query: String, item: MemoryItem): Double
    protected abstract fun calculateTemporalRelevance(item: MemoryItem, now: Instant): Double
}

/**
 * 长期记忆配置
 */
data class LongMemoryConfig(
    val minImportanceThreshold: Double = 0.3,
    val consolidationThreshold: Int = 5,
    val maxConsolidationLevel: Int = 3,
    val keywordWeight: Double = 0.25,
    val semanticWeight: Double = 0.35,
    val associativeWeight: Double = 0.15,
    val importanceWeight: Double = 0.15,
    val temporalWeight: Double = 0.1
)
