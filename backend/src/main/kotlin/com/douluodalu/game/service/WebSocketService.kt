package com.douluodalu.game.service

import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class WebSocketService(
    private val messagingTemplate: SimpMessagingTemplate
) {
    private val log = LoggerFactory.getLogger(WebSocketService::class.java)

    /**
     * 广播排行榜更新
     */
    fun broadcastRankUpdate(rankType: String, data: Any) {
        try {
            messagingTemplate.convertAndSend("/topic/rank/$rankType", data)
            log.debug("广播排行榜更新: type={}", rankType)
        } catch (e: Exception) {
            log.error("广播排行榜更新失败", e)
        }
    }

    /**
     * 向特定用户发送私信
     */
    fun sendToUser(userId: Long, destination: String, data: Any) {
        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), destination, data)
            log.debug("发送用户消息: userId={}, dest={}", userId, destination)
        } catch (e: Exception) {
            log.error("发送用户消息失败", e)
        }
    }

    /**
     * 广播系统公告
     */
    fun broadcastAnnouncement(message: String) {
        try {
            messagingTemplate.convertAndSend("/topic/announcement", mapOf(
                "type" to "ANNOUNCEMENT",
                "message" to message,
                "timestamp" to System.currentTimeMillis()
            ))
            log.debug("广播系统公告: {}", message)
        } catch (e: Exception) {
            log.error("广播系统公告失败", e)
        }
    }

    /**
     * 广播战斗结果（用于排行榜实时更新）
     */
    fun broadcastBattleResult(userId: Long, username: String, monsterName: String, won: Boolean) {
        try {
            messagingTemplate.convertAndSend("/topic/battle", mapOf(
                "userId" to userId,
                "username" to username,
                "monsterName" to monsterName,
                "won" to won,
                "timestamp" to System.currentTimeMillis()
            ))
            log.debug("广播战斗结果: user={}, monster={}, won={}", username, monsterName, won)
        } catch (e: Exception) {
            log.error("广播战斗结果失败", e)
        }
    }
}
