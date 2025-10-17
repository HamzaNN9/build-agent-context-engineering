package com.phodal.ctxmesh.memory.impl

import com.phodal.ctxmesh.memory.LongMemory
import com.phodal.ctxmesh.memory.LongMemoryConfig
import com.phodal.ctxmesh.memory.MemoryItem
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 默认长期记忆实现
 * 
 * 提供基于内存的长期记忆存储，支持语义检索和关联关系管理
 * 注意：这是一个简化的实现，生产环境中应该使用真正的向量数据库和持久化存储
 */
class DefaultLongMemory(
    config: LongMemoryConfig = LongMemoryConfig()
) : LongMemory() {
    
    override val config = config
    
    /**
     * 简化的持久化存储（实际应该使用数据库）
     */
    private val persistentStore = mutableMapOf<String, MemoryItem>()

    override fun generateMemoryId(): String {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val random = Random.nextInt(1000, 9999)
        return "long_${timestamp}_$random"
    }

    override suspend fun generateSemanticVector(content: String): List<Double> {
        // 简化的语义向量生成（实际应该使用预训练的嵌入模型）
        val words = content.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val vectorSize = 128
        val vector = DoubleArray(vectorSize) { 0.0 }
        
        // 基于词汇的简单哈希向量
        words.forEach { word ->
            val hash = word.hashCode()
            for (i in 0 until vectorSize) {
                val index = kotlin.math.abs((hash + i) % vectorSize)
                vector[index] += 1.0 / words.size
            }
        }
        
        // 归一化
        val magnitude = sqrt(vector.sumOf { it * it })
        if (magnitude > 0) {
            for (i in vector.indices) {
                vector[i] /= magnitude
            }
        }
        
        return vector.toList()
    }

    override suspend fun persistMemory(memory: MemoryItem) {
        // 简化的持久化实现
        persistentStore[memory.id] = memory
    }

    override suspend fun loadMemoryFromPersistence(memoryId: String): MemoryItem? {
        return persistentStore[memoryId]
    }

    override suspend fun deleteFromPersistence(memoryId: String) {
        persistentStore.remove(memoryId)
    }

    override suspend fun clearPersistence() {
        persistentStore.clear()
    }

    override suspend fun performKeywordSearch(query: String, maxResults: Int): List<MemoryItem> {
        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        
        return memoryIndex.values.mapNotNull { item ->
            val score = calculateKeywordRelevance(query, item)
            if (score > 0.1) item.copy(relevanceScore = score) else null
        }.sortedByDescending { it.relevanceScore }
         .take(maxResults)
    }

    override suspend fun performSemanticSearch(query: String, maxResults: Int): List<MemoryItem> {
        val queryVector = generateSemanticVector(query)
        
        return memoryIndex.values.mapNotNull { item ->
            val itemVector = semanticIndex[item.id]
            if (itemVector != null) {
                val similarity = calculateCosineSimilarity(queryVector, itemVector)
                if (similarity > 0.2) item.copy(relevanceScore = similarity) else null
            } else null
        }.sortedByDescending { it.relevanceScore }
         .take(maxResults)
    }

    override suspend fun performAssociativeSearch(query: String, maxResults: Int): List<MemoryItem> {
        // 首先找到与查询相关的记忆
        val directMatches = performKeywordSearch(query, 3)
        val results = mutableListOf<MemoryItem>()
        
        // 然后找到这些记忆的关联记忆
        directMatches.forEach { match ->
            val associations = associationGraph[match.id] ?: emptySet()
            associations.forEach { associatedId ->
                memoryIndex[associatedId]?.let { associatedItem ->
                    val score = calculateAssociativeRelevance(query, associatedItem)
                    if (score > 0.1) {
                        results.add(associatedItem.copy(relevanceScore = score))
                    }
                }
            }
        }
        
        return results.distinctBy { it.id }
            .sortedByDescending { it.relevanceScore }
            .take(maxResults)
    }

    override suspend fun findSimilarMemories(content: String, maxResults: Int): List<MemoryItem> {
        val contentVector = generateSemanticVector(content)

        return memoryIndex.values.mapNotNull { item ->
            val itemVector = semanticIndex[item.id]
            if (itemVector != null) {
                val similarity = calculateCosineSimilarity(contentVector, itemVector)
                if (similarity > 0.3) item.copy(relevanceScore = similarity) else null
            } else null
        }.sortedByDescending { it.relevanceScore }
         .take(maxResults)
    }

    /**
     * 同步版本的语义向量生成（用于非挂起函数）
     */
    private fun generateSemanticVectorSync(content: String): List<Double> {
        // 与 generateSemanticVector 相同的实现，但不是挂起函数
        val words = content.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val vectorSize = 128
        val vector = DoubleArray(vectorSize) { 0.0 }

        // 基于词汇的简单哈希向量
        words.forEach { word ->
            val hash = word.hashCode()
            for (i in 0 until vectorSize) {
                val index = kotlin.math.abs((hash + i) % vectorSize)
                vector[index] += 1.0 / words.size
            }
        }

        // 归一化
        val magnitude = sqrt(vector.sumOf { it * it })
        if (magnitude > 0) {
            for (i in vector.indices) {
                vector[i] /= magnitude
            }
        }

        return vector.toList()
    }

    override fun calculateKeywordRelevance(query: String, item: MemoryItem): Double {
        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val contentWords = item.content.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        
        if (queryWords.isEmpty() || contentWords.isEmpty()) {
            return 0.0
        }
        
        // TF-IDF 风格的计算
        var score = 0.0
        queryWords.forEach { queryWord ->
            val tf = contentWords.count { it.contains(queryWord) }.toDouble() / contentWords.size
            val idf = calculateIDF(queryWord)
            score += tf * idf
        }
        
        return min(1.0, score / queryWords.size)
    }

    override fun calculateSemanticRelevance(query: String, item: MemoryItem): Double {
        // 注意：这里应该缓存查询向量以避免重复计算
        // 为了简化，这里使用同步版本的向量生成
        val queryVector = generateSemanticVectorSync(query)
        val itemVector = semanticIndex[item.id] ?: return 0.0
        return calculateCosineSimilarity(queryVector, itemVector)
    }

    override fun calculateAssociativeRelevance(query: String, item: MemoryItem): Double {
        // 基于关联强度和重要性的评分
        val associations = associationGraph[item.id]?.size ?: 0
        val importance = getImportanceScore(item.id)
        val keywordScore = calculateKeywordRelevance(query, item)
        
        return (associations * 0.1 + importance * 0.4 + keywordScore * 0.5).coerceIn(0.0, 1.0)
    }

    override fun calculateTemporalRelevance(item: MemoryItem, now: Instant): Double {
        val age = now.toEpochMilliseconds() - item.timestamp.toEpochMilliseconds()
        val daysSinceCreation = age / (24 * 60 * 60 * 1000.0)
        
        // 长期记忆的时间衰减较慢
        val decayRate = 0.01
        return exp(-decayRate * daysSinceCreation)
    }

    /**
     * 计算余弦相似度
     */
    private fun calculateCosineSimilarity(vector1: List<Double>, vector2: List<Double>): Double {
        if (vector1.size != vector2.size) return 0.0
        
        val dotProduct = vector1.zip(vector2) { a, b -> a * b }.sum()
        val magnitude1 = sqrt(vector1.sumOf { it * it })
        val magnitude2 = sqrt(vector2.sumOf { it * it })
        
        return if (magnitude1 > 0 && magnitude2 > 0) {
            dotProduct / (magnitude1 * magnitude2)
        } else 0.0
    }

    /**
     * 计算逆文档频率（简化版）
     */
    private fun calculateIDF(word: String): Double {
        val totalDocs = memoryIndex.size
        if (totalDocs == 0) return 1.0
        
        val docsContainingWord = memoryIndex.values.count { 
            it.content.lowercase().contains(word.lowercase()) 
        }
        
        return if (docsContainingWord > 0) {
            kotlin.math.ln(totalDocs.toDouble() / docsContainingWord)
        } else 1.0
    }
}

