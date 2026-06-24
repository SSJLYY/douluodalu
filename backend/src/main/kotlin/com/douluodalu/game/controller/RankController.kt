package com.douluodalu.game.controller

import com.douluodalu.game.dto.RankEntryResponse
import com.douluodalu.game.repository.PlayerProfileRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/rank")
class RankController(
    private val playerProfileRepo: PlayerProfileRepository
) {
    @GetMapping("/level")
    fun getLevelRank(
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<List<RankEntryResponse>> {
        val effectiveLimit = limit.coerceIn(1, 1000)
        val profiles = playerProfileRepo.findWithUserByLevelGreaterThanEqualOrderByLevelDesc(1).take(effectiveLimit)
        val result = profiles.mapIndexed { idx, p ->
            RankEntryResponse(
                idx + 1,
                p.userId,
                p.user.nickname,
                p.level.toLong(),
                "转生${p.prestigeCount}次"
            )
        }
        return ResponseEntity.ok(result)
    }

    @GetMapping("/tower")
    fun getTowerRank(
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<List<RankEntryResponse>> {
        val effectiveLimit = limit.coerceIn(1, 1000)
        val profiles = playerProfileRepo.findWithUserByTowerFloorGreaterThanEqualOrderByTowerFloorDesc(1).take(effectiveLimit)
        val result = profiles.mapIndexed { idx, p ->
            RankEntryResponse(idx + 1, p.userId, p.user.nickname, p.towerFloor.toLong(), null)
        }
        return ResponseEntity.ok(result)
    }
}
