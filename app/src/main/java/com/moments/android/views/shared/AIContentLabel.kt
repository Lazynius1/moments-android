package com.moments.android.views.shared

import com.moments.android.icons.MomentsIcons
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.feed.rememberAdaptiveColors
import java.io.FileInputStream
import java.io.InputStream

/**
 * Detección de contenido generado con IA a partir de los metadatos del archivo original.
 * Busca el valor IPTC DigitalSourceType (XMP / C2PA) en los primeros y últimos 8 MB.
 */
object AIContentProvenance {
    private const val WINDOW_BYTES = 8 * 1024 * 1024

    // Basta con una: marca IPTC (cubre trainedAlgorithmicMedia y compositeWithTrainedAlgorithmicMedia)
    // y parámetros de herramientas de difusión (Stable Diffusion, ComfyUI, InvokeAI, NovelAI).
    private val MARKERS = listOf(
        "rainedAlgorithmicMedia", "\"class_type\"", "invokeai_metadata", "sd-metadata", "NovelAI",
    ).map { it.toByteArray(Charsets.US_ASCII) }

    // Tienen que aparecer las dos: parámetros de Automatic1111/Forge y manifiestos C2PA de generadores conocidos.
    private val MARKER_PAIRS = listOf(
        "Steps: " to "Sampler: ",
        "c2pa" to "OpenAI",
        "c2pa" to "Adobe Firefly",
        "c2pa" to "Google AI",
    ).map { (a, b) -> a.toByteArray(Charsets.US_ASCII) to b.toByteArray(Charsets.US_ASCII) }

    private fun containsMarker(data: ByteArray): Boolean =
        MARKERS.any { indexOf(data, it) } || MARKER_PAIRS.any { (a, b) -> indexOf(data, a) && indexOf(data, b) }

    /** Lee el archivo original; nunca lanza. Llamar fuera del hilo principal. */
    fun isAIGenerated(context: Context, uri: Uri): Boolean = try {
        detect(context, uri)
    } catch (_: Throwable) {
        false
    }

    private fun detect(context: Context, uri: Uri): Boolean {
        if (uri == Uri.EMPTY) return false
        val resolver = context.contentResolver
        val length = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull()?.takeIf { it > 0 }

        // Cabeza
        val head = resolver.openInputStream(uri)?.use { readUpTo(it, WINDOW_BYTES) } ?: return false
        if (containsMarker(head)) return true
        // Archivo entero leído o tamaño desconocido: solo la cabeza
        if (head.size < WINDOW_BYTES || length == null || length <= head.size) return false

        // Cola (solapa lo justo para no perder un marcador partido)
        val tailStart = maxOf(length - WINDOW_BYTES, head.size.toLong() - 64)
        val tailLength = (length - tailStart).toInt()
        val tail = resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { input ->
                input.channel.position(tailStart)
                readUpTo(input, tailLength)
            }
        } ?: return false
        return containsMarker(tail)
    }

    private fun readUpTo(input: InputStream, limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var total = 0
        while (total < limit) {
            val read = input.read(buffer, total, limit - total)
            if (read <= 0) break
            total += read
        }
        return if (total == limit) buffer else buffer.copyOf(total)
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray): Boolean {
        if (pattern.isEmpty() || data.size < pattern.size) return false
        val first = pattern[0]
        val last = data.size - pattern.size
        var i = 0
        while (i <= last) {
            if (data[i] == first) {
                var j = 1
                while (j < pattern.size && data[i + j] == pattern[j]) j++
                if (j == pattern.size) return true
            }
            i++
        }
        return false
    }
}

/** Fuente de la etiqueta IA guardada en Firestore. */
object AILabelSource {
    const val CREATOR = "creator"
    const val METADATA = "metadata"
}

/** Etiqueta compacta "Info de IA" (icono + texto); al pulsar abre la hoja informativa. */
@Composable
fun AIContentBadge(
    labelSource: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var showingInfo by remember { mutableStateOf(false) }
    AIContentBadgeLabel(
        color = color,
        onClick = { showingInfo = true },
        modifier = modifier,
    )
    if (showingInfo) {
        AIContentInfoSheet(
            labelSource = labelSource,
            onDismiss = { showingInfo = false },
        )
    }
}

/** Solo la etiqueta; la hoja la gestiona quien la usa (p. ej. para pausar la historia). */
@Composable
fun AIContentBadgeLabel(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            MomentsIcons.AILabel,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp),
        )
        Text(
            stringResource(R.string.ai_badge),
            color = color,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** Hoja breve que explica la etiqueta según su origen. */
@Composable
fun AIContentInfoSheet(
    labelSource: String?,
    onDismiss: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    MomentsModalSheet(
        onDismissRequest = onDismiss,
        allowExpanded = false,
        containerColor = colors.surfaceBackground,
    ) {
        MomentsSheetHeader(title = stringResource(R.string.ai_info_title), titleSize = 18.sp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(
                    if (labelSource == AILabelSource.METADATA) R.string.ai_info_metadata
                    else R.string.ai_info_creator,
                ),
                color = colors.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
            )
            Text(
                stringResource(R.string.ai_info_footer),
                color = colors.secondary,
                fontSize = 13.sp,
            )
        }
    }
}
