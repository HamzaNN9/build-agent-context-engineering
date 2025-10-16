package com.phodal.ctxmesh.context

import com.phodal.ctxmesh.context.retrieval.*

/**
 * 上下文工厂类
 * 提供预配置的上下文管理器实例
 */
object ContextFactory {
    
    /**
     * 创建基础的上下文管理器（无检索功能）
     */
    fun createBasic(tokenBudget: Int = 8192): ContextManager {
        return ContextManager(
            contextWindow = DefaultContextWindow(tokenBudget)
        )
    }
    
    /**
     * 创建带关键词检索的上下文管理器
     */
    fun createWithKeywordRetrieval(tokenBudget: Int = 8192): ContextManager {
        return ContextManager(
            contextWindow = DefaultContextWindow(tokenBudget),
            retriever = KeywordRetriever()
        )
    }
    
    /**
     * 创建带混合检索的上下文管理器
     */
    fun createWithHybridRetrieval(
        tokenBudget: Int = 8192,
        retrievers: List<ContextRetriever>? = null,
        weights: List<Double>? = null
    ): ContextManager {
        val defaultRetrievers = retrievers ?: listOf(
            KeywordRetriever()
            // 可以在这里添加更多检索器
        )
        
        val hybridRetriever = if (defaultRetrievers.size > 1) {
            HybridRetriever(defaultRetrievers, weights ?: List(defaultRetrievers.size) { 1.0 / defaultRetrievers.size })
        } else {
            defaultRetrievers.first()
        }
        
        return ContextManager(
            contextWindow = DefaultContextWindow(tokenBudget),
            retriever = hybridRetriever
        )
    }
    
    /**
     * 创建用于代码场景的上下文管理器
     */
    fun createForCoding(tokenBudget: Int = 16384): ContextManager {
        val manager = createWithKeywordRetrieval(tokenBudget)
        
        // 预设一些代码相关的系统提示词模板
        manager.addLongTermMemory(
            """
            You are an expert software developer. When analyzing code:
            1. Focus on code structure, patterns, and best practices
            2. Consider performance, maintainability, and readability
            3. Provide specific, actionable suggestions
            4. Reference relevant documentation when available
            """.trimIndent(),
            mapOf("type" to "coding_guidelines")
        )
        
        return manager
    }
    
    /**
     * 创建用于文档问答的上下文管理器
     */
    fun createForDocumentQA(tokenBudget: Int = 12288): ContextManager {
        val manager = createWithKeywordRetrieval(tokenBudget)
        
        // 预设文档问答的系统提示词
        manager.addLongTermMemory(
            """
            You are a helpful assistant that answers questions based on provided documentation.
            Always:
            1. Base your answers on the provided context
            2. Cite specific sections when possible
            3. Acknowledge when information is not available in the context
            4. Provide clear, structured responses
            """.trimIndent(),
            mapOf("type" to "qa_guidelines")
        )
        
        return manager
    }
}

/**
 * 上下文配置类
 */
data class ContextConfig(
    val tokenBudget: Int = 8192,
    val enableRetrieval: Boolean = true,
    val retrievalStrategy: RetrievalStrategy = RetrievalStrategy.KEYWORD,
    val maxRetrievalResults: Int = 3,
    val enableQueryRewriting: Boolean = true,
    val shortTermMemoryMaxAge: Long = 3600000, // 1小时
    val autoCleanup: Boolean = true
)

/**
 * 高级上下文管理器构建器
 */
class ContextManagerBuilder {
    private var config = ContextConfig()
    private var customRetrievers = mutableListOf<ContextRetriever>()
    private var customQueryRewriter: QueryRewriter? = null
    
    fun withConfig(config: ContextConfig): ContextManagerBuilder {
        this.config = config
        return this
    }
    
    fun withTokenBudget(budget: Int): ContextManagerBuilder {
        this.config = config.copy(tokenBudget = budget)
        return this
    }
    
    fun withRetrievalStrategy(strategy: RetrievalStrategy): ContextManagerBuilder {
        this.config = config.copy(retrievalStrategy = strategy)
        return this
    }
    
    fun addCustomRetriever(retriever: ContextRetriever): ContextManagerBuilder {
        customRetrievers.add(retriever)
        return this
    }
    
    fun withQueryRewriter(rewriter: QueryRewriter): ContextManagerBuilder {
        this.customQueryRewriter = rewriter
        return this
    }
    
    fun build(): ContextManager {
        val contextWindow = DefaultContextWindow(config.tokenBudget)
        
        val retriever = if (config.enableRetrieval) {
            when {
                customRetrievers.isNotEmpty() -> {
                    if (customRetrievers.size == 1) {
                        customRetrievers.first()
                    } else {
                        HybridRetriever(customRetrievers)
                    }
                }
                config.retrievalStrategy == RetrievalStrategy.KEYWORD -> KeywordRetriever()
                config.retrievalStrategy == RetrievalStrategy.HYBRID -> {
                    HybridRetriever(listOf(KeywordRetriever()))
                }
                else -> KeywordRetriever() // 默认使用关键词检索
            }
        } else {
            null
        }
        
        val queryRewriter = if (config.enableQueryRewriting) {
            customQueryRewriter ?: SimpleQueryRewriter()
        } else {
            SimpleQueryRewriter()
        }
        
        return ContextManager(contextWindow, retriever, queryRewriter)
    }
}

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
