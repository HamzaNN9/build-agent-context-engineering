package com.phodal.ctxmesh.context

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