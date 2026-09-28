package com.douluodalu.game.controller

import org.springframework.security.core.Authentication

/**
 * 当前登录用户 ID 的统一取法（JwtAuthFilter 把 JWT 的 Long userId 放进 principal）。
 *
 * 用 Kotlin 扩展函数收敛 6 个控制器里 40+ 处 `auth.principal as Long` 样板：
 *  - 语义与旧代码逐字等价（同一个强转，匿名/伪造 principal 进入控制器时的异常行为不变）；
 *  - 不改控制器方法签名，因此 standalone MockMvc（GameControllerSettingsTest，未注册
 *    Spring Security 的 @AuthenticationPrincipal 解析器）与直接调用控制器的单测零改动；
 *  - 受保护路径由 SecurityConfig.anyRequest().authenticated() 先行拦截（匿名 401/403，
 *    见 SecurityConfigPathCoverageTest），落到本函数时 principal 必为 Long。
 */
fun Authentication.userId(): Long = principal as Long
