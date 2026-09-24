-- ============================================================
-- V3: 审计日志表
-- ============================================================

CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '操作用户ID',
    action VARCHAR(64) NOT NULL COMMENT '操作类型',
    target VARCHAR(128) DEFAULT NULL COMMENT '操作目标',
    detail TEXT DEFAULT NULL COMMENT '详细信息',
    ip VARCHAR(64) DEFAULT NULL COMMENT '来源IP',
    trace_id VARCHAR(64) DEFAULT NULL COMMENT '请求traceId',
    result VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_user_time (user_id, created_at),
    INDEX idx_action (action),
    INDEX idx_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志';
