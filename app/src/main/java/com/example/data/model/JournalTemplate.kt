package com.example.data.model

data class JournalTemplate(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String,
    val defaultTitle: String,
    val defaultBody: String,
    val defaultTags: List<String> = emptyList(),
    val defaultMood: String? = null,
    val defaultEmoji: String = "📄",
    val defaultPageType: String = "PAGE",
    val defaultStatus: String = "Inbox"
) {
    companion object {
        val ALL_TEMPLATES = listOf(
            JournalTemplate(
                id = "freeform",
                name = "Página en blanco",
                description = "Documento limpio listo para bloques Markdown, tareas e ideas.",
                iconName = "edit",
                defaultTitle = "",
                defaultBody = "",
                defaultEmoji = "📄",
                defaultPageType = "PAGE",
                defaultStatus = "Inbox"
            ),
            JournalTemplate(
                id = "project_milestone",
                name = "Proyecto y entregables",
                description = "Objetivo, decisiones técnicas y lista de tareas accionables.",
                iconName = "work",
                defaultTitle = "Proyecto: ",
                defaultBody = """
> 💡 Resume en una frase por qué importa este proyecto y cuál es su resultado clave.

### Objetivo y contexto
- Resultado esperado:
- Fecha límite:

### Próximos pasos
- [ ] Definir alcance inicial
- [ ] Crear primera sub-página de especificaciones

---

### Decisiones y arquitectura
- 
                """.trimIndent(),
                defaultTags = listOf("proyecto", "enfoque"),
                defaultEmoji = "🚀",
                defaultPageType = "PROJECT",
                defaultStatus = "En curso"
            ),
            JournalTemplate(
                id = "meeting_notes",
                name = "Acta de reunión",
                description = "Participantes, agenda, acuerdos y próximos pasos.",
                iconName = "groups",
                defaultTitle = "Reunión: ",
                defaultBody = """
**Participantes:** 
**Contexto:** 

### Agenda
1. 
2. 

### Puntos clave y acuerdos
- 

### Acciones asignadas
- [ ] 
                """.trimIndent(),
                defaultTags = listOf("reunion", "trabajo"),
                defaultEmoji = "🗓️",
                defaultPageType = "MEETING",
                defaultStatus = "En curso"
            ),
            JournalTemplate(
                id = "daily_reflection",
                name = "Nota diaria",
                description = "Prioridad del día, bitácora rápida y cierre.",
                iconName = "wb_sunny",
                defaultTitle = "Nota de hoy",
                defaultBody = """
### Enfoque de hoy
- [ ] Prioridad principal del día
- [ ] Bloque de trabajo profundo

### Bitácora y aprendizajes
- 

### Cierre del día
> 
                """.trimIndent(),
                defaultTags = listOf("diario", "hoy"),
                defaultMood = "🎯 Focused",
                defaultEmoji = "☀️",
                defaultPageType = "DAILY",
                defaultStatus = "En curso"
            ),
            JournalTemplate(
                id = "book_note",
                name = "Base de conocimiento / Libro",
                description = "Tesis central, citas destacadas y síntesis personal.",
                iconName = "menu_book",
                defaultTitle = "Referencia: ",
                defaultBody = """
**Autor / Fuente:** 
**Tema:** 

### Idea central
> 💡 

### Conceptos y citas clave
- 

### Cómo aplicarlo
- [ ] 
                """.trimIndent(),
                defaultTags = listOf("conocimiento", "lectura"),
                defaultEmoji = "📚",
                defaultPageType = "DOC",
                defaultStatus = "Inbox"
            ),
            JournalTemplate(
                id = "weekly_review",
                name = "Revisión semanal",
                description = "Logros, bloqueos y prioridades para la próxima semana.",
                iconName = "calendar_view_week",
                defaultTitle = "Revisión semanal",
                defaultBody = """
### Logros principales
1. 
2. 

### Aprendizajes y bloqueos
- 

### Prioridades próxima semana
- [ ] 
- [ ] 
                """.trimIndent(),
                defaultTags = listOf("revision", "planificacion"),
                defaultEmoji = "🧭",
                defaultPageType = "DOC",
                defaultStatus = "Inbox"
            ),
            JournalTemplate(
                id = "idea_spark",
                name = "Brote de idea",
                description = "Hipótesis rápida, por qué importa y primer experimento.",
                iconName = "lightbulb",
                defaultTitle = "Idea: ",
                defaultBody = """
> 💡 Escribe la idea tal como llegó antes de editarla.

### Por qué tiene potencial
- 

### Primer experimento mínimo
- [ ] 
                """.trimIndent(),
                defaultTags = listOf("idea"),
                defaultEmoji = "💡",
                defaultPageType = "PAGE",
                defaultStatus = "Inbox"
            ),
            JournalTemplate(
                id = "travel_trip",
                name = "Viaje e itinerario",
                description = "Vuelos, alojamiento, reservas y lista de equipaje.",
                iconName = "flight",
                defaultTitle = "Viaje a ",
                defaultBody = """
### Datos clave y reservas
- Destino: 
- Fechas: 
- Alojamiento: 

### Itinerario por día
1. Día 1: 
2. Día 2: 

### Equipaje y pendientes
- [ ] Documentos y entradas
- [ ] Cargadores y adaptador
                """.trimIndent(),
                defaultTags = listOf("viaje", "itinerario"),
                defaultEmoji = "✈️",
                defaultPageType = "PROJECT",
                defaultStatus = "Inbox"
            ),
            JournalTemplate(
                id = "sprint_tasks",
                name = "Panel de tareas",
                description = "Checklist priorizada para ejecutar rápido sin distracciones.",
                iconName = "check_circle",
                defaultTitle = "Pendientes: ",
                defaultBody = """
### Prioridad alta
- [ ] 

### En progreso
- [ ] 

### En espera / Seguimiento
- [ ] 
                """.trimIndent(),
                defaultTags = listOf("tareas", "sprint"),
                defaultEmoji = "✅",
                defaultPageType = "TASK",
                defaultStatus = "En curso"
            ),
            JournalTemplate(
                id = "person_profile",
                name = "Ficha de persona / CRM",
                description = "Notas de contexto, intereses y acuerdos con alguien.",
                iconName = "person",
                defaultTitle = "Perfil: ",
                defaultBody = """
**Rol / Relación:** 
**Contacto:** 

### Contexto y temas en común
- 

### Historial de conversaciones
- 
                """.trimIndent(),
                defaultTags = listOf("personas", "red"),
                defaultEmoji = "👤",
                defaultPageType = "DOC",
                defaultStatus = "Listo"
            )
        )
    }
}
