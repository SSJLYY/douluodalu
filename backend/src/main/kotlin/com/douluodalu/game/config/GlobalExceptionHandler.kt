package com.douluodalu.game.config

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    data class ErrorResponse(
        val error: String,
        val message: String,
        val traceId: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArg(e: IllegalArgumentException) = badRequest("BAD_REQUEST", e.message ?: "请求参数错误")

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuth(e: AuthenticationException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse("UNAUTHORIZED", e.message ?: "认证失败"))

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse("FORBIDDEN", "无权访问该资源"))

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(e: OptimisticLockingFailureException) =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("CONFLICT", "数据已被其他请求修改，请重试"))

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(e: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        val rootMsg = e.cause?.message ?: e.message ?: ""
        val isUnique = rootMsg.contains("duplicate", ignoreCase = true) ||
                rootMsg.contains("unique", ignoreCase = true) ||
                rootMsg.contains("UniqueConstraint", ignoreCase = true)
        return if (isUnique) {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse("CONFLICT", "该资源已存在"))
        } else {
            log.error("DataIntegrity violation", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse("DATA_INTEGRITY", "数据完整性错误"))
        }
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> {
        val isBusinessError = e.message?.contains("不存在") == true || e.message?.contains("存档") == true
        return if (isBusinessError) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse("NOT_FOUND", e.message ?: "资源不存在"))
        } else {
            log.error("IllegalState", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse("SERVER_ERROR", e.message ?: "服务器内部错误"))
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val msg = e.bindingResult.fieldErrors.joinToString("; ") {
            "${it.field}: ${it.defaultMessage ?: "验证失败"}"
        }
        return badRequest("VALIDATION_ERROR", msg)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(e: HttpMessageNotReadableException) =
        badRequest("MALFORMED_JSON", "请求体格式错误或为空")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(e: MissingServletRequestParameterException) =
        badRequest("MISSING_PARAM", "缺少必要参数：${e.parameterName}")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException) =
        badRequest("TYPE_MISMATCH", "参数类型错误：${e.name}")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException) =
        ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ErrorResponse("METHOD_NOT_ALLOWED", "不支持的请求方法：${e.method}"))

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNotFound(e: NoHandlerFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("NOT_FOUND", "接口不存在：${e.requestURL}"))

    /**
     * Spring Boot 3.2 默认静态资源映射（classpath:/static 等）注册了通配兜底 handler：
     * 打错 URL 的 API 请求不会走 NoHandlerFoundException，而是被 ResourceHttpRequestHandler
     * 抛 NoResourceFoundException。它此前只被下方 Exception 兜底捕获 → 500 UNKNOWN_ERROR，
     * 语义错误（应为 404）。响应体沿用 ErrorResponse 白名单字段，不含堆栈/DB 细节。
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("NOT_FOUND", "接口不存在：/${e.resourcePath}"))

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("UNKNOWN_ERROR", "服务器内部错误"))
    }

    private fun badRequest(code: String, msg: String) =
        ResponseEntity.badRequest().body(ErrorResponse(code, msg))
}
