package com.phodal.ctxmesh.context

import com.phodal.ctxmesh.context.retrieval.ContextRetriever
import com.phodal.ctxmesh.context.retrieval.HybridRetriever
import com.phodal.ctxmesh.context.retrieval.KeywordRetriever
import com.phodal.ctxmesh.context.retrieval.RetrievalStrategy
import com.phodal.ctxmesh.context.retrieval.rewrite.QueryRewriter
import com.phodal.ctxmesh.context.retrieval.rewrite.SimpleCodeQueryRewriter

/**
 * 高级上下文管理器构建器
 */
class ContextManagerBuilder {
    private var config = ContextConfig()
    private var customRetrievers = mutableListOf<ContextRetriever>()
    private var customQueryRewriter: QueryRewriter? = null

    fun withConfig(config: ContextConfig): ContextManagerBuilder {
        this.config = config
        return this
    }

    fun withTokenBudget(budget: Int): ContextManagerBuilder {
        this.config = config.copy(tokenBudget = budget)
        return this
    }

    fun withRetrievalStrategy(strategy: RetrievalStrategy): ContextManagerBuilder {
        this.config = config.copy(retrievalStrategy = strategy)
        return this
    }

    fun addCustomRetriever(retriever: ContextRetriever): ContextManagerBuilder {
        customRetrievers.add(retriever)
        return this
    }

    fun withQueryRewriter(rewriter: QueryRewriter): ContextManagerBuilder {
        this.customQueryRewriter = rewriter
        return this
    }

    fun build(): ContextManager {
        val contextWindow = DefaultContextWindow(config.tokenBudget)

        val retriever = if (config.enableRetrieval) {
            when {
                customRetrievers.isNotEmpty() -> {
                    if (customRetrievers.size == 1) {
                        customRetrievers.first()
                    } else {
                        HybridRetriever(customRetrievers)
                    }
                }
                config.retrievalStrategy == RetrievalStrategy.KEYWORD -> KeywordRetriever()
                config.retrievalStrategy == RetrievalStrategy.HYBRID -> {
                    HybridRetriever(listOf(KeywordRetriever()))
                }
                else -> KeywordRetriever() // 默认使用关键词检索
            }
        } else {
            null
        }

        val queryRewriter = if (config.enableQueryRewriting) {
            customQueryRewriter ?: SimpleCodeQueryRewriter()
        } else {
            SimpleCodeQueryRewriter()
        }

        return ContextManager(contextWindow, retriever, queryRewriter)
    }
}