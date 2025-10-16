package com.phodal.ctxmesh.context

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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

/**
 * 上下文类型枚举
 * 基于 README.md 中提到的上下文分类
 */
enum class ContextType {
    /** 系统提示词部分 */
    SYSTEM_PROMPT,
    
    /** 格式化输出上下文 */
    OUTPUT_FORMAT,
    
    /** 工具相关上下文 */
    TOOL_CONTEXT,
    
    /** 外部知识上下文（RAG检索结果） */
    EXTERNAL_KNOWLEDGE,
    
    /** 短期记忆 */
    SHORT_TERM_MEMORY,
    
    /** 长期记忆 */
    LONG_TERM_MEMORY,
    
    /** 全局状态/暂存区 */
    GLOBAL_STATE,
    
    /** 用户输入 */
    USER_INPUT
}

/**
 * 上下文优先级
 * 基于 GitHub Copilot 的优先级设计理念
 */
enum class ContextPriority(val weight: Int) {
    /** 最高优先级：光标位置周围的代码等 */
    HIGHEST(100),
    
    /** 高优先级：当前正在编辑的文件 */
    HIGH(80),
    
    /** 中等优先级：打开的其他文件 */
    MEDIUM(60),
    
    /** 低优先级：辅助上下文 */
    LOW(40),
    
    /** 最低优先级：可选的背景信息 */
    LOWEST(20)
}

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
