package com.example.ledger

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

val Green = Color(0xFF0F9D7A)
val GreenDark = Color(0xFF0B6B54)
val Tint = Color(0xFFE3F4EE)
val BarGrey = Color(0xFFCADFD8)
val PageBg = Color(0xFFF7FAF9)
val Debt = Color(0xFFC62828)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green, onPrimary = Color.White, secondary = GreenDark,
            background = PageBg, surface = Color.White
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(color = PageBg, modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

fun beep(p: Prefs) {
    if (p.b("sound", true)) {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70).startTone(ToneGenerator.TONE_PROP_ACK, 120)
        } catch (_: Exception) {
        }
    }
}

fun pickDate(ctx: Context, current: String, onPick: (String) -> Unit) {
    val p = current.take(10).split("-").mapNotNull { it.toIntOrNull() }
    val (y, m, d) = if (p.size == 3) Triple(p[0], p[1] - 1, p[2]) else Triple(2026, 0, 1)
    android.app.DatePickerDialog(ctx, { _, yy, mm, dd -> onPick("%04d-%02d-%02d".format(yy, mm + 1, dd)) }, y, m, d).show()
}

@Composable
fun CardDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(10.dp), color = Tint,
            modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 24.dp)
        ) {
            Column(
                Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Green).padding(8.dp),
                    contentAlignment = Alignment.Center
                ) { Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                content()
            }
        }
    }
}

@Composable
fun FieldBox(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    error: String? = null,
    singleLine: Boolean = true,
    onIconClick: (() -> Unit)? = null
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon, null, tint = Green,
                    modifier = Modifier.padding(end = 6.dp).size(30.dp).padding(2.dp)
                        .then(if (onIconClick != null) Modifier.clickable { onIconClick() } else Modifier)
                )
            }
            TextField(
                value = value, onValueChange = onChange, modifier = Modifier.weight(1f),
                placeholder = { Text(hint, fontSize = 14.sp, color = Color.Gray) },
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                isError = error != null,
                shape = RoundedCornerShape(6.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    errorContainerColor = Color.White,
                    focusedIndicatorColor = Green, unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
        if (error != null) Text(error, color = Color.Red, fontSize = 12.sp)
    }
}

@Composable
fun ClickBox(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = Green, modifier = Modifier.padding(end = 6.dp).size(22.dp))
        Surface(
            color = Color.White, shape = RoundedCornerShape(6.dp),
            modifier = Modifier.weight(1f).clickable(onClick = onClick)
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp), fontSize = 15.sp)
        }
    }
}

@Composable
fun TextEditDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp) },
        text = { OutlinedTextField(value = v, onValueChange = { v = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(v.trim()) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun ChoiceDialog(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(i) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = i == selected, onClick = { onPick(i) })
                        Text(o)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun ConfirmDialog(text: String, onYes: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onYes) { Text("تأكيد", color = Debt) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun DebitCreditRadios(debit: Boolean, dW: String, cW: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(dW, fontSize = 14.sp)
        RadioButton(selected = debit, onClick = { onChange(true) })
        Spacer(Modifier.width(8.dp))
        Text(cW, fontSize = 14.sp)
        RadioButton(selected = !debit, onClick = { onChange(false) })
    }
}
