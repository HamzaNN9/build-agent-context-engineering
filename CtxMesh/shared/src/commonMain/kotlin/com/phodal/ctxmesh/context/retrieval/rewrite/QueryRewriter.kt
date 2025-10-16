package com.phodal.ctxmesh.context.retrieval.rewrite

/**
 * 查询改写器接口
 * 用于优化用户查询以提升检索效果
 */
interface QueryRewriter {
    /**
     * 改写查询以提升检索效果
     * @param originalQuery 原始查询
     * @return 改写后的查询列表
     */
    suspend fun rewrite(originalQuery: String): List<String>
}