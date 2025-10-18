package com.phodal.ctxmesh

/**
 * 带记忆系统的上下文构建器
 */
class ContextBuilderWithMemory(private val manager: ContextManagerWithMemory) {
    private var query: String = ""
    private var systemPrompt: String? = null
    private var outputFormat: String? = null
    private var maxRetrievalResults: Int = 3
    private var maxMemoryResults: Int = 5

    fun forQuery(query: String): ContextBuilderWithMemory {
        this.query = query
        return this
    }

    fun withSystemPrompt(prompt: String): ContextBuilderWithMemory {
        this.systemPrompt = prompt
        return this
    }

    fun withOutputFormat(format: String): ContextBuilderWithMemory {
        this.outputFormat = format
        return this
    }

    fun withMaxRetrievalResults(max: Int): ContextBuilderWithMemory {
        this.maxRetrievalResults = max
        return this
    }

    fun withMaxMemoryResults(max: Int): ContextBuilderWithMemory {
        this.maxMemoryResults = max
        return this
    }

    fun build(): String {
        return manager.buildContextForQuery(
            query = query,
            systemPrompt = systemPrompt,
            outputFormat = outputFormat,
            maxRetrievalResults = maxRetrievalResults,
            maxMemoryResults = maxMemoryResults
        )
    }
}