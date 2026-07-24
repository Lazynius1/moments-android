package com.moments.android.views.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.Point
import com.moments.android.models.QuestionResponse
import com.moments.android.models.StickerData
import com.moments.android.views.creator.CreatorView
import kotlinx.coroutines.tasks.await

/** Port de `questionResponseStickerRenderSize`. */
object QuestionResponseStickerLayout {
    val width = 300.dp
    val height = 132.dp
}

/** Port de `QuestionResponseStoryStickerCardView`. */
@Composable
fun QuestionResponseStoryStickerCardView(
    questionText: String,
    styleVariant: Int,
    modifier: Modifier = Modifier,
) {
    val isLight = styleVariant % 6 == 0
    val surface = if (isLight) Color(0xFFF8F8FA) else Color(0xFF141519)
    val header = if (isLight) Color(0xFF161616) else Color(0xFF2B6CFF)
    val ink = if (isLight) Color(0xFF161616) else Color.White
    Column(
        modifier = modifier
            .width(QuestionResponseStickerLayout.width)
            .height(QuestionResponseStickerLayout.height)
            .clip(RoundedCornerShape(24.dp))
            .background(surface),
    ) {
        Text(
            text = "ANONYMOUS RESPONSE",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.fillMaxWidth().background(header).padding(horizontal = 16.dp, vertical = 14.dp),
        )
        Text(
            text = questionText,
            color = ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/** Port de `QuestionResponsesView`: bandeja del autor y detalle para compartir. */
@Composable
fun QuestionResponsesView(
    questionText: String,
    storyId: String,
    userId: String,
    stickerId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var responses by remember(storyId, stickerId) { mutableStateOf<List<QuestionResponse>>(emptyList()) }
    var loading by remember(storyId, stickerId) { mutableStateOf(true) }
    var selected by remember { mutableStateOf<QuestionResponse?>(null) }
    LaunchedEffect(storyId, userId, stickerId) {
        val loaded = runCatching {
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .collection("stories").document(storyId).collection("questionResponses")
                .document(stickerId).collection("responses").orderBy("timestamp")
                .get().await().documents.map { document ->
                    QuestionResponse.from((document.data ?: emptyMap()) + ("id" to document.id))
                }
        }.getOrDefault(emptyList())
        responses = loaded
        loading = false
    }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row { Button(onClick = { if (selected == null) onDismiss() else selected = null }) { Text(if (selected == null) "Close" else "Back") } }
        if (selected == null) {
            Text("Questions received", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(questionText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            when {
                loading -> CircularProgressIndicator()
                responses.isEmpty() -> Text("No questions yet. Share your story to receive questions.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    items(responses, key = { it.id }) { response ->
                        Button(onClick = { selected = response }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(response.response, textAlign = TextAlign.Start)
                            }
                        }
                    }
                }
            }
        } else {
            val response = selected ?: return@Column
            Text("Reply in your story", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(questionText, fontWeight = FontWeight.SemiBold)
            Text(response.response)
            CreatorViewWithResponseData(questionText, response, onDismiss)
        }
    }
}

/** Port de `CreatorViewWithResponseData`: abre el creator con el sticker respuesta. */
@Composable
fun CreatorViewWithResponseData(
    questionText: String,
    response: QuestionResponse,
    onDismiss: () -> Unit,
) {
    var showCreator by remember { mutableStateOf(false) }
    if (showCreator) {
        CreatorView(
            showCreatorView = true,
            onShowCreatorViewChange = { visible -> if (!visible) onDismiss() },
            isCreatingStory = true,
            onIsCreatingStoryChange = {},
            initialSticker = StickerData(
                type = "questionResponse",
                content = response.response,
                position = Point(0.5, 0.5),
                scale = 1.0,
                rotation = 0.0,
                questionText = response.response,
                styleVariant = 0,
            ),
            startInCameraWhenOnlySticker = true,
        )
    } else {
        Button(onClick = { showCreator = true }, modifier = Modifier.fillMaxWidth()) { Text("Create story") }
    }
}
