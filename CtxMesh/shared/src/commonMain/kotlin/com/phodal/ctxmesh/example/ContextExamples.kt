package com.phodal.ctxmesh.example

import com.phodal.ctxmesh.context.ContextFactory
import com.phodal.ctxmesh.context.ContextManager
import com.phodal.ctxmesh.context.ContextManagerBuilder
import com.phodal.ctxmesh.context.builder
import com.phodal.ctxmesh.context.retrieval.KeywordRetriever
import com.phodal.ctxmesh.context.retrieval.RetrievalStrategy
import com.phodal.ctxmesh.context.retrieval.rewrite.SimpleQueryRewriter

/**
 * 使用示例和工具函数
 */
object ContextExamples {

    /**
     * 基础使用示例
     */
    fun basicUsageExample(): String {
        val manager = ContextFactory.createBasic()

        return manager.builder()
            .forQuery("How to implement a binary search algorithm?")
            .withSystemPrompt("You are a helpful programming assistant.")
            .withOutputFormat("Provide code examples with explanations.")
            .build()
    }

    /**
     * 带检索的使用示例
     */
    fun retrievalUsageExample(): String {
        val manager = ContextFactory.createWithKeywordRetrieval()

        // 添加一些示例内容到记忆中
        manager.addLongTermMemory(
            "Binary search is an efficient algorithm for finding an item from a sorted list of items. It works by repeatedly dividing the search interval in half.",
            mapOf("topic" to "algorithms", "type" to "definition")
        )

        manager.addLongTermMemory(
            """
            fun binarySearch(arr: IntArray, target: Int): Int {
                var left = 0
                var right = arr.size - 1

                while (left <= right) {
                    val mid = left + (right - left) / 2
                    when {
                        arr[mid] == target -> return mid
                        arr[mid] < target -> left = mid + 1
                        else -> right = mid - 1
                    }
                }
                return -1
            }
            """.trimIndent(),
            mapOf("topic" to "algorithms", "type" to "implementation", "language" to "kotlin")
        )

        return manager.builder()
            .forQuery("Show me binary search implementation")
            .withSystemPrompt("You are a Kotlin programming expert.")
            .withMaxRetrievalResults(2)
            .build()
    }

    /**
     * 高级配置示例
     */
    fun advancedConfigExample(): ContextManager {
        return ContextManagerBuilder()
            .withTokenBudget(16384)
            .withRetrievalStrategy(RetrievalStrategy.HYBRID)
            .addCustomRetriever(KeywordRetriever())
            .withQueryRewriter(SimpleQueryRewriter())
            .build()
    }
}