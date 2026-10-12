package com.kotosay.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BarBackground = Color(0xFF0D1117)
private val Hairline = Color.White.copy(alpha = 0.06f)
private val Active = Color.White
private val Inactive = Color(0xFF6E7681)

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Sign → Voice", Icons.Outlined.Videocam),
    Tab("Text → Sign", Icons.Outlined.AccessibilityNew),
)

/**
 * The app's two-tab switcher. Deliberately quiet: no pill, no weight change —
 * the active tab is simply white with a short line above it, the other grey.
 */
@Composable
fun BottomTabBar(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BarBackground)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = Hairline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            TABS.forEachIndexed { index, tab ->
                TabItem(
                    tab = tab,
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val color by animateColorAsState(
        targetValue = if (selected) Active else Inactive,
        animationSpec = tween(180),
        label = "tabColor",
    )
    val lineColor by animateColorAsState(
        targetValue = if (selected) Active else Color.Transparent,
        animationSpec = tween(180),
        label = "tabLine",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(20.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(lineColor)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = tab.label,
                color = color,
                fontSize = 11.sp,
                fontFamily = googleSansFlex,
                letterSpacing = 0.2.sp,
            )
        }
    }
}
