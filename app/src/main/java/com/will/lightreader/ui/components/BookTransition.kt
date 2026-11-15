@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.will.lightreader.ui.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NamedNavArgument
import androidx.navigation.compose.composable
import com.will.lightreader.ui.theme.Motion
import kotlinx.coroutines.flow.first

val LocalBookTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }
private val LocalBookVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** One clock for the turn, cover flight, navigation lifetime and return-to-shelf pose. */
object BookCoverMotion {
    const val TurnMs = 260
    const val FlightMs = 720
    // 平缓滑翔曲线（Material standard）：匀速起步、柔和落位，眼睛跟得上。
    // 强减速 (.16,1,.3,1) 前 20% 时间就走完 ~80% 路程，看着像「瞬间冲过去」，太快。
    val FlightEasing = CubicBezierEasing(.4f, 0f, .2f, 1f)
}

@Composable
internal fun rememberBookReturnBarrier(): suspend () -> Unit {
    val shared = LocalBookTransition.current
    val route = LocalBookVisibility.current?.transition
    return remember(shared, route) {
        suspend {
            // Wait until the receiving cover has been measured and the shared flight has registered.
            withFrameNanos { }
            withFrameNanos { }
            snapshotFlow { shared?.isTransitionActive == true || route?.isRunning == true }.first { !it }
        }
    }
}

/** One route scope for the actual jacket, independent of image loading or book data. */
fun NavGraphBuilder.bunkoDestination(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(route = route, arguments = arguments) { entry ->
        val routeScope = this
        CompositionLocalProvider(LocalBookVisibility provides routeScope) { routeScope.content(entry) }
    }
}

@Composable
fun Modifier.bookCoverTransition(key: String?): Modifier {
    val shared = LocalBookTransition.current
    val visible = LocalBookVisibility.current
    if (key == null || shared == null || visible == null || Motion.reducedMotion()) return this
    return with(shared) {
        this@bookCoverTransition.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visible,
            boundsTransform = { _, _ -> tween(BookCoverMotion.FlightMs, easing = BookCoverMotion.FlightEasing) },
        )
    }
}
