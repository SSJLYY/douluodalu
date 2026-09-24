package com.douluodalu.game.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * 给每个请求生成 traceId（透传 X-Trace-Id 或新建），
 * 写入 MDC 与响应头，便于排查问题。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter : OncePerRequestFilter() {
    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val HEADER = "X-Trace-Id"
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val incoming = request.getHeader(HEADER)
        val traceId = if (!incoming.isNullOrBlank()) incoming else UUID.randomUUID().toString().replace("-", "")
        MDC.put(TRACE_ID_KEY, traceId)
        response.setHeader(HEADER, traceId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(TRACE_ID_KEY)
        }
    }
}
