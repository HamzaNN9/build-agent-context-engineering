package com.phodal.ctxmesh.context

/**
 * 上下文窗口的核心接口
 * 基于 README.md 中的上下文工程理念设计
 */
interface ContextWindow {
    /**
     * 获取当前上下文窗口的总 token 预算
     */
    val tokenBudget: Int

    /**
     * 获取当前已使用的 token 数量
     */
    val usedTokens: Int

    /**
     * 获取剩余可用的 token 数量
     */
    val remainingTokens: Int get() = tokenBudget - usedTokens

    /**
     * 添加上下文内容到窗口
     * @param content 要添加的上下文内容
     * @return 是否成功添加（可能因为 token 预算不足而失败）
     */
    fun addContext(content: ContextContent): Boolean

    /**
     * 移除指定的上下文内容
     * @param contentId 内容ID
     */
    fun removeContext(contentId: String): Boolean

    /**
     * 根据优先级和 token 预算组装最终的上下文
     * @return 组装后的上下文字符串
     */
    fun assembleContext(): String

    /**
     * 清空所有上下文内容
     */
    fun clear()

    /**
     * 获取所有上下文内容
     */
    fun getAllContexts(): List<ContextContent>
}

/**
 * 上下文内容的抽象表示
 */
data class ContextContent(
    val id: String,
    val type: ContextType,
    val content: String,
    val priority: ContextPriority,
    val metadata: Map<String, Any> = emptyMap(),
    val timestamp: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
) {
    /**
     * 估算内容的 token 数量（简化实现）
     */
    val estimatedTokens: Int get() = content.length / 4 // 粗略估算：4个字符约等于1个token
}

