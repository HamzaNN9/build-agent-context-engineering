package com.phodal.ctxmesh

import com.phodal.ctxmesh.context.ContextContent
import com.phodal.ctxmesh.context.ContextPriority
import com.phodal.ctxmesh.context.ContextType
import com.phodal.ctxmesh.context.ContextWindow
import com.phodal.ctxmesh.context.ContextWindowStatus
import com.phodal.ctxmesh.context.DefaultContextWindow
import com.phodal.ctxmesh.context.retrieval.ContextRetriever
import com.phodal.ctxmesh.context.retrieval.rewrite.QueryRewriter
import com.phodal.ctxmesh.context.retrieval.rewrite.SimpleCodeQueryRewriter
import com.phodal.ctxmesh.memory.CleanupCriteria
import com.phodal.ctxmesh.memory.MemoryCleanupResult
import com.phodal.ctxmesh.memory.MemoryItem
import com.phodal.ctxmesh.memory.MemoryManager
import com.phodal.ctxmesh.memory.MemoryManagerStats
import kotlinx.coroutines.runBlocking

/**
 * 带记忆系统的上下文管理器
 * 
 * 扩展了基础的 ContextManager，集成了 MemoryManager 来提供智能的记忆管理功能
 */
class ContextManagerWithMemory(
    private val contextWindow: ContextWindow = DefaultContextWindow(),
    private val retriever: ContextRetriever? = null,
    private val queryRewriter: QueryRewriter = SimpleCodeQueryRewriter(),
    private val memoryManager: MemoryManager
) {
    
    /**
     * 基于查询构建上下文窗口（带记忆增强）
     */
    fun buildContextForQuery(
        query: String,
        systemPrompt: String? = null,
        outputFormat: String? = null,
        maxRetrievalResults: Int = 3,
        maxMemoryResults: Int = 5
    ): String = runBlocking {
        // 清空现有上下文
        contextWindow.clear()

        // 1. 添加系统提示词（最高优先级）
        systemPrompt?.let {
            contextWindow.addContext(
                ContextContent(
                    id = "system_prompt",
                    type = ContextType.SYSTEM_PROMPT,
                    content = it,
                    priority = ContextPriority.HIGHEST
                )
            )
        }

        // 2. 添加输出格式说明
        outputFormat?.let {
            contextWindow.addContext(
                ContextContent(
                    id = "output_format",
                    type = ContextType.OUTPUT_FORMAT,
                    content = it,
                    priority = ContextPriority.HIGH
                )
            )
        }

        // 3. 从记忆系统检索相关内容
        val memoryResults = memoryManager.retrieve(query, maxMemoryResults)
        memoryResults.forEach { memoryItem ->
            val contextContent = memoryManager.memoryToContext(memoryItem)
            contextWindow.addContext(contextContent)
        }

        // 4. 使用传统检索器检索外部知识（如果配置了）
        retriever?.let { ret ->
            val rewrittenQueries = queryRewriter.rewrite(query)
            rewrittenQueries.forEach { rewrittenQuery ->
                val retrievalResults = ret.retrieve(rewrittenQuery, maxRetrievalResults)
                retrievalResults.forEach { result ->
                    contextWindow.addContext(result)
                }
            }
        }

        // 5. 添加用户查询（中等优先级）
        contextWindow.addContext(
            ContextContent(
                id = "user_query",
                type = ContextType.USER_INPUT,
                content = query,
                priority = ContextPriority.MEDIUM
            )
        )

        // 6. 将当前查询存储到短期记忆
        memoryManager.store(
            "User query: $query",
            mapOf("type" to "query", "priority" to "medium")
        )

        return@runBlocking contextWindow.assembleContext()
    }

    /**
     * 添加内容到记忆系统
     */
    suspend fun addToMemory(content: String, metadata: Map<String, Any> = emptyMap()): String? {
        return memoryManager.store(content, metadata)
    }

    /**
     * 从记忆系统检索内容
     */
    suspend fun retrieveFromMemory(query: String, maxResults: Int = 5): List<MemoryItem> {
        return memoryManager.retrieve(query, maxResults)
    }

    /**
     * 添加短期记忆内容
     */
    fun addShortTermMemory(content: String, metadata: Map<String, Any> = emptyMap()) = runBlocking {
        memoryManager.store(content, metadata + mapOf("memoryType" to "SHORT_TERM"))
    }

    /**
     * 添加长期记忆内容
     */
    fun addLongTermMemory(content: String, metadata: Map<String, Any> = emptyMap()) = runBlocking {
        memoryManager.store(content, metadata + mapOf("priority" to "high", "type" to "knowledge"))
    }

    /**
     * 手动巩固记忆
     */
    suspend fun consolidateMemories(): Int {
        return memoryManager.consolidateMemories()
    }

    /**
     * 清理过期记忆
     */
    suspend fun cleanupMemories(): MemoryCleanupResult {
        return memoryManager.cleanup()
    }

    /**
     * 获取记忆统计信息
     */
    suspend fun getMemoryStats(): MemoryManagerStats {
        return memoryManager.getStats()
    }

    /**
     * 获取当前上下文窗口状态
     */
    fun getWindowStatus(): ContextWindowStatus {
        return ContextWindowStatus(
            totalTokenBudget = contextWindow.tokenBudget,
            usedTokens = contextWindow.usedTokens,
            remainingTokens = contextWindow.remainingTokens,
            contextCount = contextWindow.getAllContexts().size,
            contextsByType = contextWindow.getAllContexts().groupBy { it.type }
        )
    }

    /**
     * 清理过期的短期记忆
     */
    fun cleanupShortTermMemory(maxAge: Long = 3600000) = runBlocking {
        val criteria = CleanupCriteria(maxAge = maxAge)
        memoryManager.cleanup()
    }

    /**
     * 构建器模式支持
     */
    fun builder(): ContextBuilderWithMemory {
        return ContextBuilderWithMemory(this)
    }

    /**
     * 清空所有内容
     */
    suspend fun clear() {
        contextWindow.clear()
        memoryManager.clear()
    }
}

