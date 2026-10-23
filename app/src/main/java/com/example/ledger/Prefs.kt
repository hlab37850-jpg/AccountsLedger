package com.example.ledger

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** SharedPreferences wrapper whose reads are observable from Compose. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private var tick by mutableIntStateOf(0)

    fun s(k: String, d: String = ""): String { tick; return sp.getString(k, d) ?: d }
    fun b(k: String, d: Boolean = false): Boolean { tick; return sp.getBoolean(k, d) }
    fun i(k: String, d: Int = 0): Int { tick; return sp.getInt(k, d) }

    fun put(k: String, v: String) { sp.edit().putString(k, v).apply(); tick++ }
    fun put(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply(); tick++ }
    fun put(k: String, v: Int) { sp.edit().putInt(k, v).apply(); tick++ }
}
