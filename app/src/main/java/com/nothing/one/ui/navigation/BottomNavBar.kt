package com.nothing.one.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nothing.one.ui.components.DotMatrixText
import com.nothing.one.ui.components.RedDot
import com.nothing.one.ui.theme.NothingRed

enum class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Outlined.Apps),
    MUSIC("music", "Music", Icons.Outlined.LibraryMusic),
    NOTES("notes", "Notes", Icons.Outlined.Apps),
    JOURNAL("journal", "Journal", Icons.AutoMirrored.Outlined.MenuBook),
    ASSISTANT("assistant", "Assistant", Icons.AutoMirrored.Outlined.Chat),
    FOCUS("focus", "Focus", Icons.Outlined.Timer),
}

@Composable
fun NothingOneBottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Weighted slots so all six tabs fit on the narrowest phones — a plain
    // SpaceEvenly row clips the last tabs off-screen at 411dp.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTab.entries.forEach { tab ->
            val isSelected = currentRoute == tab.route
            NavItem(
                label = tab.label,
                icon = tab.icon,
                isSelected = isSelected,
                onClick = { onNavigate(tab.route) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Active tab gets a red dot before the label (NothingOS signature)
            if (isSelected) {
                RedDot(modifier = Modifier.padding(end = 3.dp), size = 4.dp)
            }
            DotMatrixText(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
