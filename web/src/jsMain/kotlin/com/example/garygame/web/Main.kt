package com.example.garygame.web

import androidx.compose.runtime.*
import com.example.garygame.engine.GameEngine
import com.example.garygame.model.*
import com.example.garygame.platform.WebStorage
import com.example.garygame.platform.PlatformTime
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable

private val scope = MainScope()
private var gameInitialized = false
private var currentUser: String? = null

fun main() {
    ApiClient.init()

    renderComposable(rootElementId = "root") {
        var isLoggedIn by remember { mutableStateOf(ApiClient.isLoggedIn()) }

        if (isLoggedIn && !gameInitialized) {
            initGame()
            gameInitialized = true
        }

        if (isLoggedIn) {
            GamePage(onLogout = {
                ApiClient.logout()
                gameInitialized = false
                currentUser = null
                isLoggedIn = false
            })
        } else {
            LoginPage(onLoginSuccess = { username ->
                currentUser = username
                isLoggedIn = true
            })
        }
    }
}

private fun initGame() {
    val storage = WebStorage("DouluoIdleGameV2")
    GameEngine.init(storage)

    val offlineReward = GameEngine.calculateOfflineReward()
    if (offlineReward > 0) {
        GameEngine.addLog("欢迎回来！离线修炼 +${GameEngine.formatNum(offlineReward)} 魂力")
    }
    GameEngine.state.lastExitTime = PlatformTime.currentTimeMillis()

    window.setInterval({
        try {
            GameEngine.tick()
        } catch (e: Exception) {
            GameEngine.addLog("⚠️ 自动循环异常: ${e.message}")
        }
    }, 500)
}

@Composable
fun LoginPage(onLoginSuccess: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Div({
        style {
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.Center)
            alignItems(AlignItems.Center)
            minHeight(100.vh)
            backgroundColor(Color("#1a1a2e"))
            fontFamily("Microsoft YaHei, sans-serif")
        }
    }) {
        Div({
            style {
                backgroundColor(Color("#16213e"))
                borderRadius(16.px)
                padding(40.px)
                width(360.px)
                property("box-shadow", "0 8px 32px rgba(0,0,0,0.3)")
            }
        }) {
            H1({
                style {
                    color(Color("#e94560"))
                    textAlign("center")
                    margin(0.px, 0.px, 8.px)
                    fontSize(24.px)
                }
            }) { Text("斗罗大陆·放置传说") }

            P({
                style {
                    color(Color("#aaa"))
                    textAlign("center")
                    marginBottom(24.px)
                    fontSize(14.px)
                }
            }) { Text(if (isRegister) "创建新账号" else "登录游戏") }

            // Username input
            Label({
                style { color(Color("#aaa")); fontSize(13.px); marginBottom(4.px); display(DisplayStyle.Block) }
            }) { Text("用户名") }
            Input(InputType.Text) {
                style {
                    width(100.percent)
                    padding(10.px, 12.px)
                    backgroundColor(Color("#0f3460"))
                    color(Color.white)
                    border("1px solid #333")
                    borderRadius(8.px)
                    fontSize(15.px)
                    marginBottom(12.px)
                    property("outline", "none")
                }
                value(username)
                onInput { username = it.value!! }
                attr("placeholder", "3-20个字符")
            }

            // Password input
            Label({
                style { color(Color("#aaa")); fontSize(13.px); marginBottom(4.px); display(DisplayStyle.Block) }
            }) { Text("密码") }
            Input(InputType.Password) {
                style {
                    width(100.percent)
                    padding(10.px, 12.px)
                    backgroundColor(Color("#0f3460"))
                    color(Color.white)
                    border("1px solid #333")
                    borderRadius(8.px)
                    fontSize(15.px)
                    marginBottom(16.px)
                    property("outline", "none")
                }
                value(password)
                onInput { password = it.value!! }
                attr("placeholder", "至少6个字符")
            }

            // Error message
            if (errorMsg.isNotEmpty()) {
                P({
                    style {
                        color(Color("#f44336"))
                        fontSize(13.px)
                        marginBottom(12.px)
                        textAlign("center")
                    }
                }) { Text(errorMsg) }
            }

            // Submit button
            Button(attrs = {
                style {
                    width(100.percent)
                    padding(12.px)
                    backgroundColor(Color("#e94560"))
                    color(Color.white)
                    border("none")
                    borderRadius(8.px)
                    fontSize(16.px)
                    cursor(if (isLoading) "wait" else "pointer")
                    marginBottom(12.px)
                    property("opacity", if (isLoading) "0.7" else "1")
                }
                onClick {
                    if (isLoading) return@onClick
                    if (username.length < 3) { errorMsg = "用户名至少3个字符"; return@onClick }
                    if (password.length < 6) { errorMsg = "密码至少6个字符"; return@onClick }

                    isLoading = true
                    errorMsg = ""
                    scope.launch {
                        val result = if (isRegister) {
                            ApiClient.register(username, password)
                        } else {
                            ApiClient.login(username, password)
                        }
                        result.fold(
                            onSuccess = { onLoginSuccess(it.username) },
                            onFailure = { errorMsg = it.message ?: "操作失败" }
                        )
                        isLoading = false
                    }
                }
            }) {
                Text(if (isLoading) "处理中..." else if (isRegister) "注册" else "登录")
            }

            // Toggle login/register
            P({
                style {
                    textAlign("center")
                    color(Color("#4CAF50"))
                    cursor("pointer")
                    fontSize(14.px)
                }
                onClick { isRegister = !isRegister; errorMsg = "" }
            }) {
                Text(if (isRegister) "已有账号？去登录" else "没有账号？去注册")
            }
        }
    }
}

