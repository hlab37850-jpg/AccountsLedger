package com.example.ledger

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode

/** Returns a function that opens the contacts app; the callback gets (name, phone) of the chosen contact. */
@Composable
fun rememberContactPicker(onPicked: (String, String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) {
            try {
                ctx.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) onPicked(c.getString(0) ?: "", (c.getString(1) ?: "").replace(" ", ""))
                }
            } catch (e: Exception) {
                toast(ctx, "تعذر قراءة جهة الاتصال")
            }
        }
    }
    return {
        try {
            launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        } catch (e: Exception) {
            toast(ctx, "لا يوجد تطبيق جهات اتصال")
        }
    }
}

fun evalExpr(s: String): Double? {
    val t = s.replace("×", "*").replace("÷", "/").replace(" ", "")
    var i = 0
    fun peek(): Char = if (i < t.length) t[i] else '\u0000'
    fun number(): Double? {
        val st = i
        while (i < t.length && (t[i].isDigit() || t[i] == '.')) i++
        return t.substring(st, i).toDoubleOrNull()
    }
    fun factor(): Double? {
        if (peek() == '-') {
            i++
            val f = factor() ?: return null
            return -f
        }
        return number()
    }
    fun term(): Double? {
        var v = factor() ?: return null
        while (peek() == '*' || peek() == '/') {
            val op = peek()
            i++
            val r = factor() ?: return null
            if (op == '*') v *= r else {
                if (r == 0.0) return null
                v /= r
            }
        }
        return v
    }
    fun expr(): Double? {
        var v = term() ?: return null
        while (peek() == '+' || peek() == '-') {
            val op = peek()
            i++
            val r = term() ?: return null
            v = if (op == '+') v + r else v - r
        }
        return v
    }
    val r = expr()
    return if (i == t.length) r else null
}

fun plain(d: Double): String = BigDecimal(d).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

@Composable
fun CalculatorDialog(initial: String, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    var expr by remember { mutableStateOf(initial) }
    fun press(k: String) {
        expr = when (k) {
            "C" -> ""
            "⌫" -> expr.dropLast(1)
            "=" -> evalExpr(expr)?.let { plain(it) } ?: expr
            else -> expr + k
        }
    }
    val keys = listOf(
        listOf("C", "⌫", "÷", "×"),
        listOf("7", "8", "9", "-"),
        listOf("4", "5", "6", "+"),
        listOf("1", "2", "3", "="),
        listOf("0", ".", "00", "")
    )
    CardDialog("الآلة الحاسبة", onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = Color.White, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        expr.ifEmpty { "0" }, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                }
                keys.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { k ->
                            if (k.isEmpty()) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                val op = k in listOf("÷", "×", "-", "+", "=")
                                Button(
                                    onClick = { press(k) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (op) Green else Color.White,
                                        contentColor = if (op) Color.White else Color.Black
                                    )
                                ) { Text(k, fontSize = 20.sp) }
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                val r = evalExpr(expr)
                if (r != null) onResult(plain(r)) else onDismiss()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("إدراج النتيجة") }
    }
}
