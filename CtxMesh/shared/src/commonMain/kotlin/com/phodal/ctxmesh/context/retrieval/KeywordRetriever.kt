package com.phodal.ctxmesh.context.retrieval

import com.phodal.ctxmesh.context.ContextContent
import com.phodal.ctxmesh.context.ContextPriority
import com.phodal.ctxmesh.context.ContextType
import com.phodal.ctxmesh.context.document.IndexedDocument
import kotlinx.datetime.Clock

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
        return "doc_${Clock.System.now().epochSeconds}_${(0..999).random()}"
    }
}