package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.rss.RssArticle
import com.example.ui.theme.ForestPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssOfflineReaderModal(
    article: RssArticle,
    onDismiss: () -> Unit,
    onSaveToNotes: (RssArticle) -> Unit,
    onFetchFullText: (suspend (RssArticle) -> String)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var fullBodyText by remember {
        mutableStateOf(if (article.fullContent.length > article.description.length) article.fullContent else article.description)
    }
    var isLoadingFullText by remember { mutableStateOf(false) }

    LaunchedEffect(article.id) {
        if (onFetchFullText != null && fullBodyText.length < 250) {
            isLoadingFullText = true
            try {
                val fetched = onFetchFullText(article)
                if (fetched.isNotBlank()) {
                    fullBodyText = fetched
                }
            } catch (_: Exception) {}
            isLoadingFullText = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("rss_offline_reader_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ForestPrimary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Artículo Completo",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ForestPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Article Hero Image
            if (!article.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = article.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Publication Source & Date
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = GoogleRed.copy(alpha = 0.15f),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.RssFeed,
                            contentDescription = null,
                            tint = GoogleRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${article.sourceTitle} • ${article.pubDate}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Article Title
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    lineHeight = 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Full Article Body
            if (isLoadingFullText) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ForestPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cargando texto completo del artículo...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                text = fullBodyText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onSaveToNotes(article.copy(fullContent = fullBodyText))
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guardar Artículo en Notas", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "${article.title}\n\n${article.link}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Compartir artículo"))
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.6f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Compartir", fontSize = 13.sp)
                }
            }
        }
    }
}
