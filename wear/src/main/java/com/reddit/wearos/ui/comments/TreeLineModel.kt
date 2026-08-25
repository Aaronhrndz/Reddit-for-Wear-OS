package com.reddit.wearos.ui.comments

import com.reddit.wearos.data.model.RedditComment

/**
 * Segmento de línea de árbol para una columna específica.
 * hasTop: línea vertical desde y=0 hasta cornerY
 * hasBottom: línea vertical desde cornerY hasta height
 * hasHoriz: línea horizontal desde este punto hacia la derecha (entrada a la tarjeta)
 */
data class TreeLineSegment(
    val hasTop: Boolean = false,
    val hasBottom: Boolean = false,
    val hasHoriz: Boolean = false
)

data class FlatComment(
    val comment: RedditComment,
    val depth: Int,
    val segments: List<TreeLineSegment> = emptyList(),
    val isCollapsed: Boolean = false
)

/**
 * Modelo canónico de árbol plano estilo `tree` (terminal/explorador de archivos).
 *
 * Para un nodo a depth D, se generan exactamente D segmentos (segments.size == depth):
 *   - Columnas 0..D-2: ancestros (pass-through vertical si activo)
 *   - Columna D-1: rama propia (├── o └──)
 *
 * La conexión padre-hijo se establece así:
 *   - Hermanos comparten la misma columna D-1
 *   - El primer hijo tiene top=true: su línea vertical empieza en el borde superior
 *     de la fila, conectando visualmente con el padre por proximidad de filas adyacentes
 *   - El último hijo tiene bottom=false: la línea termina en └──
 *   - Hijos intermedios tienen top=true y bottom=true: la línea pasa de ├── a ├──
 */
fun flattenCommentTree(
    comments: List<RedditComment>,
    depth: Int = 0,
    activeAncestors: List<Boolean> = emptyList()
): List<FlatComment> {
    val result = mutableListOf<FlatComment>()
    for (i in comments.indices) {
        val c = comments[i]
        val isLast = (i == comments.size - 1)
        val replies = if (c.replies != null) {
            com.reddit.wearos.data.model.parseCommentsFromListingElement(c.replies)
        } else {
            emptyList()
        }
        val hasReplies = replies.isNotEmpty()

        val segments = mutableListOf<TreeLineSegment>()

        // 1. Columnas de ancestros (0 hasta depth-2): pasan recto si el ancestro tiene más hermanos
        for (colIdx in 0 until (depth - 1)) {
            val isActive = colIdx < activeAncestors.size && activeAncestors[colIdx]
            segments.add(TreeLineSegment(hasTop = isActive, hasBottom = isActive, hasHoriz = false))
        }

        // 2. Columna propia (depth-1): rama de entrada a esta tarjeta
        if (depth > 0) {
            segments.add(TreeLineSegment(
                hasTop = true,       // siempre: viene del padre (primer hijo) o del hermano anterior
                hasBottom = !isLast, // continúa hacia el siguiente hermano
                hasHoriz = true      // horizontal hacia la tarjeta
            ))
        }

        // segments.size == depth SIEMPRE (sin feeder)

        result.add(
            FlatComment(
                comment = c.copy(depth = depth),
                depth = depth,
                segments = segments
            )
        )

        if (hasReplies) {
            val nextAncestors = if (depth == 0) emptyList() else activeAncestors + (!isLast)
            result.addAll(flattenCommentTree(replies, depth + 1, nextAncestors))
        }
    }
    return result
}