/**
 * 长期记忆工厂类
 */
object LongMemoryFactory {
    
    /**
     * 创建默认的长期记忆实例
     */
    fun createDefault(): DefaultLongMemory {
        return DefaultLongMemory()
    }
    
    /**
     * 创建自定义配置的长期记忆实例
     */
    fun createWithConfig(config: LongMemoryConfig): DefaultLongMemory {
        return DefaultLongMemory(config)
    }
    
    /**
     * 创建适用于编程场景的长期记忆实例
     */
    fun createForCoding(): DefaultLongMemory {
        val config = LongMemoryConfig(
            minImportanceThreshold = 0.4,
            consolidationThreshold = 3,
            maxConsolidationLevel = 5,
            keywordWeight = 0.3,
            semanticWeight = 0.4,
            associativeWeight = 0.1,
            importanceWeight = 0.15,
            temporalWeight = 0.05
        )
        return DefaultLongMemory(config)
    }
    
    /**
     * 创建适用于知识管理的长期记忆实例
     */
    fun createForKnowledgeManagement(): DefaultLongMemory {
        val config = LongMemoryConfig(
            minImportanceThreshold = 0.5,
            consolidationThreshold = 5,
            maxConsolidationLevel = 3,
            keywordWeight = 0.2,
            semanticWeight = 0.5,
            associativeWeight = 0.2,
            importanceWeight = 0.1,
            temporalWeight = 0.0
        )
        return DefaultLongMemory(config)
    }
    
    /**
     * 创建适用于文档问答的长期记忆实例
     */
    fun createForDocumentQA(): DefaultLongMemory {
        val config = LongMemoryConfig(
            minImportanceThreshold = 0.3,
            consolidationThreshold = 2,
            maxConsolidationLevel = 4,
            keywordWeight = 0.4,
            semanticWeight = 0.3,
            associativeWeight = 0.15,
            importanceWeight = 0.1,
            temporalWeight = 0.05
        )
        return DefaultLongMemory(config)
    }
}
