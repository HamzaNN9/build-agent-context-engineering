package com.phodal.ctxmesh.context

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