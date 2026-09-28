package com.douluodalu.game.exception

/**
 * 玩家存档缺失类业务异常（「玩家存档不存在」），由 GlobalExceptionHandler 单独映射为
 * 404 {error:"NOT_FOUND", message}。
 *
 * 引入动机：此前该语义靠 `IllegalStateException.message.contains("存档")` 的中文子串
 * 判定，漏配关键词的消息会从 404 掉进 500。类型即语义——抛出点显式声明 404 意图。
 *
 * 继承 IllegalStateException：拆分前所有按 IllegalStateException 捕获/分类的调用方行为不变，
 * 仅 GlobalExceptionHandler 的映射从「字符串猜测」升级为「类型匹配」（响应体逐字段一致）。
 */
class PlayerSaveNotFoundException(message: String = "玩家存档不存在") : IllegalStateException(message)
