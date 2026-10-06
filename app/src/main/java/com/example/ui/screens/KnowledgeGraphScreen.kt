package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.ui.theme.AmberNode
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.TerracottaAccent
import com.example.viewmodel.JournalViewModel
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Force-directed, animated knowledge graph in the spirit of Obsidian's
 * graph view: nodes repel, links act as springs, and the whole layout
 * settles organically. You can drag nodes, pan and pinch-zoom. The
 * simulation sleeps once it settles to keep the app smooth on low-end
 * hardware.
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

    // Canvas transformation state (pan & pinch-to-zoom)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.25f, 3.5f)
        offset += panChange
    }

    // Simulation state
    val textMeasurer = rememberTextMeasurer()
    val labelCache = remember { mutableMapOf<String, androidx.compose.ui.text.TextLayoutResult>() }
    val positionTick = remember { mutableIntStateOf(0) }
    var simulation by remember { mutableStateOf<ForceSimulation?>(null) }
    var canvasSize by remember { mutableStateOf(Offset(1080f, 1920f)) }
    var isDraggingNode by remember { mutableStateOf(false) }

    // (Re)build the simulation whenever fresh graph data arrives.
    LaunchedEffect(graphData) {
        if (graphData.nodes.isNotEmpty()) {
            simulation = ForceSimulation(graphData.nodes, graphData.edges).also { sim ->
                sim.initialize(canvasSize.x, canvasSize.y)
            }
        } else {
            simulation = null
        }
    }

    // Physics animation loop. Runs while unstable; sleeps when settled.
    LaunchedEffect(simulation, canvasSize) {
        val sim = simulation ?: return@LaunchedEffect
        while (true) {
            if (sim.isActive()) {
                withFrameNanos { }
                sim.step(canvasSize.x, canvasSize.y)
                positionTick.intValue++
            } else {
                // Idle: the simulation has FROZEN (zero velocities). Only a
                // node drag can wake it. No per-frame work happens here, so
                // the CPU stays cold and the battery is happy.
                delay(120)
                if (isDraggingNode || sim.wasDisturbed()) {
                    sim.reheat(0.35f)
                }
            }
        }
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
                        text = "Tu grafo está vacío",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Guarda memorias para generar tu grafo semántico privado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
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
                    .pointerInput(currentSim, scale, offset) {
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
                // Reading this state invalidates the canvas every tick.
                positionTick.intValue

                val sim = currentSim ?: return@Canvas
                val nodes = sim.nodes
                val nodeById = nodes.associateBy { it.id }

                // 1. Edges as soft curves (slight curve looks organic)
                sim.edges.forEach { edge ->
                    val src = nodeById[edge.sourceId]
                    val tgt = nodeById[edge.targetId]
                    if (src != null && tgt != null) {
                        val isHighlighted = selectedNode != null &&
                                (selectedNode?.id == edge.sourceId || selectedNode?.id == edge.targetId)
                        val alpha = if (selectedNode == null) 0.35f else if (isHighlighted) 0.9f else 0.10f
                        val strokeWidth = if (isHighlighted) 3.5f else 1.4f
                        val midX = (src.x + tgt.x) / 2f
                        val midY = (src.y + tgt.y) / 2f
                        // Curve perpendicular for a living, organic feel
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

                // 2. Nodes
                val showLabels = scale > 0.55f
                nodes.forEach { node ->
                    val isSelected = selectedNode?.id == node.id
                    val isConnectedToSelected = selectedNode != null && sim.edges.any {
                        (it.sourceId == selectedNode?.id && it.targetId == node.id) ||
                                (it.targetId == selectedNode?.id && it.sourceId == node.id)
                    }

                    val nodeColor = nodeColorFor(node.type)
                    val radius = if (isSelected) node.size + 6f else node.size

                    // Soft halo
                    if (isSelected || isConnectedToSelected) {
                        drawCircle(
                            color = nodeColor.copy(alpha = 0.18f),
                            radius = radius + 14f,
                            center = Offset(node.x, node.y)
                        )
                    }

                    drawCircle(
                        color = nodeColor.copy(alpha = if (selectedNode == null) 0.9f else if (isSelected || isConnectedToSelected) 1f else 0.25f),
                        radius = radius,
                        center = Offset(node.x, node.y)
                    )

                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = radius,
                        center = Offset(node.x, node.y),
                        style = Stroke(width = 2f)
                    )

                    // 3. Labels (cached — measuring text every frame is expensive)
                    if (showLabels) {
                        val textLayout = labelCache.getOrPut(node.id) {
                            textMeasurer.measure(
                                text = node.label.take(18) + (if (node.label.length > 18) "…" else ""),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = InkPrimary
                                )
                            )
                        }
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(node.x - (textLayout.size.width / 2f), node.y + radius + 4f),
                            alpha = if (selectedNode == null) 0.95f else if (isSelected || isConnectedToSelected) 1f else 0.2f
                        )
                    }
                }
            }
        }

        // Top bar & legend
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Grafo semántico",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${graphData.nodes.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                        viewModel.refreshGraph()
                    },
                    modifier = Modifier.size(28.dp).testTag("refresh_graph_button")
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset view", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Selected node inspector (bottom)
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
                                color = nodeColorFor(node.type),
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
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", modifier = Modifier.size(16.dp))
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
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("open_graph_node_button")
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (node.type == GraphNodeType.ENTRY) "Leer memoria" else "Explorar entidad")
                    }
                }
            }
        }
    }
}

