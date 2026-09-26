package com.douluodalu.game.controller

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.sql.Connection
import javax.sql.DataSource

@RestController
class HealthController @Autowired constructor(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(HealthController::class.java)

    @GetMapping("/api/health")
    fun health(): Map<String, Any> {
        val dbStatus = try {
            dataSource.connection.use { conn: Connection ->
                if (conn.isValid(2)) "UP" else "DOWN"
            }
        } catch (e: Exception) {
            // 安全审计修复：/api/health 是匿名白名单端点，异常 message 可能含 DB 主机、
            // 账号乃至连接串片段，不得回显给调用方；详情只进服务端日志。
            log.warn("健康检查数据库探测失败: {}", e.message)
            "DOWN"
        }

        return mapOf(
            "status" to if (dbStatus == "UP") "ok" else "degraded",
            "game" to "斗罗大陆·放置传说",
            "checks" to mapOf(
                "database" to dbStatus
            )
        )
    }
}
