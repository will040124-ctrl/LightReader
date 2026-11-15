package com.will.lightreader.source.net

import com.will.lightreader.source.SourceError
import java.io.IOException

/** Validate transport/interstitial responses before any book-specific parser sees them. */
internal object PageResponseError {
    fun detect(url: String, html: String, status: Int = 200): SourceError? {
        val head = html.take(32_768).lowercase()
        val title = Regex("<title\\b[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            .find(head)?.groupValues?.get(1).orEmpty()
        val cloudflare = "cloudflare" in title || "id=\"cf-error-details\"" in head || "id='cf-error-details'" in head
        val blocked = cloudflare && ("sorry, you have been blocked" in head ||
            "error 1020" in head || "attention required" in title && "you are unable to access" in head)
        if (blocked) return SourceError.ChallengeRequired(url, accessBlocked = true)
        if (status == 401) return SourceError.AuthRequired(url)
        if (status == 429) return SourceError.RateLimited(url)
        if (status == 403 || "just a moment" in title || cloudflare && "cf-chl" in head) {
            return SourceError.ChallengeRequired(url)
        }
        return if (status >= 400) SourceError.Network(IOException("HTTP $status")) else null
    }
}
