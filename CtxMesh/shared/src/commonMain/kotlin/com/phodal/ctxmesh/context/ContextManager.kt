package com.phodal.ctxmesh.context

import com.phodal.ctxmesh.context.retrieval.ContextRetriever
import com.phodal.ctxmesh.context.retrieval.rewrite.QueryRewriter
import com.phodal.ctxmesh.context.retrieval.rewrite.SimpleCodeQueryRewriter

/**
 * 上下文管理器
 * 整合上下文窗口和检索功能，提供统一的上下文工程接口
 */
class ContextManager(
    private val contextWindow: ContextWindow = DefaultContextWindow(),
    private val retriever: ContextRetriever? = null,
    private val queryRewriter: QueryRewriter = SimpleCodeQueryRewriter()
) {
    /**
     * 基于查询构建上下文窗口
     * 这是上下文工程的核心方法
     */
    fun buildContextForQuery(
        query: String,
        systemPrompt: String? = null,
        outputFormat: String? = null,
        maxRetrievalResults: Int = 3
    ): String {
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

        // 3. 添加用户查询
        contextWindow.addContext(
            ContextContent(
                id = "user_query",
                type = ContextType.USER_INPUT,
                content = query,
                priority = ContextPriority.HIGH
            )
        )

        // 4. 检索相关知识并添加到上下文（简化版本，暂时跳过检索）
        // TODO: 实现异步检索功能
        retriever?.let { ret ->
            // 暂时跳过检索功能，避免协程复杂性
            // 可以在后续版本中添加异步支持
        }

        return contextWindow.assembleContext()
    }

    /**
     * 添加长期记忆内容
     */
    fun addLongTermMemory(content: String, metadata: Map<String, Any> = emptyMap()) {
        contextWindow.addContext(
            ContextContent(
                id = "memory_${kotlinx.datetime.Clock.System.now().epochSeconds}",
                type = ContextType.LONG_TERM_MEMORY,
                content = content,
                priority = ContextPriority.MEDIUM,
                metadata = metadata
            )
        )
    }

    /**
     * 添加短期记忆内容（会话上下文）
     */
    fun addShortTermMemory(content: String, metadata: Map<String, Any> = emptyMap()) {
        contextWindow.addContext(
            ContextContent(
                id = "recent_${kotlinx.datetime.Clock.System.now().epochSeconds}",
                type = ContextType.SHORT_TERM_MEMORY,
                content = content,
                priority = ContextPriority.HIGH,
                metadata = metadata
            )
        )
    }

    /**
     * 添加工具上下文
     */
    fun addToolContext(toolName: String, toolDescription: String, toolSchema: String) {
        contextWindow.addContext(
            ContextContent(
                id = "tool_$toolName",
                type = ContextType.TOOL_CONTEXT,
                content = "Tool: $toolName\nDescription: $toolDescription\nSchema: $toolSchema",
                priority = ContextPriority.HIGH,
                metadata = mapOf("tool_name" to toolName)
            )
        )
    }

    /**
     * 更新全局状态
     */
    fun updateGlobalState(state: String, metadata: Map<String, Any> = emptyMap()) {
        // 移除旧的全局状态
        contextWindow.getAllContexts()
            .filter { it.type == ContextType.GLOBAL_STATE }
            .forEach { contextWindow.removeContext(it.id) }

        // 添加新的全局状态
        contextWindow.addContext(
            ContextContent(
                id = "global_state",
                type = ContextType.GLOBAL_STATE,
                content = state,
                priority = ContextPriority.MEDIUM,
                metadata = metadata
            )
        )
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
     * @param maxAge 最大保留时间（毫秒）
     */
    fun cleanupShortTermMemory(maxAge: Long = 3600000) { // 默认1小时
        val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val toRemove = contextWindow.getAllContexts()
            .filter {
                it.type == ContextType.SHORT_TERM_MEMORY &&
                        (currentTime - it.timestamp) > maxAge
            }
            .map { it.id }

        toRemove.forEach { contextWindow.removeContext(it) }
    }

    /**
     * 索引新内容到检索器
     */
    fun indexContent(content: String, metadata: Map<String, Any> = emptyMap()): String? {
        // TODO: 实现异步索引功能
        return null
    }

    /**
     * 从检索器中移除内容
     */
    fun removeIndexedContent(contentId: String): Boolean {
        // TODO: 实现异步移除功能
        return false
    }
}

/**
 * 上下文构建器 - 提供流式API
 */
class ContextBuilder(private val manager: ContextManager) {

    fun forQuery(query: String): ContextBuilder {
        this.query = query
        return this
    }

    fun withSystemPrompt(prompt: String): ContextBuilder {
        this.systemPrompt = prompt
        return this
    }

    fun withOutputFormat(format: String): ContextBuilder {
        this.outputFormat = format
        return this
    }

    fun withMaxRetrievalResults(max: Int): ContextBuilder {
        this.maxResults = max
        return this
    }

    fun build(): String {
        return manager.buildContextForQuery(
            query = query ?: "",
            systemPrompt = systemPrompt,
            outputFormat = outputFormat,
            maxRetrievalResults = maxResults
        )
    }

    private var query: String? = null
    private var systemPrompt: String? = null
    private var outputFormat: String? = null
    private var maxResults: Int = 3
}

/**
 * 扩展函数：为 ContextManager 提供构建器模式
 */
fun ContextManager.builder(): ContextBuilder = ContextBuilder(this)
