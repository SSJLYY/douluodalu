package com.douluodalu.game.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.sql.Connection
import javax.sql.DataSource

/**
 * 安全审计回归：/api/health 是匿名白名单端点，DB 探测失败时的异常 message
 * （可能含 DB 主机/账号/连接串片段）不得回显给调用方，只允许进服务端日志。
 */
class HealthControllerTest {

    @Test
    fun `db probe failure must not leak exception message on public endpoint`() {
        val dataSource = Mockito.mock(DataSource::class.java)
        val leaky = RuntimeException("jdbc:mysql://10.9.8.7:3306/douluo_game?user=root Access denied for user")
        Mockito.`when`(dataSource.connection).thenThrow(leaky)

        val body = HealthController(dataSource).health()

        assertEquals("degraded", body["status"])
        @Suppress("UNCHECKED_CAST")
        val checks = body["checks"] as Map<String, String>
        assertEquals("DOWN", checks["database"])
        // 响应体不得携带任何异常细节
        val rendered = body.toString()
        assertFalse(rendered.contains("jdbc"))
        assertFalse(rendered.contains("10.9.8.7"))
        assertFalse(rendered.contains("Access denied"))
    }

    @Test
    fun `invalid db connection reports degraded with generic DOWN`() {
        val conn = Mockito.mock(Connection::class.java)
        Mockito.`when`(conn.isValid(2)).thenReturn(false)
        val dataSource = Mockito.mock(DataSource::class.java)
        Mockito.`when`(dataSource.connection).thenReturn(conn)

        val body = HealthController(dataSource).health()

        assertEquals("degraded", body["status"])
        @Suppress("UNCHECKED_CAST")
        val checks = body["checks"] as Map<String, String>
        assertEquals("DOWN", checks["database"])
    }

    @Test
    fun `healthy db reports ok`() {
        val conn = Mockito.mock(Connection::class.java)
        Mockito.`when`(conn.isValid(2)).thenReturn(true)
        val dataSource = Mockito.mock(DataSource::class.java)
        Mockito.`when`(dataSource.connection).thenReturn(conn)

        val body = HealthController(dataSource).health()

        assertEquals("ok", body["status"])
        @Suppress("UNCHECKED_CAST")
        val checks = body["checks"] as Map<String, String>
        assertEquals("UP", checks["database"])
    }
}
