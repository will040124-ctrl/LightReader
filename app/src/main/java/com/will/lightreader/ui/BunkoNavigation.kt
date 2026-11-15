package com.will.lightreader.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.will.lightreader.ui.components.watercolorWash
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.will.lightreader.ui.components.BookmarkNotch
import com.will.lightreader.ui.components.BunkoSeal
import com.will.lightreader.ui.components.pressScale
import com.will.lightreader.ui.theme.AppIcons
import com.will.lightreader.ui.theme.M3E
import com.will.lightreader.R

@Composable
fun BunkoNavigation(selected: HomeTab, rail: Boolean, onSelect: (HomeTab) -> Unit) {
    val items: @Composable () -> Unit = {
        HomeTab.entries.forEach { tab ->
            val active = tab == selected
            val tint by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                M3E.effectsSpec(), label = "navigation-ink",
            )
            val selectedWash by animateFloatAsState(if (active) 1f else 0f, M3E.effectsSpec(), label = "navigation-wash")
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier.width(if (rail) 68.dp else 88.dp).heightIn(min = 64.dp)
                    .pressScale(interaction, .985f)
                    .selectable(active, role = Role.Tab, interactionSource = interaction, indication = null) { onSelect(tab) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.matchParentSize().padding(horizontal = 5.dp, vertical = 7.dp)
                    .graphicsLayer { alpha = selectedWash }.watercolorWash(MaterialTheme.colorScheme.primary))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
                    Text(tab.label, color = tint, style = MaterialTheme.typography.labelSmall)
                }

            }
        }
    }
    if (rail) {
        Column(Modifier.width(88.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding()
            .selectableGroup().padding(top = 22.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BunkoSeal()
            Spacer(Modifier.height(10.dp))
            Text("轻阅", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(48.dp))
            items()
            Spacer(Modifier.weight(1f))
            com.will.lightreader.ui.components.WatercolorCompanion(modifier = Modifier.size(72.dp))
        }
    } else {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) { items() }
    }
}
