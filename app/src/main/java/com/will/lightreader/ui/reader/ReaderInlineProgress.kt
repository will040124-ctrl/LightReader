@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.will.lightreader.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal val LocalCompactReaderFooter=staticCompositionLocalOf { false }

@Composable
internal fun ReaderViewport(modifier:Modifier,content:@Composable BoxScope.()->Unit) {
    BoxWithConstraints(modifier) {
        val compact=maxHeight<480.dp && maxWidth>maxHeight
        CompositionLocalProvider(LocalCompactReaderFooter provides compact) {
            Box(Modifier.fillMaxSize(),content=content)
        }
    }
}

@Composable
internal fun readerFooterPadding():androidx.compose.ui.unit.Dp {
    val density=LocalDensity.current
    val navigation=WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
    return readerFooterClearanceDp(density.fontScale,with(density) { navigation.toDp().value },LocalCompactReaderFooter.current).dp
}

/** Drag previews are local; page navigation happens once, when the user releases. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderInlineProgress(key:String,title:String,current:Int,total:Int,enabled:Boolean,
    ink:Color,accent:Color,onTitleClick:()->Unit,onSeek:(Int)->Unit) {
    val count=total.coerceAtLeast(1)
    val last=(count-1).coerceAtLeast(1).toFloat()
    var position by remember(key,count) { mutableFloatStateOf(current.coerceIn(0,count-1).toFloat()) }
    var pending by remember(key,count) { mutableStateOf(false) }
    val interaction=remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val actual by rememberUpdatedState(current.coerceIn(0,count-1).toFloat())
    LaunchedEffect(current,enabled) { if(!dragging) { pending=false;position=actual } }
    LaunchedEffect(dragging) { if(!dragging && !pending)position=actual }
    LaunchedEffect(pending) { if(pending) { delay(2000);pending=false;position=actual } }
    val page=position.roundToInt().coerceIn(0,count-1)
    val footerType=MaterialTheme.typography.labelSmall.copy(fontSize=12.sp,lineHeight=17.sp,
        fontWeight=FontWeight.Normal,fontFamily=FontFamily.Serif)
    val finish:()->Unit={
        val target=position.roundToInt().coerceIn(0,count-1);pending=target!=current;onSeek(target)
    }
    val track:@Composable (Modifier)->Unit={ modifier ->
        ReaderProgressTrack(position,last,count,enabled && count>1,ink,accent,interaction,modifier,
            onChange={ pending=false;position=it },onFinish=finish)
    }
    if(LocalCompactReaderFooter.current) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(.3f).clickable(onClickLabel="打开目录",onClick=onTitleClick).padding(start=8.dp,end=8.dp),
                verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(title,maxLines=1,overflow=TextOverflow.Ellipsis,style=footerType,color=ink)
                Text("${page+1} / $count",maxLines=1,overflow=TextOverflow.Ellipsis,
                    style=footerType,color=if(dragging || pending)accent else ink)
            }
            track(Modifier.weight(.7f).height(48.dp))
        }
    } else {
        Row(Modifier.fillMaxWidth().heightIn(min=24.dp).clickable(onClickLabel="打开目录",onClick=onTitleClick).padding(horizontal=8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Text(title,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,
                style=footerType,color=ink)
            Spacer(Modifier.width(16.dp))
            Text("${page+1} / $count",style=footerType,color=if(dragging || pending)accent else ink)
        }
        track(Modifier.fillMaxWidth().height(48.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderProgressTrack(position:Float,last:Float,total:Int,enabled:Boolean,ink:Color,accent:Color,
    interaction:MutableInteractionSource,modifier:Modifier,onChange:(Float)->Unit,onFinish:()->Unit,
    steps:Int=0, label:String="阅读进度", valueDescription:String="第 ${position.roundToInt()+1} 页，共 ${total} 页") {
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val dragging by interaction.collectIsDraggedAsState()
    val markerHeight by animateFloatAsState(if(dragging)14f else 9f,
        tween(if(com.will.lightreader.ui.theme.Motion.reducedMotion())0 else 120),label="ink-marker")
    val slider=remember(last,steps) { SliderState(position,steps,0f..last) }
    SideEffect { slider.value=position }
    Slider(state=slider,onValueChange={ onChange(it);slider.value=it },onValueChangeFinished=onFinish,
        enabled=enabled,interactionSource=interaction,
        modifier=modifier.semantics {
            contentDescription=label;stateDescription=valueDescription
        },
        thumb={ Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
            Canvas(Modifier.width(12.dp).height(20.dp)) {
                val pigment=if(enabled)accent else ink.copy(alpha=.3f)
                val h=markerHeight.dp.toPx();val x=size.width/2;val y=size.height/2
                drawLine(pigment.copy(alpha=.1f),Offset(x-1f,y-h/2),Offset(x+1f,y+h/2),7.dp.toPx(),StrokeCap.Round)
                drawLine(pigment.copy(alpha=.85f),Offset(x-.4f,y-h/2),Offset(x+.4f,y+h/2),2.5.dp.toPx(),StrokeCap.Round)
            }
        } },
        track={ state -> Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val start=Offset(if(rtl)size.width else 0f,size.height/2f)
            val end=Offset(if(rtl)0f else size.width,size.height/2f)
            drawLine(ink.copy(alpha=.12f),start,end,1.dp.toPx(),StrokeCap.Round)
            val f=(state.value/last).coerceIn(0f,1f)
            val tip=Offset(if(rtl)size.width*(1f-f) else size.width*f,size.height/2f)
            drawLine(accent.copy(alpha=if(enabled).08f else .03f),start,tip,4.dp.toPx(),StrokeCap.Round)
            drawLine(accent.copy(alpha=if(enabled).6f else .2f),start,tip,1.2.dp.toPx(),StrokeCap.Round)
        } },colors=SliderDefaults.colors(thumbColor=accent,activeTrackColor=accent))
}

/** Preview locally during dragging; commit typography once, on release. */
@Composable
internal fun ReaderPreferenceSlider(label:String,value:Float,range:ClosedFloatingPointRange<Float>,
    steps:Int,ink:Color,accent:Color,showLabel:Boolean=true,
    format:(Float)->String,onCommit:(Float)->Unit) {
    var preview by remember(label,range) { mutableFloatStateOf(value.coerceIn(range)) }
    val interaction=remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    LaunchedEffect(value,dragging) { if(!dragging)preview=value.coerceIn(range) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            if(showLabel)Text(label,style=MaterialTheme.typography.bodyLarge,color=ink)
            Spacer(Modifier.weight(1f))
            Text(format(preview),style=MaterialTheme.typography.titleMedium,color=if(dragging)accent else ink)
        }
        ReaderProgressTrack(preview-range.start,range.endInclusive-range.start,steps+2,true,ink,accent,
            interaction,Modifier.fillMaxWidth().height(48.dp),
            onChange={preview=(it+range.start).coerceIn(range)},onFinish={onCommit(preview)},
            steps=steps,label=label,valueDescription=format(preview))
    }
}
