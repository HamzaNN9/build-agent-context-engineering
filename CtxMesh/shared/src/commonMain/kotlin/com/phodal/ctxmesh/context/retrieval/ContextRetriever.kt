package com.phodal.ctxmesh.context.retrieval

import com.phodal.ctxmesh.context.ContextContent

interface ContextRetriever {
    /**
     * 根据查询检索相关上下文
     * @param query 查询内容
     * @param maxResults 最大返回结果数
     * @return 检索到的上下文内容列表
     */
    suspend fun retrieve(query: String, maxResults: Int = 5): List<ContextContent>
    
    /**
     * 添加内容到检索索引
     * @param content 要索引的内容
     */
    suspend fun index(content: String, metadata: Map<String, Any> = emptyMap()): String
    
    /**
     * 从索引中移除内容
     * @param contentId 内容ID
     */
    suspend fun remove(contentId: String): Boolean
}

