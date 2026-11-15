package com.will.lightreader.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Keep native settling/threshold behavior, but block residual overscroll from ineligible drags. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardedPullToRefresh(isRefreshing: Boolean, onRefresh: () -> Unit, atTop: () -> Boolean,
    modifier: Modifier = Modifier, blocked: Boolean = false, resetKey: Any? = Unit,
    content: @Composable BoxScope.() -> Unit) {
    key(resetKey) {
        val state = rememberPullToRefreshState()
        val gate = remember { RefreshGestureGate() }
        val currentTop by rememberUpdatedState(atTop)
        val currentBusy by rememberUpdatedState(blocked || isRefreshing)
        val currentRefresh by rememberUpdatedState(onRefresh)
        val connection = remember(gate) {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    // Child already received its scrolling delta. Consume only leftover downward
                    // motion here so the outer refresh node cannot take over half way to the top.
                    return if (source == NestedScrollSource.UserInput && available.y > 0f &&
                        !gate.accepts(currentTop(), currentBusy)) Offset(0f, available.y) else Offset.Zero
                }
            }
        }
        PullToRefreshBox(isRefreshing = isRefreshing,
            onRefresh = { if (gate.accepts(currentTop(), currentBusy)) currentRefresh() }, state = state,
            modifier = modifier.pointerInput(gate) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    gate.start(currentTop(), currentBusy)
                    var previous = down.position
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.position - previous
                        gate.move(delta.x, delta.y, viewConfiguration.touchSlop)
                        previous = change.position
                    } while (event.changes.any { it.pressed })
                    // Keep the decision until native onPreFling/onRelease runs after pointer up.
                }
            },
            indicator = {
                // Observe distance even while hidden: the gate is a plain gesture latch,
                // so short-circuiting this state read would prevent the indicator updating.
                val distance = state.distanceFraction
                if (isRefreshing || (gate.allowed && !blocked && distance >= .45f)) {
                    PullToRefreshDefaults.Indicator(state = state, isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter).semantics { contentDescription = "下拉刷新" })
                }
            }) {
            Box(Modifier.fillMaxSize().nestedScroll(connection), content = content)
        }
    }
}
