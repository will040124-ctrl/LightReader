package com.will.lightreader.source.script
import kotlinx.serialization.json.*
/** Exact program recognition only; other programs still run in the isolated engine. */
internal object JmImagePlan {
    private const val template = "letmodifyImage=(image)=>{constnum=#letblockSize=Math.floor(image.height/num)letremainder=image.height%numletblocks=[]for(leti=0;i<num;i++){letstart=i*blockSizeletend=start+blockSize+(i!==num-1?0:remainder)blocks.push({start:start,end:end})}letres=Image.empty(image.width,image.height)lety=0for(leti=blocks.length-1;i>=0;i--){letblock=blocks[i]letcurrentHeight=block.end-block.startres.fillImageRangeAt(0,y,image,0,block.start,image.width,currentHeight)y+=currentHeight}returnres}"
    fun resolve(script:String,width:Int,height:Int):JsonObject? {
        val compact=script.filterNot(Char::isWhitespace)
        val match=Regex("constnum=([0-9]{1,2})letblockSize").find(compact) ?: return null
        val num=match.groupValues[1].toInt()
        if(num !in 2..20 || num%2!=0 || height<num || width<=0) return null
        if(compact.replaceRange(match.range,"constnum=#letblockSize")!=template) return null
        val size=height/num;val remainder=height%num;var y=0
        return buildJsonObject {
            put("width",width);put("height",height)
            put("copies",buildJsonArray { for(i in num-1 downTo 0) {
                val h=size+if(i==num-1)remainder else 0
                add(buildJsonArray { listOf(0,y,0,i*size,width,h).forEach { add(it) } });y+=h
            } })
        }
    }
}
