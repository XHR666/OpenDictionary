package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 口语音标/弱读/吞音/喉塞音提示（自编教学整理，非词典发行数据）。 */
object PronunciationTips {
    private val weak = mapOf(
        "the" to "弱读 /ðə/；元音前 /ði/", "and" to "弱读 /ən/ 或 /n/", "to" to "弱读 /tə/；元音前 /tu/",
        "of" to "弱读 /əv/", "for" to "弱读 /fə/", "can" to "肯定句弱读 /kən/", "have" to "弱读 /həv/ 或 /əv/",
        "was" to "弱读 /wəz/", "were" to "弱读 /wə/", "a" to "弱读 /ə/", "an" to "弱读 /ən/",
        "some" to "弱读 /səm/", "them" to "弱读 /ðəm/", "than" to "弱读 /ðən/", "but" to "弱读 /bət/",
        "at" to "弱读 /ət/", "from" to "弱读 /frəm/", "as" to "弱读 /əz/", "that" to "连词弱读 /ðət/",
        "would" to "弱读 /wəd/", "could" to "弱读 /kəd/", "should" to "弱读 /ʃəd/", "been" to "弱读 /bɪn/",
        "because" to "重音常在第二音节 /bɪˈkəz/", "important" to "重音第二音节 /ɪmˈpɔːtənt/",
    )
    private val features = mapOf(
        "button" to "美音常出现喉塞音：/ˈbʌʔn/（t 不完全爆破）",
        "water" to "美音 t 常闪音：/ˈwɔːtər/（接近 d 音）",
        "little" to "美音可为 /ˈlɪtl/ 或喉塞音 /ˈlɪʔl/",
        "mountain" to "美音常吞 t：/ˈmaʊn(t)n/",
        "internet" to "美音常吞 t：/ˈɪntərnet/ 中的 t 弱化",
        "going" to "口语常弱化为 gonna /ˈɡənə/（非正式）",
        "want to" to "口语常弱化为 wanna /ˈwɒnə/（非正式）",
        "kind of" to "口语弱化为 kinda /ˈkaɪndə/（非正式）",
        "got to" to "口语弱化为 gotta /ˈɡɒtə/（非正式）",
    )
    fun tips(word: String): List<String> {
        val w = word.lowercase()
        return buildList {
            weak[w]?.let { add(it) }
            features[w]?.let { add(it) }
        }
    }
}

private fun splitSyllables(word: String): List<String> {
    val w = word.lowercase().replace(Regex("[^a-z]"), "")
    if (w.length < 3) return listOf(word)
    val vowels = "aeiouy"
    val out = mutableListOf<String>()
    var cur = StringBuilder()
    var i = 0
    while (i < w.length) {
        cur.append(w[i])
        val isVowel = w[i] in vowels
        val nextIsVowel = i + 1 < w.length && w[i + 1] in vowels
        if (isVowel && !nextIsVowel && i + 2 < w.length && w[i + 1] !in vowels && w[i + 2] in vowels) {
            out.add(cur.toString()); cur = StringBuilder()
        }
        i++
    }
    if (cur.isNotEmpty()) out.add(cur.toString())
    return out
}

@Composable
fun PronunciationCard(word: String) {
    val tips = PronunciationTips.tips(word)
    val syllables = splitSyllables(word)
    if (tips.isEmpty() && syllables.size <= 1) return
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(13.dp)).background(OD.Pearl).border(1.dp, OD.Hairline, RoundedCornerShape(13.dp)).padding(11.dp)) {
        if (syllables.size > 1) {
            Text("音节（参考切分）", style = OD.Micro.copy(fontSize = 8.5.sp))
            Text(syllables.joinToString(" · "), style = OD.Body.copy(fontSize = 12.5.sp))
        }
        tips.forEachIndexed { i, t ->
            if (i > 0 || syllables.size > 1) Spacer(Modifier.height(5.dp))
            Text("口语提示", style = OD.Micro.copy(fontSize = 8.5.sp, color = OD.CyanDeep))
            Text(t, style = OD.Cap.copy(fontSize = 11.5.sp, lineHeight = 17.sp))
        }
    }
}
