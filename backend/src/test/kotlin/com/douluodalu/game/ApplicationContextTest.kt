package com.douluodalu.game

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * 上下文加载冒烟测试：纯 Mockito 单测无法暴露 Bean 装配/Security 过滤器链
 * 配置错误（如 requestMatchers 顺序），这类错误只在真实启动时炸出。
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Test
    fun `Spring 上下文可完整启动`() {
        // 空断言：能进入此方法即代表全部 Bean 初始化成功
    }
}