private fun nodeColorFor(type: GraphNodeType): Color = when (type) {
    GraphNodeType.ENTRY -> InkPrimary
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

/**
 * Classic force-directed layout with d3-style "alpha" cooling: every force
 * is scaled by a decaying temperature factor, so the system loses energy
 * on a fixed schedule and FREEZES after a bounded number of steps. This is
 * the fix for the endless-trembling graph that kept the CPU hot: nodes
 * settle once and stay put until the user drags one (which re-heats the
 * simulation to a fraction of the initial energy for a soft re-settle).
 */
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

    /** Simulation "temperature". Decays every step; the sim freezes at 0. */
    private var alpha = 1.0f

    fun initialize(width: Float, height: Float) {
        // Circle initial positions so the forces can breathe.
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

    /** True while the simulation still has temperature and should be stepped. */
    fun isActive(): Boolean = alpha > ALPHA_MIN && nodes.isNotEmpty()

    /**
     * Re-heats the simulation after a disturbance (e.g. a node drag). A
     * partial re-heat settles quickly instead of re-exploding the layout.
     */
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

    /** One physics step. Cools the system by ALPHA_DECAY. */
    fun step(width: Float, height: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val heat = alpha

        // Pairwise repulsion (O(n²), n capped at MAX_NODES)
        val n = nodes.size
        for (i in 0 until n) {
            val a = nodes[i]
            for (j in i + 1 until n) {
                val b = nodes[j]
                var dx = a.x - b.x
                var dy = a.y - b.y
                var distSq = dx * dx + dy * dy
                if (distSq < 1f) {
                    // Nudge overlapping nodes apart deterministically
                    dx = ((i % 7) - 3) * 2f + 0.5f
                    dy = ((j % 5) - 2) * 2f + 0.5f
                    distSq = dx * dx + dy * dy
                }
                val dist = sqrt(distSq)
                val minDist = a.size + b.size + 34f
                if (dist < minDist * 4.5f) {
                    var force = REPULSION / distSq * heat
                    if (dist < minDist) force *= 3.2f // hard separation
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

        // Link springs
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

        // Center gravity + integration + damping
        nodes.forEach { node ->
            if (node !== draggedNode) {
                node.vx += (cx - node.x) * CENTER_PULL * heat
                node.vy += (cy - node.y) * CENTER_PULL * heat
                node.vx *= DAMPING
                node.vy *= DAMPING
                // Clamp per-step movement for stability
                node.vx = node.vx.coerceIn(-MAX_SPEED, MAX_SPEED)
                node.vy = node.vy.coerceIn(-MAX_SPEED, MAX_SPEED)
                node.x += node.vx
                node.y += node.vy
            }
        }

        // d3-style cooling: guaranteed termination in ~ ALPHA_DECAY steps.
        alpha *= ALPHA_DECAY
        if (alpha <= ALPHA_MIN) {
            alpha = 0f
            freeze()
        }
    }

    /** Zeroes all velocities so the layout is pixel-stable while idle. */
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
        /** Per-step temperature decay: 0.985^600 ≈ 0.0001 → hard stop. */
        private const val ALPHA_DECAY = 0.985f
        private const val ALPHA_MIN = 0.005f
    }
}
