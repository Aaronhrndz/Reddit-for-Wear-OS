package com.reddit.wearos.ui.comments

import com.reddit.wearos.data.model.RedditComment

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
 * Algoritmo canónico de árbol para listas planas.
 * Garantiza 100% de continuidad vertical entre elementos adyacentes (bottom[i][c] == top[i+1][c]).
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
        // 1. Columnas de ancestros que pasan recto (0 hasta depth-2)
        for (colIdx in 0 until (depth - 1)) {
            val isActive = colIdx < activeAncestors.size && activeAncestors[colIdx]
            segments.add(TreeLineSegment(hasTop = isActive, hasBottom = isActive, hasHoriz = false))
        }

        // 2. Columna propia (depth - 1): la rama que conecta con el padre/hermanos
        if (depth > 0) {
            // top siempre true: viene del padre (primer hijo) o del hermano anterior
            // bottom true si hay más hermanos después
            segments.add(TreeLineSegment(hasTop = true, hasBottom = !isLast, hasHoriz = true))
        }

        // 3. Si este nodo tiene hijos, emite un segmento "feeder" en la columna depth
        //    que alimentará al primer hijo (solo bottom, sin top ni horiz)
        if (hasReplies) {
            segments.add(TreeLineSegment(hasTop = false, hasBottom = true, hasHoriz = false))
        }

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
