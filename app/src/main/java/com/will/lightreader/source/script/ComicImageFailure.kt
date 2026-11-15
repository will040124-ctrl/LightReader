package com.will.lightreader.source.script
/** Images own their bounded retries; chapter retries must not multiply them. */
internal class ComicImageFailure(message:String,val transient:Boolean,cause:Throwable?=null):java.io.IOException(message,cause)
