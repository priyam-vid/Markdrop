package com.markdrop.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markdrop.app.util.HapticHelper
import com.markdrop.app.util.HapticType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials

@Composable
fun BottomNavBar(
    destinations: List<NavDestination>,
    currentDestination: NavDestination?,
    onNavigate: (NavDestination) -> Unit,
    hazeState: HazeState,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp)
            .height(72.dp),
        contentAlignment = Alignment.Center
    ) {
        val glassShape = RoundedCornerShape(28.dp)
        
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .hazeEffect(
                    state = hazeState,
                    style = HazeMaterials.ultraThin()
                ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = glassShape,
            shadowElevation = 0.dp
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val itemWidth = maxWidth / destinations.size
                
                val selectedIndex = destinations.indexOf(currentDestination).takeIf { it >= 0 } ?: 0
                val indicatorOffset by animateDpAsState(
                    targetValue = itemWidth * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = 0.65f,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "indicatorOffset"
                )
                
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                )
                
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEach { destination ->
                        val selected = destination == currentDestination
                        
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.2f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "iconScale"
                        )
                        
                        val iconOffset by animateDpAsState(
                            targetValue = if (selected) (-4).dp else 0.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "iconOffset"
                        )
                        
                        val tintColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "tintColor"
                        )

                        Column(
                            modifier = Modifier
                                .width(itemWidth)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (!selected) {
                                        HapticHelper.performHaptic(view, HapticType.LIGHT, hapticsEnabled)
                                        onNavigate(destination)
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                                modifier = Modifier
                                    .size(24.dp)
                                    .offset(y = iconOffset)
                                    .scale(iconScale),
                                tint = tintColor
                            )
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tintColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
