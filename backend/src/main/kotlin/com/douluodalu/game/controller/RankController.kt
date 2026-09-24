package com.douluodalu.game.controller

import com.douluodalu.game.dto.RankEntryResponse
import com.douluodalu.game.service.RankService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/rank")
class RankController(
    private val rankService: RankService
) {
    /** limit 在这里归一到 1..1000，保证进入缓存键的空间有上界 */
    @GetMapping("/level")
    fun getLevelRank(
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<List<RankEntryResponse>> {
        return ResponseEntity.ok(rankService.getLevelRank(limit.coerceIn(1, 1000)))
    }

    @GetMapping("/tower")
    fun getTowerRank(
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<List<RankEntryResponse>> {
        return ResponseEntity.ok(rankService.getTowerRank(limit.coerceIn(1, 1000)))
    }
}
