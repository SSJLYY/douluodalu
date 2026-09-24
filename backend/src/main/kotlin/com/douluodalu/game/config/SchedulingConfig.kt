package com.douluodalu.game.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 调度开关独立配置（当前唯一消费方是审计日志保留清理任务）。
 *
 * audit.cleanup-enabled 默认开（matchIfMissing = true）；
 * application-test.yml 显式置 false，保证测试 profile 下调度器完全不启动，
 * 防止定时任务与断言抢跑导致的测试抖动。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["audit.cleanup-enabled"], havingValue = "true", matchIfMissing = true)
class SchedulingConfig
