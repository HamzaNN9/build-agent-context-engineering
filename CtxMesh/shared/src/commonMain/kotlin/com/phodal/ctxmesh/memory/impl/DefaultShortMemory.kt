package com.phodal.ctxmesh.memory.impl

import com.phodal.ctxmesh.memory.MemoryItem
import com.phodal.ctxmesh.memory.ShortMemory
import com.phodal.ctxmesh.memory.ShortMemoryConfig
import kotlinx.datetime.Clock
import kotlin.math.max
import kotlin.random.Random

/**
 * 默认短期记忆实现
 * 
 * 提供基于内存的短期记忆存储，适用于会话级别的上下文管理
 */
class DefaultShortMemory(
    config: ShortMemoryConfig = ShortMemoryConfig()
) : ShortMemory() {
    
    override val config = config

    override fun generateMemoryId(): String {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val random = Random.nextInt(1000, 9999)
        return "short_${timestamp}_$random"
    }

    override fun calculateRelevanceScore(query: String, item: MemoryItem): Double {
        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val contentWords = item.content.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        
        if (queryWords.isEmpty() || contentWords.isEmpty()) {
            return 0.0
        }
        
        // 计算词汇重叠度
        val intersection = queryWords.intersect(contentWords.toSet())
        val union = queryWords.union(contentWords.toSet())
        val jaccardSimilarity = if (union.isNotEmpty()) intersection.size.toDouble() / union.size else 0.0
        
        // 计算精确匹配分数
        val exactMatches = queryWords.count { queryWord ->
            contentWords.any { contentWord -> contentWord.contains(queryWord) }
        }
        val exactMatchScore = exactMatches.toDouble() / queryWords.size
        
        // 计算位置权重（查询词在内容中的位置）
        val positionScore = calculatePositionScore(query, item.content)
        
        // 计算元数据匹配分数
        val metadataScore = calculateMetadataScore(query, item)
        
        // 综合评分
        return (jaccardSimilarity * 0.4 + 
                exactMatchScore * 0.3 + 
                positionScore * 0.2 + 
                metadataScore * 0.1).coerceIn(0.0, 1.0)
    }
    
    /**
     * 计算位置权重分数
     * 查询词在内容开头出现的权重更高
     */
    private fun calculatePositionScore(query: String, content: String): Double {
        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val contentLower = content.lowercase()
        
        var totalScore = 0.0
        var foundWords = 0
        
        queryWords.forEach { word ->
            val index = contentLower.indexOf(word)
            if (index >= 0) {
                foundWords++
                // 位置越靠前，分数越高
                val positionWeight = max(0.1, 1.0 - (index.toDouble() / content.length))
                totalScore += positionWeight
            }
        }
        
        return if (foundWords > 0) totalScore / queryWords.size else 0.0
    }
    
    /**
     * 计算元数据匹配分数
     */
    private fun calculateMetadataScore(query: String, item: MemoryItem): Double {
        val queryLower = query.lowercase()
        var score = 0.0
        
        // 检查标签匹配
        item.metadata["tags"]?.let { tags ->
            when (tags) {
                is List<*> -> {
                    val tagMatches = tags.filterIsInstance<String>()
                        .count { tag -> queryLower.contains(tag.lowercase()) }
                    if (tags.isNotEmpty()) {
                        score += (tagMatches.toDouble() / tags.size) * 0.5
                    }
                }
                is String -> {
                    if (queryLower.contains(tags.lowercase())) {
                        score += 0.5
                    }
                }
            }
        }
        
        // 检查类型匹配
        item.metadata["type"]?.let { type ->
            if (queryLower.contains(type.toString().lowercase())) {
                score += 0.3
            }
        }
        
        // 检查上下文匹配
        item.metadata["context"]?.let { context ->
            if (queryLower.contains(context.toString().lowercase())) {
                score += 0.2
            }
        }
        
        return score.coerceIn(0.0, 1.0)
    }
}

/**
 * 短期记忆工厂类
 */
object ShortMemoryFactory {
    
    /**
     * 创建默认的短期记忆实例
     */
    fun createDefault(): DefaultShortMemory {
        return DefaultShortMemory()
    }
    
    /**
     * 创建自定义配置的短期记忆实例
     */
    fun createWithConfig(config: ShortMemoryConfig): DefaultShortMemory {
        return DefaultShortMemory(config)
    }
    
    /**
     * 创建适用于编程场景的短期记忆实例
     */
    fun createForCoding(): DefaultShortMemory {
        val config = ShortMemoryConfig(
            maxCapacity = 500,
            maxAge = 7200000, // 2小时
            decayRate = 1.5,
            relevanceWeight = 0.6,
            temporalWeight = 0.2,
            accessWeight = 0.2
        )
        return DefaultShortMemory(config)
    }
    
    /**
     * 创建适用于对话场景的短期记忆实例
     */
    fun createForConversation(): DefaultShortMemory {
        val config = ShortMemoryConfig(
            maxCapacity = 200,
            maxAge = 1800000, // 30分钟
            decayRate = 3.0,
            relevanceWeight = 0.4,
            temporalWeight = 0.4,
            accessWeight = 0.2
        )
        return DefaultShortMemory(config)
    }
    
    /**
     * 创建适用于文档问答场景的短期记忆实例
     */
    fun createForDocumentQA(): DefaultShortMemory {
        val config = ShortMemoryConfig(
            maxCapacity = 300,
            maxAge = 3600000, // 1小时
            decayRate = 2.0,
            relevanceWeight = 0.7,
            temporalWeight = 0.2,
            accessWeight = 0.1
        )
        return DefaultShortMemory(config)
    }
}
