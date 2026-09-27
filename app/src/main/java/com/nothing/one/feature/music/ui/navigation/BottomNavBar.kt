package com.nothing.one.feature.music.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nothing.one.feature.music.ui.theme.DotMatrixFont

enum class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Library("library", "Library", Icons.Outlined.LibraryMusic),
    Search("search", "Search", Icons.Outlined.Search),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

@Composable
fun NothingBottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTab.entries.forEach { tab ->
            val isSelected = currentRoute == tab.route
            NavItem(
                label = tab.label,
                icon = tab.icon,
                isSelected = isSelected,
                onClick = { onNavigate(tab.route) },
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
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Active tab gets a red dot before the icon (NothingOS glyph signature)
        if (isSelected) {
            Dot(
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = DotMatrixFont),
            color = if (isSelected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun Dot(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .padding(2.dp)
            .clip(CircleShape)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(2.dp)
        ) {
        }
    }
}