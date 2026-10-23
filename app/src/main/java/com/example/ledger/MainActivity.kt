package com.example.ledger

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Categories : Screen
    data object Currencies : Screen
    data object Personal : Screen
    data class Detail(val id: Long) : Screen
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = this
        setContent {
            AppTheme {
                val vm: LedgerVM = viewModel()
                val prefs = remember { Prefs(applicationContext) }
                AppRoot(vm, prefs, act)
            }
        }
    }
}

@Composable
fun AppRoot(vm: LedgerVM, prefs: Prefs, act: FragmentActivity) {
    var locked by remember { mutableStateOf(prefs.b("fp") || prefs.s("pass").isNotEmpty()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    if (locked) {
        LockScreen(prefs, act) { locked = false }
        return
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    val home = { screen = Screen.Home }
    when (val s = screen) {
        Screen.Home -> HomeScreen(vm, prefs) { screen = it }
        Screen.Settings -> SettingsScreen(vm, prefs, home)
        Screen.Categories -> CategoriesScreen(vm, home)
        Screen.Currencies -> CurrenciesScreen(vm, home)
        Screen.Personal -> PersonalScreen(prefs, home)
        is Screen.Detail -> AccountScreen(vm, prefs, s.id, home)
    }
}
