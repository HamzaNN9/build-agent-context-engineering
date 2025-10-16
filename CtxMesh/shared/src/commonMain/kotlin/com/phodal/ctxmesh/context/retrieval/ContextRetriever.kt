package com.phodal.ctxmesh.context.retrieval

import com.phodal.ctxmesh.context.ContextContent
import com.phodal.ctxmesh.context.ContextType
import com.phodal.ctxmesh.context.ContextPriority

import kotlinx.datetime.Clock

/**
 * 上下文检索器接口
 * 基于 README.md 中的检索增强生成(RAG)理念
 */
interface ContextRetriever {
    /**
     * 根据查询检索相关上下文
     * @param query 查询内容
     * @param maxResults 最大返回结果数
     * @return 检索到的上下文内容列表
     */
    suspend fun retrieve(query: String, maxResults: Int = 5): List<ContextContent>
    
    /**
     * 添加内容到检索索引
     * @param content 要索引的内容
     */
    suspend fun index(content: String, metadata: Map<String, Any> = emptyMap()): String
    
    /**
     * 从索引中移除内容
     * @param contentId 内容ID
     */
    suspend fun remove(contentId: String): Boolean
}

/**
 * 检索策略枚举
 * 基于 README.md 中提到的多种检索策略
 */
enum class RetrievalStrategy {
    /** 关键词检索 */
    KEYWORD,
    
    /** 语义化检索 */
    SEMANTIC,
    
    /** 混合检索 */
    HYBRID,
    
    /** 图检索 */
    GRAPH
}

/**
 * 查询改写器接口
 * 用于优化用户查询以提升检索效果
 */
interface QueryRewriter {
    /**
     * 改写查询以提升检索效果
     * @param originalQuery 原始查询
     * @return 改写后的查询列表
     */
    suspend fun rewrite(originalQuery: String): List<String>
}

/**
 * 简单的关键词检索器实现
 * 基于文本匹配的基础检索
 */
class KeywordRetriever : ContextRetriever {
    private val documents = mutableMapOf<String, IndexedDocument>()
    
    override suspend fun retrieve(query: String, maxResults: Int): List<ContextContent> {
        val queryTerms = query.lowercase().split("\\s+".toRegex())
        
        val results = documents.values
            .map { doc -> doc to calculateRelevanceScore(doc, queryTerms) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { (doc, score) ->
                ContextContent(
                    id = doc.id,
                    type = ContextType.EXTERNAL_KNOWLEDGE,
                    content = doc.content,
                    priority = scoreToPriority(score),
                    metadata = doc.metadata + mapOf("relevance_score" to score)
                )
            }
        
        return results
    }
    
    override suspend fun index(content: String, metadata: Map<String, Any>): String {
        val id = generateId()
        val doc = IndexedDocument(
            id = id,
            content = content,
            metadata = metadata,
            terms = extractTerms(content)
        )
        documents[id] = doc
        return id
    }
    
    override suspend fun remove(contentId: String): Boolean {
        return documents.remove(contentId) != null
    }
    
    private fun calculateRelevanceScore(doc: IndexedDocument, queryTerms: List<String>): Double {
        val matchingTerms = queryTerms.count { term -> 
            doc.terms.any { it.contains(term) || term.contains(it) }
        }
        return matchingTerms.toDouble() / queryTerms.size
    }
    
    private fun extractTerms(content: String): Set<String> {
        return content.lowercase()
            .split("\\W+".toRegex())
            .filter { it.length > 2 }
            .toSet()
    }
    
    private fun scoreToPriority(score: Double): ContextPriority {
        return when {
            score >= 0.8 -> ContextPriority.HIGHEST
            score >= 0.6 -> ContextPriority.HIGH
            score >= 0.4 -> ContextPriority.MEDIUM
            score >= 0.2 -> ContextPriority.LOW
            else -> ContextPriority.LOWEST
        }
    }
    
    private fun generateId(): String {
        return "doc_${kotlinx.datetime.Clock.System.now().epochSeconds}_${(0..999).random()}"
    }
}

/**
 * 混合检索器
 * 结合多种检索策略的实现
 */
class HybridRetriever(
    private val retrievers: List<ContextRetriever>,
    private val weights: List<Double> = List(retrievers.size) { 1.0 / retrievers.size }
) : ContextRetriever {
    
    init {
        require(retrievers.size == weights.size) { "检索器数量必须与权重数量相等" }
        require(weights.sum() == 1.0) { "权重总和必须为1.0" }
    }
    
    override suspend fun retrieve(query: String, maxResults: Int): List<ContextContent> {
        val allResults = mutableMapOf<String, Pair<ContextContent, Double>>()
        
        // 从所有检索器获取结果
        retrievers.forEachIndexed { index, retriever ->
            val results = retriever.retrieve(query, maxResults * 2) // 获取更多结果用于融合
            val weight = weights[index]
            
            results.forEach { content ->
                val currentScore = content.metadata["relevance_score"] as? Double ?: 0.5
                val weightedScore = currentScore * weight
                
                val existing = allResults[content.id]
                if (existing != null) {
                    // 如果已存在，合并分数
                    val newScore = existing.second + weightedScore
                    allResults[content.id] = existing.first to newScore
                } else {
                    allResults[content.id] = content to weightedScore
                }
            }
        }
        
        // 按合并后的分数排序并返回
        return allResults.values
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { (content, score) ->
                content.copy(
                    metadata = content.metadata + mapOf("hybrid_score" to score),
                    priority = scoreToPriority(score)
                )
            }
    }
    
    override suspend fun index(content: String, metadata: Map<String, Any>): String {
        // 在所有检索器中索引
        var lastId = ""
        retrievers.forEach { retriever ->
            lastId = retriever.index(content, metadata)
        }
        return lastId
    }
    
    override suspend fun remove(contentId: String): Boolean {
        return retrievers.map { it.remove(contentId) }.any { it }
    }
    
    private fun scoreToPriority(score: Double): ContextPriority {
        return when {
            score >= 0.8 -> ContextPriority.HIGHEST
            score >= 0.6 -> ContextPriority.HIGH
            score >= 0.4 -> ContextPriority.MEDIUM
            score >= 0.2 -> ContextPriority.LOW
            else -> ContextPriority.LOWEST
        }
    }
}

/**
 * 索引文档数据类
 */
data class IndexedDocument(
    val id: String,
    val content: String,
    val metadata: Map<String, Any>,
    val terms: Set<String>
)

/**
 * 简单的查询改写器实现
 */
class SimpleQueryRewriter : QueryRewriter {
    override suspend fun rewrite(originalQuery: String): List<String> {
        val rewrites = mutableListOf<String>()
        
        // 原始查询
        rewrites.add(originalQuery)
        
        // 添加同义词扩展（简化实现）
        val synonyms = mapOf(
            "function" to listOf("method", "procedure", "routine"),
            "class" to listOf("type", "object", "entity"),
            "variable" to listOf("field", "property", "attribute"),
            "error" to listOf("exception", "bug", "issue"),
            "implement" to listOf("create", "build", "develop")
        )
        
        var expandedQuery = originalQuery
        synonyms.forEach { (word, syns) ->
            if (originalQuery.contains(word, ignoreCase = true)) {
                syns.forEach { syn ->
                    rewrites.add(originalQuery.replace(word, syn, ignoreCase = true))
                }
            }
        }
        
        // 添加问题形式的改写
        if (!originalQuery.endsWith("?")) {
            rewrites.add("How to $originalQuery?")
            rewrites.add("What is $originalQuery?")
        }
        
        return rewrites.distinct()
    }
}
