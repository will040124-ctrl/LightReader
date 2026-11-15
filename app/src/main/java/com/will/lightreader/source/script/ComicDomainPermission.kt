package com.will.lightreader.source.script

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Wildcards are explicit grants, never inferred from a response or from an exact host grant. */
fun validComicDomainPermission(domain:String):Boolean {
    val base=domain.removePrefix("*.")
    if(domain!=domain.lowercase() || base.length>253 || base.contains("..") ||
        !base.matches(Regex("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")))return false
    if(domain.startsWith("*.")) {
        // Reject public suffixes (including private registries such as github.io) and IPs.
        val parsed=("https://$base/").toHttpUrlOrNull() ?: return false
        if(parsed.topPrivateDomain()==null)return false
    }
    return true
}

fun comicDomainAllowed(host:String,permissions:List<String>):Boolean = permissions.any { permission ->
    validComicDomainPermission(permission) && (host==permission ||
        permission.startsWith("*.") && host.endsWith("."+permission.removePrefix("*.")))
}
