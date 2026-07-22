package com.moments.android.views.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.shared.Surface
import kotlinx.coroutines.delay

private val stepKeys = listOf(
    R.string.creating_step_verifying,
    R.string.creating_step_creating,
    R.string.creating_step_uploading,
    R.string.creating_step_configuring,
    R.string.creating_step_completed,
)
private val completionEmoji = listOf("😊", "💛", "✨", "🫶", "😌")

/** Overlay "Creando tu cuenta" — equivalente a CreatingProfileView de iOS (mín. 2.8s, pasos animados). */
@Composable
fun CreatingProfileOverlay() {
    var step by remember { mutableIntStateOf(0) }
    val emoji = remember { completionEmoji.random() }

    LaunchedEffect(Unit) {
        val stepDuration = 2800L / stepKeys.size
        for (i in stepKeys.indices) {
            step = i
            delay(stepDuration)
        }
    }

    Box(Modifier.fillMaxSize().background(Surface), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(30.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.login_logo),
                contentDescription = null,
                modifier = Modifier.size(118.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.creating_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AuthColors.primary)
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                    },
                    label = "creatingStep",
                ) { s ->
                    val text = stringResource(stepKeys[s])
                    Text(
                        if (s == stepKeys.lastIndex) "$text $emoji" else text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = AuthColors.secondary(0.64f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
