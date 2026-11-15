package com.will.lightreader.source.linovelib

/**
 * 鍝斿摡锛坙inovelib/bilinovel锛夌珷鑺傞〉鐨勬钀戒贡搴忚繕鍘熴€? * 绔欑偣鎶?#TextContent 閲岀殑 <p> 鎸?seed = chapterId*127+235 纭畾鎬ф礂鐗岋紙鍓?20 娈典笉鍔級锛? * 鏈畻娉曠敤鍚屼竴绉嶅瓙澶嶇幇缃崲锛屽啀鎶婃钀芥斁鍥炲師浣嶃€? * 鍏紡鏉ユ簮锛氬畼鏂?chapterlog.js 鍙嶆贩娣嗭紙bilinovel-downloader 鍙傝€冨疄鐜扮簿纭鍒伙級锛屽凡鐢ㄧ湡瀹炵珷鑺?236197 楠岃瘉銆? */
object ParagraphOrderRestorer {
    private const val KEEP_HEAD = 20

    /** order[i] = 椤靛簭 i 鐨勬钀芥墍灞炵殑鍘熸枃搴忓彿銆?     *  閫昏緫绮剧‘澶嶅埢瀹樻柟 chapterlog.js锛堢粡 bilinovel-downloader 鍙嶆贩娣嗛獙璇侊級锛?     *  娲楃浉瀵圭储寮曟暟缁?value锛屽啀鏄犲皠涓?aProperties锛?order锛夈€?*/
    fun pageToOriginalOrder(total: Int, chapterId: Int): IntArray {
        val order = IntArray(total) { it }
        if (total <= KEEP_HEAD) return order
        // seed 鍏紡鏉ヨ嚜瀹樻柟 JS锛歮s = chapterID*127 + 235
        var ms = chapterId.toLong() * 127 + 235
        val tailLen = total - KEEP_HEAD
        // value = 鐩稿绱㈠紩 0..tailLen-1锛孎isher-Yates 娲楃墝锛堜粠灏惧埌澶达級
        val value = IntArray(tailLen) { it }
        for (i in tailLen - 1 downTo 1) {
            ms = (ms * 9302 + 49397) % 233280
            val prop = (ms.toDouble() / 233280.0 * (i + 1)).toInt()
            val tmp = value[i]; value[i] = value[prop]; value[prop] = tmp
        }
        // aProperties[i+20] = value[i] + 20锛堝墠 20 娈典笉鍔級
        for (i in 0 until tailLen) {
            order[KEEP_HEAD + i] = value[i] + KEEP_HEAD
        }
        return order
    }

    /** scrambled 鏄〉搴忔钀斤紝杩斿洖鍘熸枃椤哄簭銆?*/
    fun <T> restore(scrambled: List<T>, chapterId: Int): List<T> {
        val order = pageToOriginalOrder(scrambled.size, chapterId)
        val arr = arrayOfNulls<Any>(scrambled.size)
        for (i in scrambled.indices) arr[i] = scrambled[order[i]]
        @Suppress("UNCHECKED_CAST")
        return arr.map { it as T }
    }
}
