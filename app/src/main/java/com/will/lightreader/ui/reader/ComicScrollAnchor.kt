package com.will.lightreader.ui.reader

/** Image-relative offsets survive a different viewport width, unlike raw scroll pixels. */
fun comicScrollFraction(offset:Int,itemSize:Int):Float = if(itemSize<=0)0f else (offset.toFloat()/itemSize).coerceIn(0f,1f)
fun comicScrollOffset(fraction:Float,itemSize:Int):Int =
    if(!fraction.isFinite()||itemSize<=0)0 else (fraction.coerceIn(0f,1f)*itemSize).toInt().coerceAtMost(itemSize-1)

/** Bound Compose item dimensions for unusually tall single-file strips. */
fun comicStripAspect(imageWidth:Int,imageHeight:Int,displayWidth:Int):Float =
    if(imageWidth<=0||imageHeight<=0||displayWidth<=0)2f/3f
    else (imageWidth.toFloat()/imageHeight).coerceIn(displayWidth/200000f,100f)
