package com.douluodalu.game.config

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    data class ErrorResponse(val error: String, val message: String)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArg(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(ErrorResponse("BAD_REQUEST", e.message ?: "请求参数错误"))
    }

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(e: OptimisticLockingFailureException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("CONFLICT", "数据已被其他请求修改，请重试"))
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(e: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        val rootMsg = if (e.cause != null) e.cause?.message else e.message
        val isUnique = rootMsg?.contains("duplicate", ignoreCase = true) == true ||
                rootMsg?.contains("unique", ignoreCase = true) == true ||
                rootMsg?.contains("UniqueConstraint", ignoreCase = true) == true
        return if (isUnique) {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse("CONFLICT", "该资源已存在"))
        } else {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse("DATA_INTEGRITY", rootMsg ?: "数据完整性错误"))
        }
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> {
        // "玩家存档不存在" 这类是业务逻辑错误，返回 404
        return if (e.message?.contains("不存在") == true || e.message?.contains("存档") == true) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse("NOT_FOUND", e.message ?: "资源不存在"))
        } else {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse("SERVER_ERROR", e.message ?: "服务器内部错误"))
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val msg = e.bindingResult.fieldErrors.joinToString("; ") {
            "${it.field}: ${it.defaultMessage ?: it.message ?: "验证失败"}"
        }
        return ResponseEntity.badRequest().body(ErrorResponse("VALIDATION_ERROR", msg))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("UNKNOWN_ERROR", e.message ?: "未知错误"))
    }
}
