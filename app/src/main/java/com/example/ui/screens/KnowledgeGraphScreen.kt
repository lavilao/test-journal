package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GraphEdge
import com.example.data.model.GraphNode
import com.example.data.model.GraphNodeType
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.AmberNode
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TerracottaAccent
import com.example.ui.theme.tabular
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private enum class GraphNodeFilter(val label: String) {
    ALL("Todos"),
    ENTRY("Memorias"),
    PERSON("Personas"),
    PLACE("Lugares"),
    TOPIC("Temas")
}

/**
 * Force-directed, animated knowledge graph: nodes repel, links act as springs,
 * and the whole layout settles organically before cooling down to 0 FPS overhead.
 */
@Composable
fun KnowledgeGraphScreen(
    viewModel: JournalViewModel,
    onNavigateToEntry: (Long) -> Unit,
    onNavigateToEntity: (Long) -> Unit
) {
    val graphData by viewModel.graphData.collectAsState()
    val isLoading by viewModel.isGraphLoading.collectAsState()

    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    var activeFilter by remember { mutableStateOf(GraphNodeFilter.ALL) }

    // Canvas transformation state (pan & pinch-to-zoom)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.25f, 3.5f)
        offset += panChange
    }

    val textMeasurer = rememberTextMeasurer()
    val labelCache = remember { mutableMapOf<Pair<String, Color>, androidx.compose.ui.text.TextLayoutResult>() }
    val positionTick = remember { mutableIntStateOf(0) }
    var simulation by remember { mutableStateOf<ForceSimulation?>(null) }
    var canvasSize by remember { mutableStateOf(Offset(1080f, 1920f)) }
    var isDraggingNode by remember { mutableStateOf(false) }

    val labelTextColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val entryNodeColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(graphData) {
        if (graphData.nodes.isNotEmpty()) {
            simulation = ForceSimulation(graphData.nodes, graphData.edges).also { sim ->
                sim.initialize(canvasSize.x, canvasSize.y)
            }
        } else {
            simulation = null
        }
    }

    LaunchedEffect(simulation, canvasSize) {
        val sim = simulation ?: return@LaunchedEffect
        while (true) {
            if (sim.isActive()) {
                withFrameNanos { }
                sim.step(canvasSize.x, canvasSize.y)
                positionTick.intValue++
            } else {
                delay(120)
                if (isDraggingNode || sim.wasDisturbed()) {
                    sim.reheat(0.35f)
                }
            }
        }
    }

    fun matchesFilter(node: GraphNode): Boolean = when (activeFilter) {
        GraphNodeFilter.ALL -> true
        GraphNodeFilter.ENTRY -> node.type == GraphNodeType.ENTRY
        GraphNodeFilter.PERSON -> node.type == GraphNodeType.PERSON
        GraphNodeFilter.PLACE -> node.type == GraphNodeType.PLACE || node.type == GraphNodeType.EVENT
        GraphNodeFilter.TOPIC -> node.type == GraphNodeType.TOPIC ||
            node.type == GraphNodeType.TAG ||
            node.type == GraphNodeType.PROJECT ||
            node.type == GraphNodeType.ORGANIZATION
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    canvasSize = Offset(size.width.toFloat(), size.height.toFloat())
                }
            }
            .testTag("knowledge_graph_canvas_container")
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primaryColor)
            }
        } else if (graphData.nodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                LiquidGlassCard(cornerRadius = 24.dp, contentPadding = 24.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = primaryColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tu grafo está vacío",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Guarda memorias para generar tu grafo semántico privado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            val currentSim = simulation
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
                    .pointerInput(currentSim) {
                        detectTapGestures { tapOffset ->
                            val sim = currentSim ?: return@detectTapGestures
                            val graphX = (tapOffset.x - offset.x) / scale
                            val graphY = (tapOffset.y - offset.y) / scale
                            selectedNode = sim.hitTest(graphX, graphY)
                        }
                    }
                    .pointerInput(currentSim) {
                        detectDragGestures(
                            onDragStart = { dragStart ->
                                val sim = currentSim ?: return@detectDragGestures
                                val graphX = (dragStart.x - offset.x) / scale
                                val graphY = (dragStart.y - offset.y) / scale
                                val hit = sim.hitTest(graphX, graphY)
                                if (hit != null) {
                                    sim.beginDrag(hit, graphX, graphY)
                                    isDraggingNode = true
                                }
                            },
                            onDrag = { change, _ ->
                                val sim = currentSim ?: return@detectDragGestures
                                if (isDraggingNode) {
                                    val graphX = (change.position.x - offset.x) / scale
                                    val graphY = (change.position.y - offset.y) / scale
                                    sim.dragTo(graphX, graphY)
                                    positionTick.intValue++
                                } else {
                                    offset += change.position - change.previousPosition
                                }
                            },
                            onDragEnd = {
                                currentSim?.endDrag()
                                isDraggingNode = false
                            },
                            onDragCancel = {
                                currentSim?.endDrag()
                                isDraggingNode = false
                            }
                        )
                    }
            ) {
                positionTick.intValue

                val sim = currentSim ?: return@Canvas
                val nodes = sim.nodes
                val nodeById = nodes.associateBy { it.id }

                // 1. Edges as organic bezier curves
                sim.edges.forEach { edge ->
                    val src = nodeById[edge.sourceId]
                    val tgt = nodeById[edge.targetId]
                    if (src != null && tgt != null) {
                        val isHighlighted = selectedNode != null &&
                            (selectedNode?.id == edge.sourceId || selectedNode?.id == edge.targetId)
                        val passesFilter = matchesFilter(src) || matchesFilter(tgt)
                        val alpha = when {
                            selectedNode != null -> if (isHighlighted) 0.9f else 0.08f
                            !passesFilter -> 0.08f
                            else -> 0.34f
                        }
                        val strokeWidth = if (isHighlighted) 3.5f else 1.4f
                        val midX = (src.x + tgt.x) / 2f
                        val midY = (src.y + tgt.y) / 2f
                        val dx = tgt.x - src.x
                        val dy = tgt.y - src.y
                        val len = max(1f, sqrt(dx * dx + dy * dy))
                        val curve = min(0.12f * len, 26f)
                        val ctrlX = midX - (dy / len) * curve
                        val ctrlY = midY + (dx / len) * curve
                        drawPath(
                            path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(src.x, src.y)
                                quadraticBezierTo(ctrlX, ctrlY, tgt.x, tgt.y)
                            },
                            color = if (isHighlighted) AmberNode else Color(0xFF8A8071),
                            alpha = alpha,
                            style = Stroke(width = strokeWidth)
                        )
                    }
                }

                // 2. Nodes & Dark/Light-safe labels
                val showLabels = scale > 0.55f
                nodes.forEach { node ->
                    val isSelected = selectedNode?.id == node.id
                    val isConnectedToSelected = selectedNode != null && sim.edges.any {
                        (it.sourceId == selectedNode?.id && it.targetId == node.id) ||
                            (it.targetId == selectedNode?.id && it.sourceId == node.id)
                    }
                    val passesFilter = matchesFilter(node)

                    val nodeAlpha = when {
                        selectedNode != null -> if (isSelected || isConnectedToSelected) 1f else 0.18f
                        !passesFilter -> 0.16f
                        else -> 0.92f
                    }
                    val nodeColor = nodeColorFor(node.type, entryNodeColor)
                    val radius = if (isSelected) node.size + 6f else node.size

                    if (isSelected || isConnectedToSelected) {
                        drawCircle(
                            color = nodeColor.copy(alpha = 0.22f),
                            radius = radius + 14f,
                            center = Offset(node.x, node.y)
                        )
                    }

                    drawCircle(
                        color = nodeColor.copy(alpha = nodeAlpha),
                        radius = radius,
                        center = Offset(node.x, node.y)
                    )

                    drawCircle(
                        color = Color.White.copy(alpha = if (nodeAlpha > 0.5f) 0.85f else 0.25f),
                        radius = radius,
                        center = Offset(node.x, node.y),
                        style = Stroke(width = 2f)
                    )

                    if (showLabels && (selectedNode != null || passesFilter)) {
                        val textLayout = labelCache.getOrPut(node.id to labelTextColor) {
                            textMeasurer.measure(
                                text = node.label.take(18) + (if (node.label.length > 18) "…" else ""),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = labelTextColor
                                )
                            )
                        }
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(node.x - (textLayout.size.width / 2f), node.y + radius + 4f),
                            alpha = when {
                                selectedNode != null -> if (isSelected || isConnectedToSelected) 1f else 0.16f
                                else -> 0.92f
                            }
                        )
                    }
                }
            }
        }

        // Top floating Liquid Glass bar & interactive color legend/filter
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Hub, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Grafo semántico",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = primaryColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "${graphData.nodes.size} nodos",
                                style = MaterialTheme.typography.labelSmall.tabular.copy(fontWeight = FontWeight.SemiBold),
                                color = primaryColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                            viewModel.refreshGraph()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("refresh_graph_button")
                    ) {
                        Icon(
                            Icons.Default.CenterFocusStrong,
                            contentDescription = "Centrar grafo",
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Legend + interactive node-type filter strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GraphNodeFilter.entries.forEach { filter ->
                    val selected = activeFilter == filter
                    val dotColor = when (filter) {
                        GraphNodeFilter.ALL -> null
                        GraphNodeFilter.ENTRY -> entryNodeColor
                        GraphNodeFilter.PERSON -> ForestPrimary
                        GraphNodeFilter.PLACE -> TerracottaAccent
                        GraphNodeFilter.TOPIC -> AmberNode
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) {
                            primaryColor.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) primaryColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { activeFilter = filter }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (dotColor != null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(dotColor, CircleShape)
                                )
                            }
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) primaryColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Selected node inspector (bottom, clearing dock & nav bar)
        selectedNode?.let { node ->
            LiquidGlassCard(
                cornerRadius = 22.dp,
                contentPadding = 16.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 92.dp)
                    .testTag("graph_inspector_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = nodeColorFor(node.type, entryNodeColor),
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
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = typeLabel(node.type),
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
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_graph_node_button")
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (node.type == GraphNodeType.ENTRY) "Leer memoria" else "Explorar entidad")
                }
            }
        }
    }
}