@Composable
fun GamePage(onLogout: () -> Unit) {
    var refreshTrigger by remember { mutableStateOf(0) }
    var currentTab by remember { mutableStateOf("cultivation") }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            refreshTrigger++
        }
    }

    val s = GameEngine.state
    val attr = remember(refreshTrigger) { GameEngine.calcAttributes() }

    Div({
        style {
            fontFamily("Microsoft YaHei, sans-serif")
            backgroundColor(Color("#1a1a2e"))
            color(Color("#eee"))
            minHeight(100.vh)
            padding(0.px)
            margin(0.px)
        }
    }) {
        // Top Bar with logout
        TopBar(s, attr, refreshTrigger, onLogout)

        // Tab Navigation
        TabNavigation(currentTab) { currentTab = it }

        // Main Content
        Div({
            style {
                padding(16.px)
                maxWidth(800.px)
                margin(0.px, Length.Auto)
            }
        }) {
            when (currentTab) {
                "cultivation" -> CultivationPanel(refreshTrigger)
                "adventure" -> AdventurePanel(refreshTrigger)
                "equipment" -> EquipmentPanel(refreshTrigger)
                "shop" -> ShopPanel(refreshTrigger)
                "wiki" -> WikiPanel()
            }
        }

        // Battle Log
        BattleLogPanel(refreshTrigger)
    }
}

@Composable
fun TopBar(s: com.example.garygame.engine.GameState, attr: PlayerAttributes, trigger: Int, onLogout: () -> Unit) {
    val cost = remember(trigger) { RealmData.breakthroughCost(s.level) }
    val progress = remember(trigger) { (s.soulPower * 100 / cost.coerceAtLeast(1)).toInt().coerceIn(0, 100) }

    Div({
        style {
            backgroundColor(Color("#16213e"))
            padding(12.px)
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.SpaceBetween)
            alignItems(AlignItems.Center)
            flexWrap(FlexWrap.Wrap)
        }
    }) {
        Span({
            style {
                color(Color("#e94560"))
                fontWeight("bold")
                fontSize(18.px)
            }
        }) {
            Text("${RealmData.name(s.level)} Lv.${s.level} | 攻:${attr.atk} | 防:${attr.pdef}")
        }
        Span({
            style {
                color(Color("#FFD700"))
                fontSize(16.px)
            }
        }) {
            Text("💰 ${GameEngine.formatNum(s.gold)}")
        }
        Span({
            style {
                color(Color("#4CAF50"))
                fontSize(14.px)
            }
        }) {
            Text("魂力: ${GameEngine.formatNum(s.soulPower)}/${GameEngine.formatNum(cost)} ($progress%)")
        }
        Button(attrs = {
            style {
                backgroundColor(Color.transparent)
                color(Color("#f44336"))
                border("1px solid #f44336")
                borderRadius(6.px)
                padding(4.px, 12.px)
                fontSize(13.px)
                cursor("pointer")
            }
            onClick { onLogout() }
        }) {
            Text("退出")
        }
    }
}

