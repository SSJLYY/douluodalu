package com.douluodalu.game.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @GetMapping("/api/health")
    fun health(): Map<String, String> = mapOf("status" to "ok", "game" to "斗罗大陆·放置传说")
}
