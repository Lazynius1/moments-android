package com.moments.android

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.coordinators.TabBarScreen
import com.moments.android.views.login.AccountState
import com.moments.android.views.login.DeactivatedScreen
import com.moments.android.views.login.LoginScreen
import com.moments.android.views.login.SplashScreen
import com.moments.android.views.login.SuspendedScreen
import com.moments.android.views.login.resolveAccountState
import com.moments.android.views.shared.MomentsTheme
import com.moments.android.views.shared.Surface
import kotlinx.coroutines.launch

@Composable
fun MomentsApp(
    deepLinkUri: Uri? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var showSplash by remember { mutableStateOf(true) }
    var signedIn by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }
    var accountState by remember { mutableStateOf<AccountState>(AccountState.Loading) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { signedIn = it.currentUser != null }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        onDispose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
    }

    LaunchedEffect(signedIn) {
        if (signedIn) {
            accountState = AccountState.Loading
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            accountState = if (uid != null) resolveAccountState(uid) else AccountState.Active
        }
    }

    when {
        showSplash -> SplashScreen(onComplete = { showSplash = false })
        !signedIn -> LoginScreen(onAuthenticated = { signedIn = true })
        accountState is AccountState.Loading -> AccountLoading()
        accountState is AccountState.Deactivated -> DeactivatedScreen(accountState as AccountState.Deactivated) {
            scope.launch {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                accountState = if (uid != null) resolveAccountState(uid) else AccountState.Active
            }
        }
        accountState is AccountState.Suspended -> SuspendedScreen(accountState as AccountState.Suspended)
        else -> TabBarScreen(deepLinkUri = deepLinkUri, onDeepLinkHandled = onDeepLinkHandled)
    }
}

@Composable
private fun AccountLoading() {
    Box(Modifier.fillMaxSize().background(Surface), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Preview(showBackground = true)
@Composable
private fun MomentsAppPreview() {
    MomentsTheme { MomentsApp() }
}
