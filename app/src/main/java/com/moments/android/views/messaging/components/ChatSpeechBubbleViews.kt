package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.models.EnhancedMessage

enum class ChatBubbleSide { LEADING, TRAILING }
enum class ChatMessageGroupPosition { FIRST, MIDDLE, LAST, SINGLE }
object ChatTextBubbleMetrics { val horizontalPadding=15.dp; val verticalPadding=10.dp; val cornerRadius=20.dp; const val maxWidthFraction=.78f }
data class TextSegment(val text:String,val isSpoiler:Boolean)
fun chatTextSegments(text:String)=text.split("||").mapIndexedNotNull{i,s->s.takeIf{it.isNotEmpty()}?.let{TextSegment(it,i%2==1)}}
@Composable fun ChatTextBubbleView(text:String,isOutgoing:Boolean,messageId:String?=null,groupPosition:ChatMessageGroupPosition=ChatMessageGroupPosition.SINGLE,reactions:Map<String,List<String>>?=null,isStarred:Boolean=false,repliedMessage:EnhancedMessage?=null,otherParticipantName:String="",onReplyTap:(()->Unit)?=null,onReaction:(String)->Unit,modifier:Modifier=Modifier){var reveal by remember(messageId){mutableStateOf(false)};val colors=com.moments.android.views.feed.AdaptiveColors(androidx.compose.foundation.isSystemInDarkTheme());val segments=chatTextSegments(text);val shape=when{isOutgoing&&groupPosition==ChatMessageGroupPosition.FIRST->RoundedCornerShape(20.dp,20.dp,4.dp,20.dp);isOutgoing&&groupPosition==ChatMessageGroupPosition.LAST->RoundedCornerShape(20.dp,4.dp,20.dp,20.dp);!isOutgoing&&groupPosition==ChatMessageGroupPosition.FIRST->RoundedCornerShape(20.dp,20.dp,20.dp,4.dp);!isOutgoing&&groupPosition==ChatMessageGroupPosition.LAST->RoundedCornerShape(4.dp,20.dp,20.dp,20.dp);else->RoundedCornerShape(20.dp)};Column(modifier.widthIn(max=300.dp).clip(shape).background(if(isOutgoing)colors.userAccentColor else colors.messageBubbleBackground).clickable(enabled=segments.any{it.isSpoiler}){reveal=!reveal}.padding(ChatTextBubbleMetrics.horizontalPadding,ChatTextBubbleMetrics.verticalPadding)){repliedMessage?.let{EmbeddedReplyView(it,isOutgoing,otherParticipantName,onReplyTap)};segments.forEach{Text(if(it.isSpoiler&&!reveal)"████" else it.text,color=if(isOutgoing)Color.White else colors.messageTextColor,fontSize=15.sp)};if(!reactions.isNullOrEmpty())MessageReactionChip(reactions,onReaction,compact=true)}}
object ChatIncomingMessageLayout { val gutterAvatarSize=26.dp; val gutterGap=6.dp; val gutterInset get()=gutterAvatarSize+gutterGap }

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ChatIncomingAvatarGutter(showAvatar: Boolean, userId: String?, unavailable: Boolean, onTap: () -> Unit) {
    Box(Modifier.width(32.dp).height(32.dp).padding(end = 6.dp), contentAlignment = Alignment.BottomCenter) {
        if (showAvatar) {
            val color = if (unavailable) Color.Gray else Color(0xFF3F6F8F)
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(50)).background(color).combinedClickable(onClick = onTap)) {
                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(15.dp))
            }
        }
    }
}
