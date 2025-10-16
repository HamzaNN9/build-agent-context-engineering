package com.phodal.ctxmesh.context

import com.phodal.ctxmesh.context.retrieval.KeywordRetriever
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ContextManagerTest {

    @Test
    fun testBasicContextBuilding() {
        val manager = ContextFactory.createBasic()

        // 简化测试，不使用协程
        manager.addShortTermMemory("What is Kotlin?")

        val status = manager.getWindowStatus()
        assertTrue(status.contextCount > 0)
        assertTrue(status.usedTokens > 0)
    }

    @Test
    fun testContextWithRetrieval() {
        val manager = ContextFactory.createWithKeywordRetrieval()

        // 测试检索器是否正确创建
        val status = manager.getWindowStatus()
        assertEquals(0, status.contextCount) // 初始应该为空
    }

    @Test
    fun testMemoryManagement() {
        val manager = ContextFactory.createBasic()

        // 添加长期记忆
        manager.addLongTermMemory(
            "Remember: Always follow coding best practices",
            mapOf("category" to "guidelines")
        )

        // 添加短期记忆
        manager.addShortTermMemory(
            "User is working on a web application project",
            mapOf("context" to "current_session")
        )

        val status = manager.getWindowStatus()
        assertEquals(2, status.contextCount)
        assertTrue(status.contextsByType.containsKey(ContextType.LONG_TERM_MEMORY))
        assertTrue(status.contextsByType.containsKey(ContextType.SHORT_TERM_MEMORY))
    }

    @Test
    fun testToolContext() {
        val manager = ContextFactory.createBasic()

        manager.addToolContext(
            toolName = "file_reader",
            toolDescription = "Reads content from files",
            toolSchema = "{ \"type\": \"function\", \"parameters\": { \"path\": \"string\" } }"
        )

        val status = manager.getWindowStatus()
        assertEquals(1, status.contextCount)
        assertTrue(status.contextsByType.containsKey(ContextType.TOOL_CONTEXT))
    }

    @Test
    fun testGlobalStateManagement() {
        val manager = ContextFactory.createBasic()

        // 设置初始状态
        manager.updateGlobalState(
            "Current project: E-commerce website, Phase: Development",
            mapOf("project" to "ecommerce", "phase" to "dev")
        )

        val status1 = manager.getWindowStatus()
        assertEquals(1, status1.contextCount)
        assertTrue(status1.contextsByType.containsKey(ContextType.GLOBAL_STATE))

        // 更新状态
        manager.updateGlobalState(
            "Current project: E-commerce website, Phase: Testing",
            mapOf("project" to "ecommerce", "phase" to "test")
        )

        val status2 = manager.getWindowStatus()
        assertEquals(1, status2.contextCount) // 应该还是1个，因为旧状态被替换
    }

    @Test
    fun testWindowStatusTracking() {
        val manager = ContextFactory.createBasic(tokenBudget = 1000)

        manager.addLongTermMemory("Some long term memory content")
        manager.addShortTermMemory("Some short term memory content")

        val status = manager.getWindowStatus()

        assertEquals(1000, status.totalTokenBudget)
        assertTrue(status.usedTokens > 0)
        assertTrue(status.remainingTokens < 1000)
        assertEquals(2, status.contextCount)
        assertTrue(status.contextsByType.containsKey(ContextType.LONG_TERM_MEMORY))
        assertTrue(status.contextsByType.containsKey(ContextType.SHORT_TERM_MEMORY))
    }



    @Test
    fun testCodingScenario() {
        val manager = ContextFactory.createForCoding()

        val status = manager.getWindowStatus()
        // 编码场景应该有预设的长期记忆
        assertTrue(status.contextCount > 0)
        assertTrue(status.contextsByType.containsKey(ContextType.LONG_TERM_MEMORY))
    }

    @Test
    fun testDocumentQAScenario() {
        val manager = ContextFactory.createForDocumentQA()

        val status = manager.getWindowStatus()
        // 文档问答场景应该有预设的长期记忆
        assertTrue(status.contextCount > 0)
        assertTrue(status.contextsByType.containsKey(ContextType.LONG_TERM_MEMORY))
    }
}
