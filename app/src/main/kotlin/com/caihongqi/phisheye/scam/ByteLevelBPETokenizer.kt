package com.caihongqi.phisheye.scam

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.nio.charset.Charset

class ByteLevelBPETokenizer(
    private val vocab: Map<String, Long>,
    private val merges: Map<Pair<String, String>, Int>
) {
    private val byteEncoder: Map<Int, Char> = bytesToUnicode()
    private val byteDecoder: Map<Char, Int> = byteEncoder.entries.associate { (k, v) -> v to k }
    private val cache = mutableMapOf<String, String>()

    data class Encoding(val ids: LongArray, val attentionMask: LongArray)

    fun encode(text: String): Encoding {
        // 1. Pre-tokenization and Byte-level encoding
        // RoBERTa uses a specific regex for pre-tokenization, but usually splitting by space
        // and handling prefix space is the main part.
        // We will assume the input text needs a prefix space if it's the start of a sentence.
        // Simplified: Split by whitespace, preserving the whitespace as a prefix to the next word?
        // RoBERTa treats spaces as part of the token (Ġ).
        
        // A simple approximation for RoBERTa pre-tokenization:
        // 'Hello world' -> 'Hello', 'Ġworld'
        
        // Use a simpler approach: regex split based on GPT-2/RoBERTa pattern
        // 's|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+
        
        val pattern = Regex("'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+")
        val words = pattern.findAll(text).map { it.value }.toList()

        val bpeTokens = mutableListOf<String>()
        bpeTokens.add("<s>") // BOS token

        for (token in words) {
            // Encode token bytes to unicode chars
            val tokenBytes = token.toByteArray(Charsets.UTF_8)
            val tokenChars = tokenBytes.map { byteEncoder[it.toInt() and 0xFF]!! }.joinToString("")
            
            // Apply BPE
            val bpeToken = bpe(tokenChars)
            
            // Split BPE result into subwords
            val subwords = bpeToken.split(" ")
            bpeTokens.addAll(subwords)
        }
        
        bpeTokens.add("</s>") // EOS token

        // Convert to IDs
        val idsList = bpeTokens.map {
            vocab[it] ?: vocab["<unk>"] ?: 0L
        }

        // Create Attention Mask (all 1s for valid tokens)
        val attentionMaskList = LongArray(idsList.size) { 1L }

        return Encoding(idsList.toLongArray(), attentionMaskList)
    }

    private fun bpe(token: String): String {
        if (cache.containsKey(token)) {
            return cache[token]!!
        }

        var word = token.map { it.toString() }.toMutableList()
        
        if (word.size <= 1) {
            return token
        }

        while (true) {
            var minRank = Int.MAX_VALUE
            var bestPair: Pair<String, String>? = null
            var bestPairIdx = -1

            for (i in 0 until word.size - 1) {
                val pair = Pair(word[i], word[i+1])
                val rank = merges[pair]
                if (rank != null && rank < minRank) {
                    minRank = rank
                    bestPair = pair
                    bestPairIdx = i
                }
            }

            if (bestPair == null) {
                break
            }

            val (first, second) = bestPair
            val newWord = mutableListOf<String>()
            var i = 0
            while (i < word.size) {
                if (i < word.size - 1 && word[i] == first && word[i+1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else {
                    newWord.add(word[i])
                    i += 1
                }
            }
            word = newWord
            if (word.size == 1) break
        }

        val result = word.joinToString(" ")
        cache[token] = result
        return result
    }

    companion object {
        fun newInstance(vocabFile: File, mergesFile: File): ByteLevelBPETokenizer {
            // Parse vocab.json
            val vocabJson = Json.parseToJsonElement(vocabFile.readText()).jsonObject
            val vocab = vocabJson.mapValues { it.value.jsonPrimitive.long }

            // Parse merges.txt
            val mergesLines = mergesFile.readLines()
            val merges = mutableMapOf<Pair<String, String>, Int>()
            // Skip version/comment line if exists (usually first line is '#version: 0.2')
            val startIdx = if (mergesLines.firstOrNull()?.startsWith("#") == true) 1 else 0
            
            for (i in startIdx until mergesLines.size) {
                val line = mergesLines[i].trim()
                if (line.isEmpty()) continue
                val parts = line.split(" ")
                if (parts.size == 2) {
                    merges[Pair(parts[0], parts[1])] = i
                }
            }

            return ByteLevelBPETokenizer(vocab, merges)
        }
        
        private fun bytesToUnicode(): Map<Int, Char> {
            val bs = mutableListOf<Int>()
            
            // list(range(ord("!"), ord("~")+1)) -> 33..126
            for (i in 33..126) bs.add(i)
            // list(range(ord("¡"), ord("¬")+1)) -> 161..172
            for (i in 161..172) bs.add(i)
            // list(range(ord("®"), ord("ÿ")+1)) -> 174..255
            for (i in 174..255) bs.add(i)
            
            val cs = bs.toMutableList()
            var n = 0
            
            for (b in 0..255) {
                if (!bs.contains(b)) {
                    bs.add(b)
                    cs.add(256 + n)
                    n += 1
                }
            }
            
            return bs.zip(cs).associate { (b, c) -> b to c.toChar() }
        }
    }
}
