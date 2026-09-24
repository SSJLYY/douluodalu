package com.douluodalu.game.config

/**
 * 审计日志注解 - 标记需要记录审计日志的方法
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuditLog(
    /** 操作类型 */
    val action: String,
    /** 操作目标描述 */
    val target: String = "",
    /** 是否记录请求参数 */
    val logParams: Boolean = false,
    /** 是否记录返回结果 */
    val logResult: Boolean = false
)
