package com.douluodalu.game.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.sql.Connection
import javax.sql.DataSource

@RestController
class HealthController @Autowired constructor(private val dataSource: DataSource) {

    @GetMapping("/api/health")
    fun health(): Map<String, Any> {
        val dbStatus = try {
            dataSource.connection.use { conn: Connection ->
                if (conn.isValid(2)) "UP" else "DOWN"
            }
        } catch (e: Exception) {
            "DOWN: ${e.message}"
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