@Composable
fun TabNavigation(currentTab: String, onTabChange: (String) -> Unit) {
    val tabs = listOf(
        "cultivation" to "🧘 修炼",
        "adventure" to "🗺️ 冒险",
        "equipment" to "⚔️ 装备",
        "shop" to "🏪 商店",
        "wiki" to "📖 百科"
    )

    Div({
        style {
            backgroundColor(Color("#0f3460"))
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.SpaceAround)
            padding(8.px, 0.px)
        }
    }) {
        tabs.forEach { (key, label) ->
            val isActive = key == currentTab
            Button(attrs = {
                style {
                    backgroundColor(if (isActive) Color("#e94560") else Color.transparent)
                    color(if (isActive) Color.white else Color("#aaa"))
                    border(if (isActive) "2px solid #e94560" else "2px solid transparent")
                    borderRadius(8.px)
                    padding(8.px, 16.px)
                    cursor("pointer")
                    fontSize(14.px)
                }
                onClick { onTabChange(key) }
            }) {
                Text(label)
            }
        }
    }
}

@Composable
fun CultivationPanel(trigger: Int) {
    val s = GameEngine.state

    Div({
        style {
            backgroundColor(Color("#16213e"))
            borderRadius(12.px)
            padding(20.px)
            marginBottom(16.px)
        }
    }) {
        H3({ style { color(Color("#e94560")); margin(0.px, 0.px, 12.px) } }) { Text("🧘 修炼") }

        if (s.chosenSchool == null) {
            P({ style { color(Color("#FFD700")); marginBottom(12.px) } }) {
                Text("请先选择修炼流派：")
            }
            SoulSchool.entries.filter { it.category == SchoolCategory.BASIC }.forEach { school ->
                Button(attrs = {
                    style {
                        backgroundColor(Color("#0f3460"))
                        color(Color.white)
                        border("1px solid #4CAF50")
                        borderRadius(8.px)
                        padding(8.px, 16.px)
                        margin(4.px)
                        cursor("pointer")
                    }
                    onClick { GameEngine.doChooseSchool(school) }
                }) {
                    Text("${school.icon} ${school.displayName}")
                }
            }
        } else {
            P({ style { color(Color("#aaa")); marginBottom(8.px) } }) {
                Text("流派: ${s.chosenSchool!!.displayName} | 武魂: ${s.martialSoul?.name ?: "未觉醒"}")
            }

            Button(attrs = {
                style {
                    backgroundColor(Color("#4CAF50"))
                    color(Color.white)
                    border("none")
                    borderRadius(8.px)
                    padding(12.px, 24.px)
                    fontSize(16.px)
                    cursor("pointer")
                    margin(4.px)
                }
                onClick { GameEngine.doCultivate() }
            }) {
                Text("修炼 +${GameEngine.cultivatePerTick()}魂力")
            }

            val cost = RealmData.breakthroughCost(s.level)
            val canBreak = s.soulPower >= cost && !s.isLevelCapped
            Button(attrs = {
                style {
                    backgroundColor(if (canBreak) Color("#e94560") else Color("#555"))
                    color(Color.white)
                    border("none")
                    borderRadius(8.px)
                    padding(12.px, 24.px)
                    fontSize(16.px)
                    cursor(if (canBreak) "pointer" else "not-allowed")
                    margin(4.px)
                }
                onClick { if (canBreak) GameEngine.doBreakthrough() }
            }) {
                Text("突破 (${GameEngine.formatNum(cost)}魂力)")
            }

            Button(attrs = {
                style {
                    backgroundColor(Color("#FF9800"))
                    color(Color.white)
                    border("none")
                    borderRadius(8.px)
                    padding(12.px, 24.px)
                    fontSize(16.px)
                    cursor("pointer")
                    margin(4.px)
                }
                onClick { GameEngine.doBattle() }
            }) {
                Text("⚔️ 战斗")
            }

            Button(attrs = {
                style {
                    backgroundColor(if (s.autoBattle) Color("#f44336") else Color("#2196F3"))
                    color(Color.white)
                    border("none")
                    borderRadius(8.px)
                    padding(12.px, 24.px)
                    fontSize(16.px)
                    cursor("pointer")
                    margin(4.px)
                }
                onClick { GameEngine.toggleAutoBattle() }
            }) {
                Text(if (s.autoBattle) "🔴 停止挂机" else "🟢 自动挂机")
            }

            val maxHp = remember(trigger) { GameEngine.calcMaxHp() }
            P({ style { color(Color("#aaa")); marginTop(12.px) } }) {
                Text("HP: ${GameEngine.formatNum(s.currentHp)} / ${GameEngine.formatNum(maxHp)}")
            }

            val monster = s.currentMonster
            if (monster != null && s.inBattle) {
                Div({
                    style {
                        backgroundColor(Color("#2a1a1a"))
                        borderRadius(8.px)
                        padding(12.px)
                        marginTop(8.px)
                    }
                }) {
                    P({ style { color(Color("#f44336")) } }) {
                        Text("👾 ${monster.name} HP: ${GameEngine.formatNum(monster.hp)}/${GameEngine.formatNum(monster.maxHp)}")
                    }
                }
            }
        }
    }
}

