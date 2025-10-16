package com.phodal.ctxmesh.context

/**
 * 上下文窗口状态信息
 */
data class ContextWindowStatus(
    val totalTokenBudget: Int,
    val usedTokens: Int,
    val remainingTokens: Int,
    val contextCount: Int,
    val contextsByType: Map<ContextType, List<ContextContent>>
) {
    val utilizationRate: Double get() = usedTokens.toDouble() / totalTokenBudget
    val isNearCapacity: Boolean get() = utilizationRate > 0.8
}