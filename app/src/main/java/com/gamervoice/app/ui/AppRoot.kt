package com.gamervoice.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gamervoice.app.service.ConnState

@Composable
fun AppRoot(rejoinRoom: String?) {
    val vm: CallViewModel = viewModel()
    val nav = rememberNavController()
    val call by vm.call.collectAsStateWithLifecycle()
    val inCall = call.conn == ConnState.Connected || call.conn == ConnState.Reconnecting || call.conn == ConnState.Connecting

    // Navigate automatically between join and call screens.
    LaunchedEffect(inCall) {
        val target = if (inCall) "call" else "join"
        if (nav.currentDestination?.route != target) nav.navigate(target) { popUpTo(0) }
    }

    NavHost(nav, startDestination = "join") {
        composable("join") { JoinScreen(vm, rejoinRoom, onSettings = { nav.navigate("settings") }, onHelp = { nav.navigate("help") }) }
        composable("call") { CallScreen(vm, onSettings = { nav.navigate("settings") }) }
        composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }, onHelp = { nav.navigate("help") }) }
        composable("help") { KeepAliveHelpScreen(onBack = { nav.popBackStack() }) }
    }
}
