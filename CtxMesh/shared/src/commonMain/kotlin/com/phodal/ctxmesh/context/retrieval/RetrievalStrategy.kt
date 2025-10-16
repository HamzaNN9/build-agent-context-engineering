package com.phodal.ctxmesh.context.retrieval

/**
 * 检索策略枚举
 * 基于 README.md 中提到的多种检索策略
 */
enum class RetrievalStrategy {
    /** 关键词检索 */
    KEYWORD,

    /** 语义化检索 */
    SEMANTIC,

    /** 混合检索 */
    HYBRID,

    /** 图检索 */
    GRAPH
}