package com.phodal.ctxmesh.context

import com.phodal.ctxmesh.context.retrieval.*

object ContextFactory {
    
    /**
     * 创建基础的上下文管理器（无检索功能）
     */
    fun createBasic(tokenBudget: Int = 8192): ContextManager {
        return ContextManager(
            contextWindow = DefaultContextWindow(tokenBudget)
        )
    }
    
    /**
     * 创建带关键词检索的上下文管理器
     */
    fun createWithKeywordRetrieval(tokenBudget: Int = 8192): ContextManager {
        return ContextManager(
            contextWindow = DefaultContextWindow(tokenBudget),
            retriever = KeywordRetriever()
        )
    }
    
    /**
     * 创建带混合检索的上下文管理器
     */
    fun createWithHybridRetrieval(
        tokenBudget: Int = 8192,
        retrievers: List<ContextRetriever>? = null,
        weights: List<Double>? = null
    ): ContextManager {
        val defaultRetrievers = retrievers ?: listOf(
            KeywordRetriever()
            // 可以在这里添加更多检索器
        )
        
        val hybridRetriever = if (defaultRetrievers.size > 1) {
            HybridRetriever(defaultRetrievers, weights ?: List(defaultRetrievers.size) { 1.0 / defaultRetrievers.size })
        } else {
            defaultRetrievers.first()
        }
        
        return ContextManager(
            contextWindow = DefaultContextWindow(tokenBudget),
            retriever = hybridRetriever
        )
    }
    
    /**
     * 创建用于代码场景的上下文管理器
     */
    fun createForCoding(tokenBudget: Int = 16384): ContextManager {
        val manager = createWithKeywordRetrieval(tokenBudget)
        
        // 预设一些代码相关的系统提示词模板
        manager.addLongTermMemory(
            """
            You are an expert software developer. When analyzing code:
            1. Focus on code structure, patterns, and best practices
            2. Consider performance, maintainability, and readability
            3. Provide specific, actionable suggestions
            4. Reference relevant documentation when available
            """.trimIndent(),
            mapOf("type" to "coding_guidelines")
        )
        
        return manager
    }
    
    /**
     * 创建用于文档问答的上下文管理器
     */
    fun createForDocumentQA(tokenBudget: Int = 12288): ContextManager {
        val manager = createWithKeywordRetrieval(tokenBudget)
        
        // 预设文档问答的系统提示词
        manager.addLongTermMemory(
            """
            You are a helpful assistant that answers questions based on provided documentation.
            Always:
            1. Base your answers on the provided context
            2. Cite specific sections when possible
            3. Acknowledge when information is not available in the context
            4. Provide clear, structured responses
            """.trimIndent(),
            mapOf("type" to "qa_guidelines")
        )
        
        return manager
    }
}

