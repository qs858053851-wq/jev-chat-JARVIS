package com.jev.probe

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Msg
import com.jev.probe.core.Prefs
import com.jev.probe.jev.JevClient
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val accent = Color.parseColor("#3A7AFE")
    private val ink = Color.parseColor("#111827")
    private val sub = Color.parseColor("#6B7280")

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.decorView.setBackgroundColor(Color.parseColor("#F2F3F5"))

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }
        scroll.addView(root)

        root.addView(header("设置"))

        // --- Jev 决策服务 ---
        root.addView(section("Jev (GeV) 意图判断服务"))
        val cardJev = card()
        cardJev.addView(text("负责 7 道实时意图与危险度分析、回复排序", 12f, sub))
        cardJev.addView(label("Jev API 密钥"))
        val jevKeyEdit = edit(prefs.effectiveJevKey, "填入 Jev / TypeSafe 密钥", password = true)
        cardJev.addView(jevKeyEdit)
        cardJev.addView(label("Jev 接口地址"))
        val jevUrlEdit = edit(prefs.effectiveJevUrl, Prefs.DEFAULT_JEV_URL)
        cardJev.addView(jevUrlEdit)
        root.addView(cardJev)

        // --- 回复生成服务 ---
        root.addView(section("回复生成与润色服务 (Command Go)"))
        val cardChat = card()
        cardChat.addView(text("负责起草 3 条不同策略的候选回复", 12f, sub))
        cardChat.addView(label("生成 API 密钥"))
        val chatKeyEdit = edit(prefs.effectiveChatKey, "填入 Command Go / OpenAI 兼容密钥", password = true)
        cardChat.addView(chatKeyEdit)
        cardChat.addView(label("生成接口地址"))
        val chatUrlEdit = edit(prefs.effectiveChatUrl, Prefs.DEFAULT_CHAT_URL)
        cardChat.addView(chatUrlEdit)

        cardChat.addView(label("回复生成模型"))
        val modelEdit = edit(prefs.replyModel, Prefs.DEFAULT_REPLY_MODEL)

        // Model quick-switch buttons
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(4))
        }
        val btnDeepseek = chipBtn("DeepSeek 4.1 Flash") {
            modelEdit.setText(Prefs.MODEL_DEEPSEEK_4_1_FLASH)
        }
        val btnGoogle = chipBtn("Google 3.8 Flash") {
            modelEdit.setText(Prefs.MODEL_GOOGLE_3_8_FLASH)
        }
        btnRow.addView(btnDeepseek)
        btnRow.addView(btnGoogle)
        cardChat.addView(btnRow)
        cardChat.addView(modelEdit)
        root.addView(cardChat)

        // --- 分析 ---
        root.addView(section("分析配置"))
        val card2 = card()
        card2.addView(label("关系描述（给 Jev 判断用）"))
        val relEdit = edit(prefs.relationship, Prefs.DEFAULT_REL)
        card2.addView(relEdit)
        card2.addView(label("会话白名单（每行一个关键词，空=所有会话）"))
        val wlEdit = edit(prefs.whitelist.joinToString("
"), "留空则对所有会话生效").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = 2
        }
        card2.addView(wlEdit)
        val autoRow = toggleRow("对方发消息时自动分析", prefs.autoAnalyze)
        card2.addView(autoRow)
        root.addView(card2)

        // --- 外观 ---
        root.addView(section("外观"))
        val card3 = card()
        val opacityLabel = label("悬浮窗不透明度：${prefs.overlayOpacity}%")
        card3.addView(opacityLabel)
        card3.addView(text("越低越透，越能看清下面的聊天", 12f, sub))
        val seek = SeekBar(this).apply {
            max = 40; progress = prefs.overlayOpacity - 60  // 60..100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                    opacityLabel.text = "悬浮窗不透明度：${p + 60}%"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        card3.addView(seek)
        root.addView(card3)

        // --- Actions ---
        val result = text("", 13f, sub).apply { setPadding(0, dp(12), 0, dp(4)) }
        root.addView(primaryBtn("保存") {
            prefs.jevKey = jevKeyEdit.text.toString().trim()
            prefs.jevUrl = jevUrlEdit.text.toString().trim().ifBlank { Prefs.DEFAULT_JEV_URL }
            prefs.chatKey = chatKeyEdit.text.toString().trim()
            prefs.chatUrl = chatUrlEdit.text.toString().trim().ifBlank { Prefs.DEFAULT_CHAT_URL }
            prefs.replyModel = modelEdit.text.toString().trim().ifBlank { Prefs.DEFAULT_REPLY_MODEL }
            prefs.relationship = relEdit.text.toString().ifBlank { Prefs.DEFAULT_REL }
            prefs.whitelist = wlEdit.text.toString().split("
").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            prefs.autoAnalyze = (autoRow.tag as? Boolean) ?: true
            prefs.overlayOpacity = seek.progress + 60
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        })
        root.addView(secondaryBtn("连通测试") {
            val jKey = jevKeyEdit.text.toString().trim()
            val jUrl = jevUrlEdit.text.toString().trim().ifBlank { Prefs.DEFAULT_JEV_URL }
            val cKey = chatKeyEdit.text.toString().trim()
            val cUrl = chatUrlEdit.text.toString().trim().ifBlank { Prefs.DEFAULT_CHAT_URL }
            val model = modelEdit.text.toString().trim().ifBlank { Prefs.DEFAULT_REPLY_MODEL }
            if (jKey.isBlank() || cKey.isBlank()) {
                result.text = "请先填写 Jev 密钥与生成模型密钥"
                return@secondaryBtn
            }
            result.text = "正在测试 Jev 与生成模型连通性…"
            worker.execute {
                val demo = ChatSnapshot("连通测试", listOf(
                    Msg("other", "在吗？"), Msg("me", "在"), Msg("other", "那你说说昨天答应我的事")))
                val client = JevClient(
                    jevKey = jKey,
                    jevUrl = jUrl,
                    chatKey = cKey,
                    chatUrl = cUrl,
                    replyModel = model
                )
                val a = client.analyze(demo, prefs.relationship)
                main.post {
                    result.text = if (a.error != null) "测试失败：${a.error}"
                    else "✓ 连通成功！
- 意图判断：${a.trueIntent?.choice ?: "ok"} (耗时 ${a.latencyMs}ms)
- 候选回复：已生成 ${a.rankedReplies.size} 条 (${model})"
                }
            }
        })
        root.addView(result)

        setContentView(scroll)
    }

    private fun chipBtn(labelText: String, onClick: () -> Unit) = TextView(this).apply {
        text = labelText; textSize = 12f; gravity = Gravity.CENTER
        setTextColor(accent); background = round(dp(8), Color.parseColor("#EFF6FF"), stroke = true)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(8)
        }
        setOnClickListener { onClick() }
    }

    private fun toggleRow(labelText: String, initial: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(2)); tag = initial
        }
        val lab = text(labelText, 14f, ink).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = TextView(this).apply {
            text = if (initial) "开" else "关"; textSize = 13f; gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (initial) Color.WHITE else sub)
            background = round(dp(10), if (initial) accent else Color.parseColor("#E5E7EB"))
            setPadding(dp(18), dp(6), dp(18), dp(6))
        }
        sw.setOnClickListener {
            val now = !((row.tag as? Boolean) ?: true); row.tag = now
            sw.text = if (now) "开" else "关"
            sw.setTextColor(if (now) Color.WHITE else sub)
            sw.background = round(dp(10), if (now) accent else Color.parseColor("#E5E7EB"))
        }
        row.addView(lab); row.addView(sw)
        return row
    }

    // atoms
    private fun header(t: String) = text(t, 24f, ink, bold = true).apply { setPadding(0, 0, 0, dp(4)) }
    private fun section(t: String) = text(t, 12f, sub, bold = true).apply { setPadding(dp(2), dp(16), 0, dp(6)) }
    private fun label(t: String) = text(t, 13f, ink, bold = true).apply { setPadding(0, dp(12), 0, dp(4)) }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(dp(14), Color.WHITE)
        setPadding(dp(14), dp(4), dp(14), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun edit(value: String, hint: String, password: Boolean = false) = EditText(this).apply {
        setText(value); this.hint = hint; textSize = 14f; setTextColor(ink)
        background = round(dp(8), Color.parseColor("#F3F4F6"))
        setPadding(dp(10), dp(10), dp(10), dp(10))
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) }
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun primaryBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = 15f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE); background = round(dp(12), accent)
        setPadding(dp(16), dp(13), dp(16), dp(13))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) }
        setOnClickListener { onClick() }
    }

    private fun secondaryBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = 15f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD)
        setTextColor(accent); background = round(dp(12), Color.WHITE, stroke = true)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
        setOnClickListener { onClick() }
    }

    private fun round(radius: Int, color: Int, stroke: Boolean = false) = GradientDrawable().apply {
        cornerRadius = radius.toFloat(); setColor(color); if (stroke) setStroke(dp(1), accent)
    }

    override fun onDestroy() { super.onDestroy(); worker.shutdownNow() }
}
