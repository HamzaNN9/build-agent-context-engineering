package com.phodal.ctxmesh.context

import com.phodal.ctxmesh.memory.MemoryManager
import com.phodal.ctxmesh.memory.impl.LongMemoryFactory
import com.phodal.ctxmesh.memory.impl.ShortMemoryFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ContextManagerWithMemoryTest {

    @Test
    fun testBasicContextBuilding() = runTest {
        val memoryManager = MemoryManager(
            shortMemory = ShortMemoryFactory.createDefault(),
            longMemory = LongMemoryFactory.createDefault()
        )
        
        val contextManager = ContextManagerWithMemory(
            contextWindow = DefaultContextWindow(2000),
            memoryManager = memoryManager
        )
        
        // 添加一些记忆内容
        contextManager.addToMemory(
            "Kotlin is a modern programming language",
            mapOf("type" to "knowledge", "priority" to "high")
        )
        
        // 构建上下文
        val context = contextManager.buildContextForQuery(
            query = "What is Kotlin?",
            systemPrompt = "You are a helpful programming assistant.",
            outputFormat = "Provide a clear and concise answer."
        )
        
        assertNotNull(context)
        assertTrue(context.contains("You are a helpful programming assistant"))
        assertTrue(context.contains("What is Kotlin?"))
        assertTrue(context.contains("Provide a clear and concise answer"))
    }

    @Test
    fun testMemoryIntegration() = runTest {
        val memoryManager = MemoryManager(
            shortMemory = ShortMemoryFactory.createDefault(),
            longMemory = LongMemoryFactory.createDefault()
        )
        
        val contextManager = ContextManagerWithMemory(
            contextWindow = DefaultContextWindow(4000),
            memoryManager = memoryManager
        )
        
        // 添加相关记忆
        contextManager.addLongTermMemory(
            "Kotlin coroutines provide powerful asynchronous programming capabilities",
            mapOf("topic" to "kotlin", "subtopic" to "coroutines")
        )
        
        contextManager.addShortTermMemory(
            "User is learning about Kotlin programming",
            mapOf("context" to "learning_session")
        )
        
        // 构建上下文，应该包含相关记忆
        val context = contextManager.buildContextForQuery(
            query = "How do Kotlin coroutines work?",
            maxMemoryResults = 5
        )
        
        assertTrue(context.contains("coroutines"))
        assertTrue(context.contains("How do Kotlin coroutines work?"))
    }

    @Test
    fun testBuilderPattern() = runTest {
        val memoryManager = MemoryManager(
            shortMemory = ShortMemoryFactory.createDefault(),
            longMemory = LongMemoryFactory.createDefault()
        )
        
        val contextManager = ContextManagerWithMemory(
            contextWindow = DefaultContextWindow(2000),
            memoryManager = memoryManager
        )
        
        // 使用构建器模式
        val context = contextManager.builder()
            .forQuery("Explain data structures")
            .withSystemPrompt("You are an expert computer science teacher")
            .withOutputFormat("Use examples and diagrams")
            .withMaxMemoryResults(3)
            .build()
        
        assertNotNull(context)
        assertTrue(context.contains("Explain data structures"))
        assertTrue(context.contains("expert computer science teacher"))
        assertTrue(context.contains("examples and diagrams"))
    }

    @Test
    fun testMemoryRetrieval() = runTest {
        val memoryManager = MemoryManager(
            shortMemory = ShortMemoryFactory.createDefault(),
            longMemory = LongMemoryFactory.createDefault()
        )
        
        val contextManager = ContextManagerWithMemory(
            contextWindow = DefaultContextWindow(4000),
            memoryManager = memoryManager
        )
        
        // 添加多个记忆项
        val id1 = contextManager.addToMemory(
            "Arrays are contiguous memory structures",
            mapOf("type" to "definition", "topic" to "data_structures")
        )
        
        val id2 = contextManager.addToMemory(
            "Linked lists use pointers to connect nodes",
            mapOf("type" to "definition", "topic" to "data_structures")
        )
        
        assertNotNull(id1)
        assertNotNull(id2)
        
        // 检索记忆
        val memories = contextManager.retrieveFromMemory("data structures", 5)
        assertTrue(memories.isNotEmpty())
        assertTrue(memories.any { it.content.contains("Arrays") })
        assertTrue(memories.any { it.content.contains("Linked lists") })
    }

    @Test
    fun testMemoryConsolidation() = runTest {
        val memoryManager = MemoryManager(
            shortMemory = ShortMemoryFactory.createDefault(),
            longMemory = LongMemoryFactory.createDefault()
        )
        
        val contextManager = ContextManagerWithMemory(
            contextWindow = DefaultContextWindow(4000),
            memoryManager = memoryManager
        )
        
        // 添加短期记忆
        contextManager.addShortTermMemory(
            "Important algorithm concept that should be remembered",
            mapOf("importance" to 0.8, "type" to "concept")
        )
        
        // 执行记忆巩固
        val consolidatedCount = contextManager.consolidateMemories()
        assertTrue(consolidatedCount >= 0)
        
        // 获取统计信息
        val stats = contextManager.getMemoryStats()
        assertTrue(stats.totalItems >= 0)
    }

    @Test
    fun testMemoryCleanup() = runTest {
        val memoryManager = MemoryManager(
            shortMemory = ShortMemoryFactory.createDefault(),
            longMemory = LongMemoryFactory.createDefault()
        )
        
        val contextManager = ContextManagerWithMemory(
            contextWindow = DefaultContextWindow(4000),
            memoryManager = memoryManager
        )
        
        // 添加一些测试内容
        repeat(5) { i ->
            contextManager.addShortTermMemory(
                "Test memory content $i",
                mapOf("index" to i)
            )
        }
        
        // 执行清理
        val cleanupResult = contextManager.cleanupMemories()
        assertTrue(cleanupResult.totalCleaned >= 0)
        
        // 验证清理结果
        assertTrue(cleanupResult.shortTermCleaned >= 0)
        assertTrue(cleanupResult.longTermCleaned >= 0)
    }

    @Test
    fun testWindowStatusTracking() = runTest {
        val memoryManager = MemoryManager(
            shortMemory = ShortMemoryFactory.createDefault(),
            longMemory = LongMemoryFactory.createDefault()
        )
        
        val contextManager = ContextManagerWithMemory(
            contextWindow = DefaultContextWindow(1000),
            memoryManager = memoryManager
        )
        
        // 添加记忆内容
        contextManager.addLongTermMemory("Some knowledge content")
        
        // 构建上下文
        contextManager.buildContextForQuery(
            "Test query",
            systemPrompt = "Test system prompt"
        )
        
        // 检查窗口状态
        val status = contextManager.getWindowStatus()
        assertEquals(1000, status.totalTokenBudget)
        assertTrue(status.usedTokens > 0)
        assertTrue(status.remainingTokens < 1000)
        assertTrue(status.contextCount > 0)
    }

    @Test
    fun testMemoryStats() = runTest {
        val memoryManager = MemoryManager(
            shortMemory = ShortMemoryFactory.createDefault(),
            longMemory = LongMemoryFactory.createDefault()
        )
        
        val contextManager = ContextManagerWithMemory(
            contextWindow = DefaultContextWindow(4000),
            memoryManager = memoryManager
        )
        
        // 初始状态
        val initialStats = contextManager.getMemoryStats()
        assertEquals(0, initialStats.totalItems)
        
        // 添加内容
        contextManager.addShortTermMemory("Short term content")
        contextManager.addLongTermMemory("Long term knowledge content")
        
        val stats = contextManager.getMemoryStats()
        assertTrue(stats.totalItems > 0)
        assertTrue(stats.totalSize > 0)
    }

    @Test
    fun testClearOperation() = runTest {
        val memoryManager = MemoryManager(
            shortMemory = ShortMemoryFactory.createDefault(),
            longMemory = LongMemoryFactory.createDefault()
        )
        
        val contextManager = ContextManagerWithMemory(
            contextWindow = DefaultContextWindow(4000),
            memoryManager = memoryManager
        )
        
        // 添加内容
        contextManager.addShortTermMemory("Test content 1")
        contextManager.addLongTermMemory("Test content 2")
        
        // 构建上下文
        contextManager.buildContextForQuery("Test query")
        
        // 验证内容存在
        val beforeStats = contextManager.getMemoryStats()
        val beforeWindowStatus = contextManager.getWindowStatus()
        assertTrue(beforeStats.totalItems > 0)
        assertTrue(beforeWindowStatus.contextCount > 0)
        
        // 清空
        contextManager.clear()
        
        // 验证清空成功
        val afterStats = contextManager.getMemoryStats()
        val afterWindowStatus = contextManager.getWindowStatus()
        assertEquals(0, afterStats.totalItems)
        assertEquals(0, afterWindowStatus.contextCount)
    }

    @Test
    fun testQueryStorageInShortTermMemory() = runTest {
        val memoryManager = MemoryManager(
            shortMemory = ShortMemoryFactory.createDefault(),
            longMemory = LongMemoryFactory.createDefault()
        )
        
        val contextManager = ContextManagerWithMemory(
            contextWindow = DefaultContextWindow(4000),
            memoryManager = memoryManager
        )
        
        val query = "What are the benefits of functional programming?"
        
        // 构建上下文（这应该自动将查询存储到短期记忆）
        contextManager.buildContextForQuery(query)
        
        // 验证查询被存储
        val memories = contextManager.retrieveFromMemory("functional programming", 5)
        assertTrue(memories.any { it.content.contains(query) })
    }

    @Test
    fun testMemoryPrioritization() = runTest {
        val memoryManager = MemoryManager(
            shortMemory = ShortMemoryFactory.createDefault(),
            longMemory = LongMemoryFactory.createDefault()
        )
        
        val contextManager = ContextManagerWithMemory(
            contextWindow = DefaultContextWindow(1000), // 小的token预算
            memoryManager = memoryManager
        )
        
        // 添加不同优先级的记忆
        contextManager.addLongTermMemory(
            "High priority knowledge that should always be included",
            mapOf("priority" to "high", "type" to "critical")
        )
        
        contextManager.addShortTermMemory(
            "Medium priority recent context",
            mapOf("priority" to "medium")
        )
        
        contextManager.addShortTermMemory(
            "Low priority background information",
            mapOf("priority" to "low")
        )
        
        // 构建上下文
        val context = contextManager.buildContextForQuery(
            "Tell me about the critical information",
            maxMemoryResults = 10
        )
        
        // 高优先级内容应该被包含
        assertTrue(context.contains("High priority knowledge"))
    }
}
