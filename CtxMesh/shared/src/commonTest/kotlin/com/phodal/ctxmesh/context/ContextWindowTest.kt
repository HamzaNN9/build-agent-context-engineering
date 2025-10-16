package com.phodal.ctxmesh.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ContextWindowTest {
    
    @Test
    fun testBasicContextWindow() {
        val window = DefaultContextWindow(tokenBudget = 1000)
        
        val content = ContextContent(
            id = "test1",
            type = ContextType.SYSTEM_PROMPT,
            content = "You are a helpful assistant.",
            priority = ContextPriority.HIGHEST
        )
        
        assertTrue(window.addContext(content))
        assertEquals(1, window.getAllContexts().size)
        assertTrue(window.usedTokens > 0)
    }
    
    @Test
    fun testTokenBudgetEnforcement() {
        val window = DefaultContextWindow(tokenBudget = 100) // 很小的预算
        
        val largeContent = ContextContent(
            id = "large",
            type = ContextType.EXTERNAL_KNOWLEDGE,
            content = "A".repeat(500), // 大约125个token
            priority = ContextPriority.LOW
        )
        
        // 应该因为超出预算而失败
        assertFalse(window.addContext(largeContent))
    }
    
    @Test
    fun testPriorityOrdering() {
        val window = DefaultContextWindow(tokenBudget = 2000)
        
        val lowPriority = ContextContent(
            id = "low",
            type = ContextType.EXTERNAL_KNOWLEDGE,
            content = "Low priority content",
            priority = ContextPriority.LOW
        )
        
        val highPriority = ContextContent(
            id = "high",
            type = ContextType.SYSTEM_PROMPT,
            content = "High priority content",
            priority = ContextPriority.HIGHEST
        )
        
        window.addContext(lowPriority)
        window.addContext(highPriority)
        
        val assembled = window.assembleContext()
        val highIndex = assembled.indexOf("High priority content")
        val lowIndex = assembled.indexOf("Low priority content")
        
        assertTrue(highIndex < lowIndex, "高优先级内容应该出现在前面")
    }
    
    @Test
    fun testAutoTruncation() {
        val window = DefaultContextWindow(tokenBudget = 200)
        
        // 添加一些低优先级内容
        repeat(3) { i ->
            window.addContext(
                ContextContent(
                    id = "low_$i",
                    type = ContextType.EXTERNAL_KNOWLEDGE,
                    content = "Low priority content $i",
                    priority = ContextPriority.LOW
                )
            )
        }
        
        // 添加高优先级内容，应该触发自动截断
        val highPriorityContent = ContextContent(
            id = "high",
            type = ContextType.SYSTEM_PROMPT,
            content = "A".repeat(150), // 大约37个token
            priority = ContextPriority.HIGHEST
        )
        
        assertTrue(window.addContext(highPriorityContent))
        
        // 验证高优先级内容存在
        val contexts = window.getAllContexts()
        assertTrue(contexts.any { it.id == "high" })
    }
    
    @Test
    fun testContextRemoval() {
        val window = DefaultContextWindow()
        
        val content = ContextContent(
            id = "removable",
            type = ContextType.USER_INPUT,
            content = "This will be removed",
            priority = ContextPriority.MEDIUM
        )
        
        window.addContext(content)
        assertEquals(1, window.getAllContexts().size)
        
        assertTrue(window.removeContext("removable"))
        assertEquals(0, window.getAllContexts().size)
        
        assertFalse(window.removeContext("nonexistent"))
    }
    
    @Test
    fun testContextFormatting() {
        val window = DefaultContextWindow()
        
        val systemPrompt = ContextContent(
            id = "system",
            type = ContextType.SYSTEM_PROMPT,
            content = "You are helpful",
            priority = ContextPriority.HIGHEST
        )
        
        val userInput = ContextContent(
            id = "user",
            type = ContextType.USER_INPUT,
            content = "Hello world",
            priority = ContextPriority.HIGH
        )
        
        window.addContext(systemPrompt)
        window.addContext(userInput)
        
        val assembled = window.assembleContext()
        
        assertTrue(assembled.contains("# System Prompt"))
        assertTrue(assembled.contains("# User Request"))
        assertTrue(assembled.contains("You are helpful"))
        assertTrue(assembled.contains("Hello world"))
    }
}
