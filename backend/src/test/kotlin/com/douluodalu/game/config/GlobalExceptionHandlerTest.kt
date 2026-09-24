package com.douluodalu.game.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * GlobalExceptionHandler 语义治理回归（任务#26）：
 * 未映射路径必须 404（此前 Spring Boot 3.2 的通配静态兜底抛 NoResourceFoundException，
 * 被 Exception 兜底捕成 500 UNKNOWN_ERROR——实测 POST /api/shop/buy 打错 URL 复现），
 * 方法不匹配 405，且路由类错误响应体不得泄漏堆栈/DB 细节。
 */
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `unmapped api path should return 404 not 500 UNKNOWN_ERROR`() {
        // Boot 3.2 下打错 URL 实际抛的是 NoResourceFoundException（resourcePath 无前导斜杠）
        val e = NoResourceFoundException(HttpMethod.POST, "api/shop/buy")

        val resp = handler.handleNoResource(e)

        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
        assertEquals("NOT_FOUND", resp.body?.error)
        assertTrue(resp.body?.message?.contains("api/shop/buy") == true)
    }

    @Test
    fun `NoHandlerFoundException should also map to 404 without stack or db details`() {
        val e = NoHandlerFoundException("POST", "/api/guild/kickX", HttpHeaders.EMPTY)

        val resp = handler.handleNotFound(e)

        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
        assertEquals("NOT_FOUND", resp.body?.error)
        val body = resp.body.toString()
        assertFalse(body.contains("Exception"))
        assertFalse(body.contains("at "))
    }

    @Test
    fun `route error bodies must not leak sql or stack information`() {
        val leaky = RuntimeException("jdbc.sqlsyntax: Table 'douluo_game.guild' doesn't exist\n\tat com.zaxxer.hikari.Pool.above(HikariPool.java:999)")

        val resp = handler.handleGeneral(leaky)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
        assertEquals("UNKNOWN_ERROR", resp.body?.error)
        // 兜底只回固定文案，异常 message（含表名/堆栈）不得进入响应体
        val body = resp.body.toString()
        assertFalse(body.contains("douluo_game"))
        assertFalse(body.contains("HikariPool"))
    }

    @Test
    fun `non-unique data integrity error should not echo db root message`() {
        val e = DataIntegrityViolationException("could not execute statement [Column 'name' cannot be null]")

        val resp = handler.handleDataIntegrity(e)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
        assertFalse(resp.body.toString().contains("cannot be null"))
    }

    @Test
    fun `wrong http method on mapped path should return 405 not 500`() {
        val e = HttpRequestMethodNotSupportedException("GET")

        val resp = handler.handleMethodNotSupported(e)

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.statusCode)
        assertEquals("METHOD_NOT_ALLOWED", resp.body?.error)
    }
}
