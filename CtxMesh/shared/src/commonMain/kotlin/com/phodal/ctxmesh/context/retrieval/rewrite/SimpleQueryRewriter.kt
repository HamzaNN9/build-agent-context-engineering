package com.phodal.ctxmesh.context.retrieval.rewrite

/**
 * 简单的查询改写器实现
 */
class SimpleQueryRewriter : QueryRewriter {
    override suspend fun rewrite(originalQuery: String): List<String> {
        val rewrites = mutableListOf<String>()

        // 原始查询
        rewrites.add(originalQuery)

        // 添加同义词扩展（简化实现）
        val synonyms = mapOf(
            "function" to listOf("method", "procedure", "routine"),
            "class" to listOf("type", "object", "entity"),
            "variable" to listOf("field", "property", "attribute"),
            "error" to listOf("exception", "bug", "issue"),
            "implement" to listOf("create", "build", "develop")
        )

        synonyms.forEach { (word, syns) ->
            if (originalQuery.contains(word, ignoreCase = true)) {
                syns.forEach { syn ->
                    rewrites.add(originalQuery.replace(word, syn, ignoreCase = true))
                }
            }
        }

        if (!originalQuery.endsWith("?")) {
            rewrites.add("How to $originalQuery?")
            rewrites.add("What is $originalQuery?")
        }

        return rewrites.distinct()
    }
}