package com.phodal.ctxmesh.context.document

/**
 * 索引文档数据类
 */
data class IndexedDocument(
    val id: String,
    val content: String,
    val metadata: Map<String, Any>,
    val terms: Set<String>
)