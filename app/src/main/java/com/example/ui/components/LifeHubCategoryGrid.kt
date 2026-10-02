package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.SageAccent
import com.example.ui.theme.WarmAccent

data class LifeHubCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val count: Int
)

@Composable
fun LifeHubCategoryGrid(
    personalCount: Int,
    healthCount: Int,
    financeCount: Int,
    projectsCount: Int,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        LifeHubCategory(
            id = "Personal",
            title = "Personal",
            subtitle = "Memories & mood",
            icon = Icons.Default.Favorite,
            accentColor = ForestPrimary,
            count = personalCount
        ),
        LifeHubCategory(
            id = "Health",
            title = "Health",
            subtitle = "Steps & wellbeing",
            icon = Icons.Default.FitnessCenter,
            accentColor = WarmAccent,
            count = healthCount
        ),
        LifeHubCategory(
            id = "Finance",
            title = "Finance & Legal",
            subtitle = "Docs & records",
            icon = Icons.Default.AccountBalance,
            accentColor = Color(0xFF386663),
            count = financeCount
        ),
        LifeHubCategory(
            id = "Projects",
            title = "Home & Projects",
            subtitle = "Plans & tasks",
            icon = Icons.Default.HomeWork,
            accentColor = SageAccent,
            count = projectsCount
        )
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LifeHubCategoryCard(
                category = categories[0],
                onClick = { onSelectCategory(categories[0].id) },
                modifier = Modifier.weight(1f)
            )
            LifeHubCategoryCard(
                category = categories[1],
                onClick = { onSelectCategory(categories[1].id) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LifeHubCategoryCard(
                category = categories[2],
                onClick = { onSelectCategory(categories[2].id) },
                modifier = Modifier.weight(1f)
            )
            LifeHubCategoryCard(
                category = categories[3],
                onClick = { onSelectCategory(categories[3].id) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LifeHubCategoryCard(
    category: LifeHubCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("category_card_${category.id.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = category.accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = category.title,
                            tint = category.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "${category.count}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = category.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Text(
                text = category.subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