@Composable
fun AdventurePanel(trigger: Int) {
    val s = GameEngine.state

    Div({
        style {
            backgroundColor(Color("#16213e"))
            borderRadius(12.px)
            padding(20.px)
            marginBottom(16.px)
        }
    }) {
        H3({ style { color(Color("#e94560")); margin(0.px, 0.px, 12.px) } }) { Text("🗺️ 冒险地图") }

        MapData.all.forEachIndexed { index, map ->
            val isUnlocked = s.level >= map.unlockLevel
            val isCurrent = s.currentMapId == index

            Div({
                style {
                    backgroundColor(if (isCurrent) Color("#0f3460") else Color("#1a1a2e"))
                    borderRadius(8.px)
                    padding(12.px)
                    margin(4.px, 0.px)
                    opacity(if (isUnlocked) 1.0 else 0.5)
                    cursor(if (isUnlocked) "pointer" else "not-allowed")
                }
                onClick { if (isUnlocked && !isCurrent) GameEngine.doChangeMap(index) }
            }) {
                Span({
                    style {
                        color(if (isCurrent) Color("#FFD700") else Color("#eee"))
                        fontWeight("bold")
                    }
                }) {
                    Text("${if (isCurrent) "📍 " else ""}${map.name} (Lv.${map.unlockLevel}+)")
                }
                Span({
                    style { color(Color("#aaa")); marginLeft(8.px) }
                }) {
                    Text(map.description)
                }
            }
        }
    }
}

@Composable
fun EquipmentPanel(trigger: Int) {
    val s = GameEngine.state

    Div({
        style {
            backgroundColor(Color("#16213e"))
            borderRadius(12.px)
            padding(20.px)
            marginBottom(16.px)
        }
    }) {
        H3({ style { color(Color("#e94560")); margin(0.px, 0.px, 12.px) } }) { Text("⚔️ 装备") }

        if (s.martialSoul != null) {
            val soul = s.martialSoul!!
            Div({
                style {
                    backgroundColor(Color("#2a2a4e"))
                    borderRadius(8.px)
                    padding(12.px)
                    marginBottom(8.px)
                }
            }) {
                P({ style { color(Color("#FFD700")) } }) {
                    Text("${soul.rarity.displayName}·${soul.name}")
                }
                P({ style { color(Color("#aaa")) } }) {
                    Text("技能: ${soul.skill.name} - ${soul.skill.description}")
                }
            }
        }

        H4({ style { color(Color("#4CAF50")); margin(12.px, 0.px, 8.px) } }) { Text("魂环 (${s.soulRings.size}/${RealmData.maxSoulRings(s.level)})") }
        SoulRingSlots.all.forEach { slot ->
            val ring = s.soulRings[slot.index]
            if (ring != null) {
                Div({
                    style {
                        backgroundColor(Color("#1a3a1a"))
                        borderRadius(6.px)
                        padding(8.px)
                        margin(2.px, 0.px)
                    }
                }) {
                    val name = SoulRingSystem.fullDisplayName(ring.yearOrdinal, ring.qualityOrdinal, ring.percentage)
                    P({ style { color(Color("#4CAF50")) } }) {
                        Text("${slot.displayName}: $name")
                    }
                    if (ring.skill != null) {
                        P({ style { color(Color("#aaa")); fontSize(12.px) } }) {
                            Text("技能: ${ring.skill.name}")
                        }
                    }
                }
            } else if (s.level >= RealmData.ringSlotUnlockLevel(slot.index)) {
                Div({
                    style {
                        backgroundColor(Color("#1a1a2e"))
                        borderRadius(6.px)
                        padding(8.px)
                        margin(2.px, 0.px)
                        border("1px dashed #555")
                    }
                }) {
                    P({ style { color(Color("#555")) } }) { Text("${slot.displayName}: 空") }
                }
            }
        }

        H4({ style { color(Color("#2196F3")); margin(12.px, 0.px, 8.px) } }) { Text("魂骨 (${s.soulBones.size}/${RealmData.maxSoulBones(s.level)})") }
        BoneType.entries.forEachIndexed { idx, boneType ->
            val bone = s.soulBones[idx]
            if (bone != null) {
                Div({
                    style {
                        backgroundColor(Color("#1a1a3a"))
                        borderRadius(6.px)
                        padding(8.px)
                        margin(2.px, 0.px)
                    }
                }) {
                    P({ style { color(Color("#2196F3")) } }) {
                        Text("${boneType.displayName}: ${BoneRarity.fullDisplayName(bone.combinedTier)} +${bone.enhanceLevel}")
                    }
                }
            }
        }
    }
}

