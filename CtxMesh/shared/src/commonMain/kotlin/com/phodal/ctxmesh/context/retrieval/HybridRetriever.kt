package com.phodal.ctxmesh.context.retrieval

import com.phodal.ctxmesh.context.ContextContent
import com.phodal.ctxmesh.context.ContextPriority

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