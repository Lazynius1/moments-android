package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageStatus
import java.text.SimpleDateFormat
import java.util.Locale

@Composable fun GlassmorphicReplyBar(message: EnhancedMessage, otherParticipantName: String, onCancel: () -> Unit, modifier: Modifier = Modifier) = ReplyContent(message, otherParticipantName, true, onCancel = onCancel, modifier = modifier)
@Composable fun GlassmorphicReplyPreview(message: EnhancedMessage, isParentMessageFromCurrentUser: Boolean, otherParticipantName: String, onTap: (() -> Unit)?, modifier: Modifier = Modifier) = ReplyContent(message, otherParticipantName, false, onTap, modifier = modifier)
@Composable fun StackedReplyQuote(repliedMessage: EnhancedMessage, isOutgoingRow: Boolean, otherParticipantName: String, onTap: ((String) -> Unit)? = null) { ReplyContent(repliedMessage, otherParticipantName, false, { onTap?.invoke(repliedMessage.id) }) }
@Composable fun EmbeddedReplyView(repliedMessage: EnhancedMessage, isOutgoingBubble: Boolean, otherParticipantName: String, onTap: (() -> Unit)? = null) { ReplyContent(repliedMessage, otherParticipantName, false, onTap = onTap, modifier = Modifier.fillMaxWidth()) }
@Composable private fun ReplyContent(message: EnhancedMessage, name: String, large: Boolean, onTap: (() -> Unit)? = null, onCancel: (() -> Unit)? = null, modifier: Modifier = Modifier) {
 val dark=androidx.compose.foundation.isSystemInDarkTheme(); val colors=com.moments.android.views.feed.AdaptiveColors(dark); Row(modifier.clip(RoundedCornerShape(if(large)12.dp else 10.dp)).background(colors.replyBarBackground).clickable(enabled=onTap!=null){onTap?.invoke()}.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.width(3.dp).height(if(large)40.dp else 30.dp).background(colors.userAccentColor)); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)){Text(name,color=colors.userAccentColor,fontSize=if(large)13.sp else 11.sp,maxLines=1);Text(message.content?:message.fileName.orEmpty(),fontSize=if(large)14.sp else 12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)};message.thumbnailUrl?.let{AsyncImage(it,null,Modifier.size(if(large)40.dp else 30.dp).clip(RoundedCornerShape(5.dp)))};onCancel?.let{Icon(Icons.Default.Close,null,Modifier.padding(start=8.dp).clickable(onClick=it))}}
}
@Composable fun ChatQuickReactionsBar(onReaction:(String)->Unit,onMore:()->Unit)=Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(.14f)).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){listOf("❤️","😂","😮","😢","😡","👍").forEach{Text(it,fontSize=26.sp,modifier=Modifier.clickable{onReaction(it)})};Icon(Icons.Default.Add,stringResource(R.string.chat_action_more_reactions),modifier=Modifier.size(30.dp).clickable(onClick=onMore))}
object MessageReactionMetrics { fun badgeDiameter(compact:Boolean,cluster:Boolean=false)=if(cluster)18.dp else if(compact)22.dp else 24.dp; fun hangOffset(compact:Boolean)=badgeDiameter(compact).value*.62f; fun reactionRowSpacing(compact:Boolean)=badgeDiameter(compact).value*.66f }
@Composable fun MessageReactionChip(reactions:Map<String,List<String>>,onTap:(String)->Unit,compact:Boolean=false,cluster:Boolean=false)=Row(horizontalArrangement=Arrangement.spacedBy((-5).dp)){reactions.entries.sortedByDescending{it.value.size}.take(5).forEach{(emoji,users)->Column(Modifier.size(MessageReactionMetrics.badgeDiameter(compact,cluster)).clip(RoundedCornerShape(50)).background(Color.White.copy(.16f)).clickable{onTap(emoji)},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(emoji,fontSize=if(cluster)12.sp else 18.sp);if(users.size>1)Text(users.size.toString(),fontSize=8.sp)}}}
@Composable fun MessageStarBadge(compact:Boolean=false,cluster:Boolean=false)=Icon(Icons.Default.Star,null,tint=Color(0xFFFFD60A),modifier=Modifier.size(if(cluster)16.dp else if(compact)20.dp else 22.dp))
@Composable fun MessageTimestamp(message:EnhancedMessage,isCurrentUser:Boolean,showSeenLabel:Boolean=false,overrideStatus:MessageStatus?=null)=Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){Text(SimpleDateFormat("HH:mm",Locale.getDefault()).format(message.timestamp),fontSize=11.sp,color=Color.Gray);if(message.editedAt!=null)Text(stringResource(R.string.chat_edited),fontSize=11.sp,color=Color.Gray);if(isCurrentUser)MessageStatusIcon(overrideStatus?:message.status,showSeenLabel)}
@Composable fun MessageStatusIcon(status:MessageStatus,showSeenLabel:Boolean=false){when(status){MessageStatus.SENDING->CircularProgressIndicator(Modifier.size(10.dp),strokeWidth=1.dp);MessageStatus.FAILED->Icon(Icons.Default.Error,null,tint=Color.Red,modifier=Modifier.size(12.dp));MessageStatus.READ->if(showSeenLabel)Text(stringResource(R.string.chat_seen),fontSize=11.sp,color=Color.Gray)else Icon(Icons.Default.DoneAll,null,tint=Color(0xFF3F6F8F),modifier=Modifier.size(13.dp));else->Icon(Icons.Default.Done,null,tint=Color.Gray,modifier=Modifier.size(12.dp))}}
@Composable fun GlassmorphicReactionsOverlay(onReaction:(String)->Unit)=ChatQuickReactionsBar(onReaction,{})
