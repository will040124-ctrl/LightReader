package com.will.lightreader.ui.book

import com.will.lightreader.source.SourceError

/** A primed cover is not a usable local directory and must not hide a load failure. */
internal fun bookLoadFailure(state:BookState,hasLocalDirectory:Boolean,error:SourceError):BookState =
    if(!hasLocalDirectory)state.copy(loading=false,refreshing=false,error=error)
    else state.copy(loading=false,refreshing=false,offline=true,error=null,
        refreshMessage="更新未完成：${error.message?.take(200).orEmpty()}；已保留本地目录")