@Composable
fun ShopPanel(trigger: Int) {
    val s = GameEngine.state

    Div({
        style {
            backgroundColor(Color("#16213e"))
            borderRadius(12.px)
            padding(20.px)
            marginBottom(16.px)
        }
    }) {
        H3({ style { color(Color("#e94560")); margin(0.px, 0.px, 12.px) } }) { Text("🏪 商店") }

        P({ style { color(Color("#FFD700")); marginBottom(12.px) } }) {
            Text("Boss币: ${s.bossCoin}")
        }

        H4({ style { color(Color("#FF9800")); margin(8.px, 0.px) } }) { Text("Boss商店") }
        BossShopData.ringItems.forEach { item ->
            val unlocked = BossShopData.isTierUnlocked(item.tier, s.currentMapId)
            Button(attrs = {
                style {
                    backgroundColor(if (unlocked) Color("#0f3460") else Color("#333"))
                    color(if (unlocked) Color.white else Color("#666"))
                    border("1px solid #444")
                    borderRadius(6.px)
                    padding(8.px, 12.px)
                    margin(4.px)
                    cursor(if (unlocked) "pointer" else "not-allowed")
                }
                onClick { if (unlocked) GameEngine.doBuyBossRing(item.combinedTier) }
            }) {
                Text("${item.icon} ${item.label} (${item.cost}Boss币)")
            }
        }
    }
}

@Composable
fun WikiPanel() {
    Div({
        style {
            backgroundColor(Color("#16213e"))
            borderRadius(12.px)
            padding(20.px)
            marginBottom(16.px)
        }
    }) {
        H3({ style { color(Color("#e94560")); margin(0.px, 0.px, 12.px) } }) { Text("📖 游戏百科") }

        H4({ style { color(Color("#FFD700")) } }) { Text("境界一览") }
        RealmData.names.forEachIndexed { idx, name ->
            P({ style { color(Color("#aaa")); margin(2.px, 0.px) } }) {
                Text("Lv.${idx * 10 + 1}-${(idx + 1) * 10}: $name")
            }
        }

        H4({ style { color(Color("#4CAF50")); margin(12.px, 0.px, 8.px) } }) { Text("武魂一览") }
        MartialSoulPool.all.forEach { soul ->
            P({ style { color(Color("#aaa")); margin(2.px, 0.px) } }) {
                Text("${soul.rarity.displayName}·${soul.name} - ${soul.description}")
            }
        }
    }
}

@Composable
fun BattleLogPanel(trigger: Int) {
    val logs = remember(trigger) { GameEngine.getLog() }

    Div({
        style {
            backgroundColor(Color("#0a0a1a"))
            borderRadius(12.px)
            padding(16.px)
            margin(16.px, 0.px)
            maxHeight(300.px)
            overflowY("auto")
        }
    }) {
        H4({ style { color(Color("#e94560")); margin(0.px, 0.px, 8.px) } }) { Text("📜 战斗日志") }
        logs.take(20).forEach { log ->
            P({
                style {
                    color(Color("#aaa"))
                    margin(2.px, 0.px)
                    fontSize(13.px)
                }
            }) {
                Text(log)
            }
        }
    }
}
