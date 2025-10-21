package com.phodal.ctxmesh.context.retrieval

import com.phodal.ctxmesh.context.ContextContent

interface ContextRetriever {
    suspend fun retrieve(query: String, maxResults: Int = 5): List<ContextContent>
    suspend fun index(content: String, metadata: Map<String, Any> = emptyMap()): String
    suspend fun remove(contentId: String): Boolean
}

