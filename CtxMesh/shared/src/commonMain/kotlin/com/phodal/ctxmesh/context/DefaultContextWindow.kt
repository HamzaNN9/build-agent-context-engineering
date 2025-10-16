package com.phodal.ctxmesh.context

/**
 * 默认的上下文窗口实现
 * 实现了基本的优先级管理和 token 预算控制
 */
class DefaultContextWindow(
    override val tokenBudget: Int = 8192
) : ContextWindow {

    private val contexts = mutableMapOf<String, ContextContent>()

    override val usedTokens: Int
        get() = contexts.values.sumOf { it.estimatedTokens }

    override fun addContext(content: ContextContent): Boolean {
        // 检查是否超出 token 预算
        val newTokenCount = usedTokens + content.estimatedTokens
        if (newTokenCount > tokenBudget) {
            // 尝试自动截断低优先级内容
            if (!autoTruncate(content.estimatedTokens)) {
                return false
            }
        }

        contexts[content.id] = content
        return true
    }

    override fun removeContext(contentId: String): Boolean {
        return contexts.remove(contentId) != null
    }

    override fun assembleContext(): String {
        // 按优先级和时间戳排序
        val sortedContexts = contexts.values.sortedWith(
            compareByDescending<ContextContent> { it.priority.weight }
                .thenByDescending { it.timestamp }
        )

        val result = StringBuilder()
        var currentTokens = 0

        for (context in sortedContexts) {
            if (currentTokens + context.estimatedTokens <= tokenBudget) {
                result.append(formatContextContent(context))
                result.append("\n\n")
                currentTokens += context.estimatedTokens
            }
        }

        return result.toString().trim()
    }

    override fun clear() {
        contexts.clear()
    }

    override fun getAllContexts(): List<ContextContent> {
        return contexts.values.toList()
    }

    /**
     * 自动截断低优先级内容以腾出空间
     */
    private fun autoTruncate(requiredTokens: Int): Boolean {
        val sortedByPriority = contexts.values.sortedBy { it.priority.weight }
        var freedTokens = 0
        val toRemove = mutableListOf<String>()

        for (context in sortedByPriority) {
            if (freedTokens >= requiredTokens) break

            toRemove.add(context.id)
            freedTokens += context.estimatedTokens
        }

        toRemove.forEach { contexts.remove(it) }
        return freedTokens >= requiredTokens
    }

    /**
     * 格式化上下文内容
     */
    private fun formatContextContent(context: ContextContent): String {
        return when (context.type) {
            ContextType.SYSTEM_PROMPT -> "# System Prompt\n${context.content}"
            ContextType.OUTPUT_FORMAT -> "# Output Format\n${context.content}"
            ContextType.TOOL_CONTEXT -> "# Available Tools\n${context.content}"
            ContextType.EXTERNAL_KNOWLEDGE -> "# Knowledge Base\n${context.content}"
            ContextType.SHORT_TERM_MEMORY -> "# Recent Context\n${context.content}"
            ContextType.LONG_TERM_MEMORY -> "# Background Knowledge\n${context.content}"
            ContextType.GLOBAL_STATE -> "# Current State\n${context.content}"
            ContextType.USER_INPUT -> "# User Request\n${context.content}"
        }
    }
}