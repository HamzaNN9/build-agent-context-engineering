package com.phodal.ctxmesh.context

import com.phodal.ctxmesh.context.retrieval.RetrievalStrategy

/**
 * 上下文配置类
 */
data class ContextConfig(
    val tokenBudget: Int = 8192,
    val enableRetrieval: Boolean = true,
    val retrievalStrategy: RetrievalStrategy = RetrievalStrategy.KEYWORD,
    val maxRetrievalResults: Int = 3,
    val enableQueryRewriting: Boolean = true,
    val shortTermMemoryMaxAge: Long = 3600000, // 1小时
    val autoCleanup: Boolean = true
)