package com.example.garygame

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.garygame.engine.GameEngine
import com.example.garygame.engine.GameEngine.NotifyType
import com.example.garygame.model.CodexData
import com.example.garygame.model.RealmData
import com.example.garygame.ui.AdventureFragment
import com.example.garygame.ui.CultivationFragment
import com.example.garygame.ui.EquipmentFragment
import com.example.garygame.ui.ShopFragment
import com.example.garygame.ui.WikiFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var tvTopRealm: TextView
    private lateinit var tvTopGold: TextView
    private lateinit var progressTopLevel: ProgressBar
    private lateinit var bottomNav: BottomNavigationView

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            try {
                GameEngine.tick()
                updateTopBar()
                showPendingNotifications()
            } catch (e: Exception) {
                GameEngine.addLog("⚠️ 自动循环异常: ${e.message}")
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 切换回主主题（去掉启动画面背景）
        setTheme(R.style.Theme_MyApplication)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        GameEngine.init(this)

        tvTopRealm = findViewById(R.id.tv_top_realm)
        tvTopGold = findViewById(R.id.tv_top_gold)
        progressTopLevel = findViewById(R.id.progress_top_level)
        bottomNav = findViewById(R.id.bottom_nav)

        val offlineReward = GameEngine.calculateOfflineReward()
        if (offlineReward > 0) {
            GameEngine.addLog("欢迎回来！离线修炼 +${GameEngine.formatNum(offlineReward)} 魂力")
        }
        GameEngine.state.lastExitTime = System.currentTimeMillis()

        if (savedInstanceState == null) {
            switchFragment(CultivationFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_cultivation -> CultivationFragment()
                R.id.nav_adventure -> AdventureFragment()
                R.id.nav_equipment -> EquipmentFragment()
                R.id.nav_shop -> ShopFragment()
                R.id.nav_wiki -> WikiFragment()
                else -> CultivationFragment()
            }
            switchFragment(fragment)
            true
        }

        handler.post(tickRunnable)
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun updateTopBar() {
        val s = GameEngine.state
        val attr = GameEngine.calcAttributes()
        tvTopRealm.text = "${RealmData.name(s.level)} Lv.${s.level} | 攻:${attr.atk}"

        tvTopGold.text = "💰 ${GameEngine.formatNum(s.gold)}"

        // 突破进度条
        val cost = RealmData.breakthroughCost(s.level)
        progressTopLevel.max = cost.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        progressTopLevel.progress = s.soulPower.coerceAtMost(cost).toInt()
    }

    private fun showPendingNotifications() {
        val events = GameEngine.pollNotifyEvents()
        if (events.isEmpty()) return
        // 只显示第一个事件，其余留到下次tick
        val event = events.first()
        val s = GameEngine.state

        // 检查是否已被跳过
        when (event.type) {
            NotifyType.BOSS_PURCHASE -> if (s.skipNotifyBossPurchase) return
            NotifyType.SHOP_PURCHASE -> if (s.skipNotifyShopPurchase) return
            NotifyType.HIGH_DROP -> if (s.skipNotifyHighDrop) return
            NotifyType.TITLE -> { /* 称号始终显示 */ }
            NotifyType.FEATURE_UNLOCK -> { /* 功能解锁始终显示 */ }
        }

        val hasSkipOption = event.type != NotifyType.TITLE && event.type != NotifyType.FEATURE_UNLOCK
        val cb = if (hasSkipOption) CheckBox(this).apply {
            text = when (event.type) {
                NotifyType.BOSS_PURCHASE -> "不再提示Boss商店购买"
                NotifyType.SHOP_PURCHASE -> "不再提示限时珍品购买"
                NotifyType.HIGH_DROP -> "不再提示高品质掉落"
                else -> "不再提示此类型"
            }
        } else null

        AlertDialog.Builder(this)
            .setTitle(event.title)
            .setMessage(event.message)
            .apply {
                if (cb != null) setView(cb)
            }
            .setPositiveButton("确定") { _, _ ->
                if (cb != null && cb.isChecked) {
                    when (event.type) {
                        NotifyType.BOSS_PURCHASE -> s.skipNotifyBossPurchase = true
                        NotifyType.SHOP_PURCHASE -> s.skipNotifyShopPurchase = true
                        NotifyType.HIGH_DROP -> s.skipNotifyHighDrop = true
                        else -> {}
                    }
                    s.save()
                }
            }
            .setCancelable(false)
            .show()
    }

    override fun onPause() {
        super.onPause()
        GameEngine.state.save()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        GameEngine.state.save()
    }
}
