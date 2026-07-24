package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.ChatAccessState
import kotlinx.coroutines.launch

@Composable fun ChatRecoveryGateView(accessState:ChatAccessState?, unavailableMessage:String?=null,onCancel:(()->Unit)?=null,onCreatePin:(suspend (String)->Result<Unit>)?=null,onRestore:(suspend (String)->Result<Unit>)?=null,content:@Composable ()->Unit){when(accessState){ChatAccessState.Available->content();ChatAccessState.NeedsPinSetup->ChatRecoveryPinForm(false,onCancel,onCreatePin);ChatAccessState.NeedsRestore->ChatRecoveryPinForm(true,onCancel,onRestore);is ChatAccessState.Unavailable->ChatRecoveryStatusView(unavailableMessage?:accessState.reason,onCancel);null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator();Text(stringResource(R.string.chat_recovery_loading),Modifier.padding(top=48.dp))}}}
@Composable fun ChatRecoverySettingsView(onClose:()->Unit,onChangePin:()->Unit,onForceRestore:()->Unit,modifier:Modifier=Modifier)=Column(modifier.padding(20.dp)){Text(stringResource(R.string.chat_recovery_pin),fontSize=20.sp);Button(onChangePin,Modifier.fillMaxWidth().padding(top=16.dp)){Text(stringResource(R.string.chat_action_edit))};TextButton(onForceRestore){Text(stringResource(R.string.chat_action_reply))};TextButton(onClose){Text(stringResource(R.string.chat_action_reply))}}
@Composable private fun ChatRecoveryPinForm(restore:Boolean,onCancel:(()->Unit)?,submit:(suspend (String)->Result<Unit>)?){val invalidPin=stringResource(R.string.chat_recovery_invalid_pin);var pin by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)};var saving by remember{mutableStateOf(false)};val scope=rememberCoroutineScope();ChatRecoveryFormContainer{Text(stringResource(R.string.chat_recovery_pin),fontSize=24.sp);ChatRecoveryPINField(pin){pin=it};if(!restore)ChatRecoveryPINField(confirm){confirm=it};error?.let{Text(it,color=Color.Red)};Button({if(pin.length!=6||(!restore&&pin!=confirm))error=invalidPin else scope.launch{saving=true;val r=submit?.invoke(pin);saving=false;if(r?.isSuccess==true)onCancel?.invoke()else error=r?.exceptionOrNull()?.message} },enabled=!saving){Text(stringResource(R.string.chat_action_reply))};onCancel?.let{TextButton(it){Text(stringResource(R.string.chat_action_reply))}}}}
@Composable private fun ChatRecoveryFormContainer(content:@Composable ColumnScope.()->Unit)=Box(Modifier.fillMaxSize().background(Color.Black.copy(.35f)),contentAlignment=Alignment.BottomCenter){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart=32.dp,topEnd=32.dp)).background(if(androidx.compose.foundation.isSystemInDarkTheme())Color(0xFF101112) else Color.White).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,content=content)}
@Composable fun ChatRecoveryStatusView(message:String,onClose:(()->Unit)?=null)=ChatRecoveryFormContainer{Icon(Icons.Default.Lock,null,modifier=Modifier.size(30.dp));Text(message);onClose?.let{Button(it){Text(stringResource(R.string.chat_action_reply))}}}
@Composable fun ChatRecoveryPINField(value:String,onChange:(String)->Unit){OutlinedTextField(value,{onChange(it.filter(Char::isDigit).take(6))},label={Text(stringResource(R.string.chat_recovery_pin))},supportingText={Text(stringResource(R.string.chat_recovery_six_digits))},keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=KeyboardType.NumberPassword),modifier=Modifier.fillMaxWidth())}
fun filteredPIN(text:String,length:Int)=text.filter(Char::isDigit).take(length)
fun isValidPIN(pin:String,length:Int)=pin.length==length&&pin.all(Char::isDigit)
