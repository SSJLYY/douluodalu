package com.douluodalu.game.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 调度总闸（第二十二轮放宽）：@EnableScheduling 无条件开启，不再绑定 audit.cleanup-enabled。
 *
 * 此前整个调度器挂在 audit.cleanup-enabled 开关下——宗门 Boss 周榜重置（GuildWeeklyResetService）
 * 加入后调度器成为多任务共享基础设施，总闸与单任务开关解耦；各 @Scheduled Bean 用自己类上的
 * @ConditionalOnProperty 独立开关：
 *  - AuditLogCleanupService → audit.cleanup-enabled（条件保留在它自己的类上，默认开 / test 关）
 *  - GuildWeeklyResetService → guild.weekly-reset-enabled（默认开 / test 关）
 * test profile 下两个任务 Bean 均不注册（application-test.yml 显式关闭），@Scheduled 不会触发，
 * 不会与测试断言抢跑造成抖动。
 */
@Configuration
@EnableScheduling
class SchedulingConfig
