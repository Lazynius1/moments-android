package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moments.android.views.feed.AdaptiveColors

/** Port de `ChatSearchNavigationBar.swift`. */
@Composable fun ChatSearchNavigationBar(text:String,onTextChange:(String)->Unit,adaptiveColors:AdaptiveColors,onClear:()->Unit,onClose:()->Unit,onSubmit:()->Unit={},modifier:Modifier=Modifier)=Row(modifier.fillMaxWidth().background(adaptiveColors.chatBackground.first()).padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){ChatInThreadSearchField(text,onTextChange,adaptiveColors,onClear,onSubmit,Modifier.weight(1f));IconButton(onClose,Modifier.size(44.dp)){Icon(Icons.Default.Close,null,tint=adaptiveColors.primary)}}
