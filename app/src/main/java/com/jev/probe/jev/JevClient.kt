package com.jev.probe.jev

import android.util.Log
import com.jev.probe.core.Analysis
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Choice
import com.jev.probe.core.Prefs
import com.jev.probe.core.RankedReply
import com.jev.probe.core.Score
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "JevClient"

/**
 * Dual-channel client:
 * 1. Jev decisions endpoint: calls TypeSafe (or OpenRouter) for 7 judgment questions + candidate ranking.
 * 2. Chat completions endpoint: calls Command Go (or OpenAI/OpenRouter compatible endpoint)
 *    to draft candidate replies using DeepSeek 4.1 Flash or Google 3.8 Flash.
 */
class JevClient(
    private val jevKey: String,
    private val jevUrl: String = Prefs.DEFAULT_JEV_URL,
    private val chatKey: String,
    private val chatUrl: String = Prefs.DEFAULT_CHAT_URL,
    private val replyModel: String = Prefs.DEFAULT_REPLY_MODEL
) {

    /** The 7 judgment questions only (fast, ~1s). No candidate generation. */
    fun judge(snapshot: ChatSnapshot, relationship: String): Analysis {
        val start = System.currentTimeMillis()
        try {
            val modelName = if (jevUrl.contains("openrouter.ai")) "typesafe/jev-1.13" else "jev-latest"
            val body = JSONObject()
                .put("model", modelName)
                .put("state", JevQuestions.buildState(snapshot, relationship))
                .put("questions", JevQuestions.judge())
            val answers = postJson(jevUrl, body, jevKey).optJSONObject("answers") ?: JSONObject()
            return Analysis(
                trueIntent = parseChoice(answers.optJSONObject("true_intent")),
                dangerLevel = parseScore(answers.optJSONObject("danger_level")),
                sheNeeds = parseChoice(answers.optJSONObject("she_needs")),
                shouldReplyNow = answers.optJSONObject("should_reply_now")?.optDouble("noul"),
                bestAction = parseChoice(answers.optJSONObject("best_action")),
                tensionResolved = answers.optJSONObject("tension_resolved")?.optDouble("noul"),
                literalQuestion = answers.optJSONObject("literal_question")?.optDouble("noul"),
                rankedReplies = emptyList(),
                latencyMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            Log.w(TAG, "judge failed: ${e.message}")
            return Analysis(null, null, null, null, null, null, null, emptyList(),
                System.currentTimeMillis() - start, error = readableError(e))
        }
    }

    /** Draft 3 candidate replies (generative model) then Jev-rank them. */
    fun draftAndRank(snapshot: ChatSnapshot, relationship: String): List<RankedReply> {
        val candidates = generateCandidates(snapshot, relationship)
        val modelName = if (jevUrl.contains("openrouter.ai")) "typesafe/jev-1.13" else "jev-latest"
        val questions = JSONObject().put("best_reply",
            JevQuestions.rankQuestion(candidates).getJSONObject("best_reply"))
        val body = JSONObject()
            .put("model", modelName)
            .put("state", JevQuestions.buildState(snapshot, relationship))
            .put("questions", questions)
        val answers = postJson(jevUrl, body, jevKey).optJSONObject("answers") ?: JSONObject()
        return parseRanked(answers.optJSONObject("best_reply"), candidates)
    }

    /** Convenience for the settings connectivity test: judge + replies, sequential. */
    fun analyze(snapshot: ChatSnapshot, relationship: String): Analysis {
        val a = judge(snapshot, relationship)
        if (a.error != null) return a
        val ranked = try { draftAndRank(snapshot, relationship) } catch (e: Exception) {
            return a.copy(error = "生成候选回复失败: ${e.message}")
        }
        return a.copy(rankedReplies = ranked)
    }

    /** Ask a generative model for exactly 3 varied candidate replies (Chinese). */
    private fun generateCandidates(snapshot: ChatSnapshot, relationship: String): List<String> {
        val convo = snapshot.messages.takeLast(10).joinToString("\n") {
            (if (it.side == "me") "我" else "对方") + "：" + it.text
        }
        val sys = "你是中文即时通讯回复助手。只输出一个 JSON 数组，含且仅含 3 条候选回复文本，" +
            "三条策略要有区别（例如：一条稳妥承接、一条给具体行动或承诺、一条简短低姿态）。" +
            "每条不超过 40 字，口语、自然、像真人在聊天软件里发消息。不要解释，不要加引号以外的内容，直接输出 JSON 数组。"
        val user = "关系：$relationship\n\n最近对话：\n$convo\n\n请给出 3 条候选回复。"
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", sys))
            .put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject()
            .put("model", replyModel)
            .put("messages", messages)
            .put("temperature", 0.8)
        val resp = postJson(chatUrl, body, chatKey)
        val content = resp.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content") ?: ""
        return parseThree(content)
    }

    private fun parseThree(content: String): List<String> {
        val start = content.indexOf('[')
        val end = content.lastIndexOf(']')
        if (start >= 0 && end > start) {
            try {
                val arr = JSONArray(content.substring(start, end + 1))
                val out = ArrayList<String>()
                for (i in 0 until arr.length()) out.add(arr.getString(i).trim())
                if (out.size >= 3) return out.take(3)
                while (out.size < 3) out.add("（稍等，我看下）")
                return out
            } catch (_: Exception) { }
        }
        // Fallback: split lines.
        val lines = content.split("\n").map { it.trim().trimStart('-', '*', '1', '2', '3', '.', ' ', '"') }
            .filter { it.isNotBlank() }
        val out = lines.take(3).toMutableList()
        while (out.size < 3) out.add("（稍等，我看下）")
        return out
    }

    private fun parseChoice(o: JSONObject?): Choice? {
        if (o == null) return null
        val choice = o.optString("choice")
        val conf = o.optDouble("confidence")
        if (choice.isNullOrBlank()) return null
        return Choice(choice, if (conf.isNaN()) null else conf)
    }

    private fun parseScore(o: JSONObject?): Score? {
        if (o == null) return null
        val s = o.optInt("score", -1)
        val r = o.optString("reason")
        if (s < 0) return null
        return Score(s, r)
    }

    private fun parseRanked(replyObj: JSONObject?, candidates: List<String>): List<RankedReply> {
        if (replyObj == null) return candidates.map { RankedReply(it, null) }
        val probs = replyObj.optJSONObject("probabilities") ?: JSONObject()
        val mapped = candidates.mapIndexed { idx, text ->
            val key = ('A' + idx).toString()
            val prob = probs.optDouble(key)
            RankedReply(text, if (prob.isNaN()) null else prob)
        }
        return mapped.sortedByDescending { it.probability ?: 0.0 }
    }

    private fun postJson(urlStr: String, body: JSONObject, authKey: String): JSONObject {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $authKey")
            setRequestProperty("Accept", "application/json")
        }
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val respText = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw RuntimeException("HTTP $code: $respText")
        }
        return JSONObject(respText)
    }

    private fun readableError(e: Exception): String {
        val m = e.message ?: e.javaClass.simpleName
        return when {
            m.contains("401") -> "密钥被拒绝（401）"
            m.contains("429") -> "请求太频繁（429）"
            m.contains("timed out") -> "请求超时"
            m.contains("UnknownHostException") -> "网络不可达"
            else -> m.take(60)
        }
    }
}