private fun nodeColorFor(type: GraphNodeType, entryColor: Color): Color = when (type) {
    GraphNodeType.ENTRY -> entryColor
    GraphNodeType.PERSON -> ForestPrimary
    GraphNodeType.PLACE -> TerracottaAccent
    GraphNodeType.TOPIC, GraphNodeType.TAG -> AmberNode
    GraphNodeType.ORGANIZATION -> Color(0xFF4338CA)
    GraphNodeType.EVENT -> TerracottaAccent
    GraphNodeType.PROJECT -> Color(0xFF6D28D9)
}

private fun typeLabel(type: GraphNodeType): String = when (type) {
    GraphNodeType.ENTRY -> "Memoria"
    GraphNodeType.PERSON -> "Persona"
    GraphNodeType.PLACE -> "Lugar"
    GraphNodeType.TOPIC -> "Tema"
    GraphNodeType.TAG -> "Etiqueta"
    GraphNodeType.ORGANIZATION -> "Organización"
    GraphNodeType.EVENT -> "Evento"
    GraphNodeType.PROJECT -> "Proyecto"
}

class ForceSimulation(
    graphNodes: List<GraphNode>,
    graphEdges: List<GraphEdge>
) {
    val nodes: List<GraphNode> = graphNodes.take(MAX_NODES)
    val edges: List<GraphEdge> = graphEdges.filter { edge ->
        nodes.any { it.id == edge.sourceId } && nodes.any { it.id == edge.targetId }
    }

    private val nodeIndex = nodes.associateBy { it.id }
    private var draggedNode: GraphNode? = null
    private var disturbed = false
    private var alpha = 1.0f

    fun initialize(width: Float, height: Float) {
        val n = nodes.size
        val radius = min(width, height) * 0.35f
        val cx = width / 2f
        val cy = height / 2f
        nodes.forEachIndexed { index, node ->
            val angle = (2.0 * Math.PI * index / n).toFloat()
            node.x = cx + radius * kotlin.math.cos(angle)
            node.y = cy + radius * kotlin.math.sin(angle)
            node.vx = 0f
            node.vy = 0f
        }
        alpha = 1f
        disturbed = false
    }

    fun isActive(): Boolean = alpha > ALPHA_MIN && nodes.isNotEmpty()

    fun reheat(energy: Float = 0.35f) {
        alpha = alpha.coerceAtLeast(energy)
        disturbed = false
    }

    fun markDisturbed() {
        disturbed = true
    }

    fun wasDisturbed(): Boolean {
        val value = disturbed
        disturbed = false
        return value
    }

    fun hitTest(graphX: Float, graphY: Float): GraphNode? {
        var best: GraphNode? = null
        var bestDist = Float.MAX_VALUE
        nodes.forEach { node ->
            val d = kotlin.math.hypot(node.x - graphX, node.y - graphY)
            if (d <= node.size + 24f && d < bestDist) {
                best = node
                bestDist = d
            }
        }
        return best
    }

    fun beginDrag(node: GraphNode, x: Float, y: Float) {
        draggedNode = node
        node.x = x
        node.y = y
        node.vx = 0f
        node.vy = 0f
    }

    fun dragTo(x: Float, y: Float) {
        draggedNode?.let { node ->
            node.x = x
            node.y = y
            node.vx = 0f
            node.vy = 0f
        }
    }

    fun endDrag() {
        draggedNode = null
        reheat(0.3f)
    }

    fun step(width: Float, height: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val heat = alpha

        val n = nodes.size
        for (i in 0 until n) {
            val a = nodes[i]
            for (j in i + 1 until n) {
                val b = nodes[j]
                var dx = a.x - b.x
                var dy = a.y - b.y
                var distSq = dx * dx + dy * dy
                if (distSq < 1f) {
                    dx = ((i % 7) - 3) * 2f + 0.5f
                    dy = ((j % 5) - 2) * 2f + 0.5f
                    distSq = dx * dx + dy * dy
                }
                val dist = sqrt(distSq)
                val minDist = a.size + b.size + 34f
                if (dist < minDist * 4.5f) {
                    var force = REPULSION / distSq * heat
                    if (dist < minDist) force *= 3.2f
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force
                    if (a !== draggedNode) {
                        a.vx += fx
                        a.vy += fy
                    }
                    if (b !== draggedNode) {
                        b.vx -= fx
                        b.vy -= fy
                    }
                }
            }
        }

        edges.forEach { edge ->
            val src = nodeIndex[edge.sourceId] ?: return@forEach
            val tgt = nodeIndex[edge.targetId] ?: return@forEach
            val dx = tgt.x - src.x
            val dy = tgt.y - src.y
            val dist = max(1f, sqrt(dx * dx + dy * dy))
            val target = 150f
            val force = SPRING * (dist - target) * heat
            val fx = (dx / dist) * force
            val fy = (dy / dist) * force
            if (src !== draggedNode) {
                src.vx += fx
                src.vy += fy
            }
            if (tgt !== draggedNode) {
                tgt.vx -= fx
                tgt.vy -= fy
            }
        }

        nodes.forEach { node ->
            if (node !== draggedNode) {
                node.vx += (cx - node.x) * CENTER_PULL * heat
                node.vy += (cy - node.y) * CENTER_PULL * heat
                node.vx *= DAMPING
                node.vy *= DAMPING
                node.vx = node.vx.coerceIn(-MAX_SPEED, MAX_SPEED)
                node.vy = node.vy.coerceIn(-MAX_SPEED, MAX_SPEED)
                node.x += node.vx
                node.y += node.vy
            }
        }

        alpha *= ALPHA_DECAY
        if (alpha <= ALPHA_MIN) {
            alpha = 0f
            freeze()
        }
    }

    private fun freeze() {
        nodes.forEach { node ->
            node.vx = 0f
            node.vy = 0f
        }
    }

    companion object {
        private const val MAX_NODES = 150
        private const val REPULSION = 260_000f
        private const val SPRING = 0.012f
        private const val CENTER_PULL = 0.0016f
        private const val DAMPING = 0.86f
        private const val MAX_SPEED = 14f
        private const val ALPHA_DECAY = 0.985f
        private const val ALPHA_MIN = 0.005f
    }
}
