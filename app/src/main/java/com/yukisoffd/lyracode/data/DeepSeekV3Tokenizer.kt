package com.yukisoffd.lyracode.data

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.ceil

class DeepSeekV3Tokenizer private constructor(private val root: TrieNode?) {
    fun count(text: String): Long {
        if (text.isBlank()) return 0L
        val trie = root ?: return estimateFallback(text)
        var index = 0
        var tokens = 0L
        while (index < text.length) {
            var node = trie
            var cursor = index
            var longest = -1
            while (cursor < text.length) {
                node = node.children[text[cursor]] ?: break
                cursor++
                if (node.terminal) longest = cursor
            }
            if (longest > index) {
                index = longest
            } else {
                index++
            }
            tokens++
        }
        return tokens
    }

    private fun estimateFallback(text: String): Long {
        var tokens = 0L
        var asciiRun = 0
        fun flushAscii() {
            if (asciiRun > 0) {
                tokens += ceil(asciiRun / 4.0).toLong().coerceAtLeast(1L)
                asciiRun = 0
            }
        }
        text.forEach { ch ->
            if (ch.code in 0x20..0x7E) {
                asciiRun++
            } else {
                flushAscii()
                tokens++
            }
        }
        flushAscii()
        return tokens
    }

    private class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var terminal = false
    }

    companion object {
        @Volatile
        private var cached: DeepSeekV3Tokenizer? = null

        fun get(context: Context): DeepSeekV3Tokenizer {
            return cached ?: synchronized(this) {
                cached ?: load(context.applicationContext).also { cached = it }
            }
        }

        private fun load(context: Context): DeepSeekV3Tokenizer {
            return runCatching {
                val json = context.assets.open("tokenizer.json").use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
                }
                val vocab = JSONObject(json).getJSONObject("model").getJSONObject("vocab")
                val root = TrieNode()
                val keys = vocab.keys()
                while (keys.hasNext()) {
                    val token = keys.next()
                    insertVariants(root, token)
                }
                DeepSeekV3Tokenizer(root)
            }.getOrElse {
                DeepSeekV3Tokenizer(null)
            }
        }

        private fun insertVariants(root: TrieNode, token: String) {
            if (token.isBlank() || token.length > 256 || token.startsWith("<｜")) return
            insert(root, token)
            val normalized = token
                .replace("Ġ", " ")
                .replace("▁", " ")
                .replace("Ċ", "\n")
                .replace("ĉ", "\t")
            if (normalized != token && normalized.isNotBlank()) {
                insert(root, normalized)
            }
        }

        private fun insert(root: TrieNode, token: String) {
            var node = root
            token.forEach { ch ->
                node = node.children.getOrPut(ch) { TrieNode() }
            }
            node.terminal = true
        }
    }
}
