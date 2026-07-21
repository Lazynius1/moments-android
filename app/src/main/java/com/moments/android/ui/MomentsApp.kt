package com.moments.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moments.android.ui.theme.MomentsTheme
import com.moments.android.ui.theme.Surface
import com.moments.android.ui.login.AccountState
import com.moments.android.ui.login.DeactivatedScreen
import com.moments.android.ui.login.LoginScreen
import com.moments.android.ui.login.SplashScreen
import com.moments.android.ui.login.SuspendedScreen
import com.moments.android.ui.login.resolveAccountState
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.google.firebase.auth.FirebaseAuth as FBAuth
import kotlinx.coroutines.launch
import com.moments.android.ui.feed.FeedScreen
import com.google.firebase.auth.FirebaseAuth

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Inicio", Icons.Filled.Home),
    Discover("Buscar", Icons.Filled.Explore),
    Create("Crear", Icons.Filled.Add),
    Activity("Actividad", Icons.Filled.Notifications),
    Profile("Perfil", Icons.Filled.Person),
}

@Composable
fun MomentsApp() {
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
            val uid = FBAuth.getInstance().currentUser?.uid
            accountState = if (uid != null) resolveAccountState(uid) else AccountState.Active
        }
    }

    when {
        showSplash -> SplashScreen(onComplete = { showSplash = false })
        !signedIn -> LoginScreen(onAuthenticated = { signedIn = true })
        accountState is AccountState.Loading -> AccountLoading()
        accountState is AccountState.Deactivated -> DeactivatedScreen(accountState as AccountState.Deactivated) {
            scope.launch {
                val uid = FBAuth.getInstance().currentUser?.uid
                accountState = if (uid != null) resolveAccountState(uid) else AccountState.Active
            }
        }
        accountState is AccountState.Suspended -> SuspendedScreen(accountState as AccountState.Suspended)
        else -> MomentsSignedInApp()
    }
}

@Composable
private fun AccountLoading() {
    Box(Modifier.fillMaxSize().background(Surface), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MomentsSignedInApp() {
    var destination by remember { mutableStateOf(Destination.Home) }
    Scaffold(
        topBar = { if (destination != Destination.Home) MomentsTopBar(destination) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        DestinationContent(destination, padding)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MomentsTopBar(destination: Destination) {
    TopAppBar(
        title = {
            Text(
                text = if (destination == Destination.Home) "moments" else destination.label,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            if (destination == Destination.Home) {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notificaciones")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun DestinationContent(destination: Destination, padding: PaddingValues) {
    when (destination) {
        Destination.Home -> FeedScreen(padding)
        else -> PlaceholderScreen(destination, padding)
    }
}

@Composable
private fun PlaceholderScreen(destination: Destination, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Text("${destination.label} estará disponible en la primera vertical Android.", style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun MomentsAppPreview() {
    MomentsTheme { MomentsApp() }
}
