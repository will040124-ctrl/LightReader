package com.will.lightreader.source.net

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Decode bytes once. Browser DOM strings have already been decoded and must bypass this. */
object ResponseText {
    private val declaration = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)

    fun decode(bytes: ByteArray, contentType: String? = null, fallback: Charset = Charsets.UTF_8): String {
        if (bytes.size >= 3 && bytes.take(3) == listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            return strict(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8)
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return strict(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16LE)
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return strict(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16BE)
        }
        val declared = declaration.find(contentType.orEmpty())?.groupValues?.get(1)
            ?: declaration.find(String(bytes.take(4096).toByteArray(), Charsets.ISO_8859_1))?.groupValues?.get(1)
        val utf8First = declared == null || declared.lowercase().startsWith("gb")
        val candidates = (if (utf8First) listOf(Charsets.UTF_8) else emptyList()) + listOfNotNull(declared?.let { runCatching { Charset.forName(it) }.getOrNull() },
            Charsets.UTF_8, fallback, Charset.forName("GB18030")).distinct()
        for (charset in candidates) {
            runCatching { strict(bytes, charset) }.getOrNull()?.let { return it }
        }
        throw java.io.IOException("正文字符编码无法识别，请重新获取章节")
    }

    private fun strict(bytes: ByteArray, charset: Charset): String = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
}

/** Shared source for the page-fetch bridge; Response.text() always assumes UTF-8. */
object BrowserResponseText {
    fun decoder(fallback: Charset): String = """
        function(r){return r.arrayBuffer().then(function(buffer){
          var bytes=new Uint8Array(buffer), hint='${fallback.name()}', type=r.headers.get('Content-Type')||'';
          var ascii=Array.prototype.map.call(bytes.subarray(0,4096),function(b){return String.fromCharCode(b);}).join('');
          var declaration=/charset\s*=\s*["']?([A-Za-z0-9_-]+)/i;
          var match=type.match(declaration)||ascii.match(declaration);
          var encodings=[match?match[1]:null,'utf-8',hint,'gb18030'].filter(Boolean);
          if(!match||/^gb/i.test(match[1]))encodings.unshift('utf-8');
          if(bytes[0]===255&&bytes[1]===254)encodings.unshift('utf-16le');
          if(bytes[0]===254&&bytes[1]===255)encodings.unshift('utf-16be');
          if(bytes[0]===239&&bytes[1]===187&&bytes[2]===191)encodings.unshift('utf-8');
          for(var i=0;i<encodings.length;i++){
            try{return new TextDecoder(encodings[i],{fatal:true}).decode(bytes);}catch(e){}
          }
          throw new Error('Unrecognized chapter encoding');
        });}
    """.trimIndent()
}
