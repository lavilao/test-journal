package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GraphNode
import com.example.data.model.GraphNodeType
import com.example.ui.theme.AmberNode
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel

@Composable
fun KnowledgeGraphScreen(
    viewModel: JournalViewModel,
    onNavigateToEntry: (Long) -> Unit,
    onNavigateToEntity: (Long) -> Unit
) {
    val graphData by viewModel.graphData.collectAsState()
    val isLoading by viewModel.isGraphLoading.collectAsState()

    var selectedFilter by remember { mutableStateOf<GraphNodeType?>(null) }
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }

    // Canvas Transformation State (Pan & Pinch-to-zoom)
    var scale by remember { mutableFloatStateOf(0.9f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.4f, 2.5f)
        offset += panChange
    }

    val textMeasurer = rememberTextMeasurer()

    Box(modifier = Modifier.fillMaxSize().testTag("knowledge_graph_canvas_container")) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ForestPrimary)
            }
        } else if (graphData.nodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Knowledge Graph Empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Save journal entries to generate your private semantic graph",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Interactive 2D Graph Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = transformState)
                    .pointerInput(graphData.nodes, scale, offset) {
                        detectTapGestures { tapOffset ->
                            // Convert tap into graph space
                            val graphX = (tapOffset.x - offset.x) / scale
                            val graphY = (tapOffset.y - offset.y) / scale

                            val hit = graphData.nodes.find { node ->
                                val dist = kotlin.math.hypot(node.x - graphX, node.y - graphY)
                                dist <= node.size + 14f
                            }
                            selectedNode = hit
                        }
                    }
            ) {
                val nodesMap = graphData.nodes.associateBy { it.id }

                // 1. Draw Edges
                graphData.edges.forEach { edge ->
                    val src = nodesMap[edge.sourceId]
                    val tgt = nodesMap[edge.targetId]
                    if (src != null && tgt != null) {
                        val isHighlighted = selectedNode != null &&
                                (selectedNode?.id == edge.sourceId || selectedNode?.id == edge.targetId)

                        val strokeWidth = if (isHighlighted) 3.5f else 1.2f
                        val edgeColor = if (isHighlighted) AmberNode else Color(0xFFC7BCAD).copy(alpha = 0.5f)

                        drawLine(
                            color = edgeColor,
                            start = Offset(src.x, src.y),
                            end = Offset(tgt.x, tgt.y),
                            strokeWidth = strokeWidth
                        )
                    }
                }

                // 2. Draw Nodes
                graphData.nodes.forEach { node ->
                    val isSelected = selectedNode?.id == node.id
                    val isConnectedToSelected = selectedNode != null && graphData.edges.any {
                        (it.sourceId == selectedNode?.id && it.targetId == node.id) ||
                                (it.targetId == selectedNode?.id && it.sourceId == node.id)
                    }

                    val nodeColor = when (node.type) {
                        GraphNodeType.ENTRY -> InkPrimary
                        GraphNodeType.PERSON -> ForestPrimary
                        GraphNodeType.PLACE -> TerracottaAccent
                        GraphNodeType.TOPIC, GraphNodeType.TAG -> AmberNode
                        GraphNodeType.ORGANIZATION -> Color(0xFF4338CA)
                        GraphNodeType.EVENT -> TerracottaAccent
                        GraphNodeType.PROJECT -> Color(0xFF6D28D9)
                    }

                    val radius = if (isSelected) node.size + 6f else node.size

                    // Outer pulse/halo for selected
                    if (isSelected) {
                        drawCircle(
                            color = nodeColor.copy(alpha = 0.25f),
                            radius = radius + 12f,
                            center = Offset(node.x, node.y)
                        )
                    }

                    // Node body
                    drawCircle(
                        color = nodeColor,
                        radius = radius,
                        center = Offset(node.x, node.y)
                    )

                    // Node border
                    drawCircle(
                        color = Color.White,
                        radius = radius,
                        center = Offset(node.x, node.y),
                        style = Stroke(width = 2.5f)
                    )

                    // Label
                    val textLayout = textMeasurer.measure(
                        text = node.label.take(18) + (if (node.label.length > 18) "..." else ""),
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected || isConnectedToSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) nodeColor else InkPrimary
                        )
                    )
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(node.x - (textLayout.size.width / 2f), node.y + radius + 4f)
                    )
                }
            }
        }

        // Top Filter Bar & Legend
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Semantic Knowledge Graph",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(
                    onClick = {
                        scale = 0.9f
                        offset = Offset.Zero
                        viewModel.refreshGraph()
                    },
                    modifier = Modifier.size(28.dp).testTag("refresh_graph_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset view", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Legend Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LegendBadge(color = InkPrimary, label = "Entries")
                LegendBadge(color = ForestPrimary, label = "People")
                LegendBadge(color = TerracottaAccent, label = "Places")
                LegendBadge(color = AmberNode, label = "Topics")
                LegendBadge(color = Color(0xFF4338CA), label = "Orgs")
            }
        }

        // Selected Node Inspection Card (Bottom)
        selectedNode?.let { node ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("graph_inspector_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = when (node.type) {
                                    GraphNodeType.ENTRY -> InkPrimary
                                    GraphNodeType.PERSON -> ForestPrimary
                                    GraphNodeType.PLACE -> TerracottaAccent
                                    GraphNodeType.TOPIC, GraphNodeType.TAG -> AmberNode
                                    GraphNodeType.ORGANIZATION -> Color(0xFF4338CA)
                                    GraphNodeType.EVENT -> TerracottaAccent
                                    GraphNodeType.PROJECT -> Color(0xFF6D28D9)
                                },
                                modifier = Modifier.size(14.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = node.label,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { selectedNode = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Type: ${node.type.name} • Connected Associations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (node.type == GraphNodeType.ENTRY) {
                                onNavigateToEntry(node.rawId)
                            } else {
                                onNavigateToEntity(node.rawId)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("open_graph_node_button")
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (node.type == GraphNodeType.ENTRY) "Read Memory" else "Explore Entity Timeline")
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendBadge(color: Color, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier.size(10.dp)
            ) {}
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
