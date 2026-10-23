@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.ledger

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

fun sha256(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

@Composable
fun SubTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, color = Color.White, fontSize = 16.sp) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Green)
    )
}

// ───────────────────────── Account details ─────────────────────────

@Composable
fun AddEntryDialog(vm: LedgerVM, prefs: Prefs, accountId: Long, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val dW = prefs.s("dWord", "عليه")
    val cW = prefs.s("cWord", "له")
    var amount by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today()) }
    var debit by remember {
        mutableStateOf(when (prefs.i("defMode", 0)) { 0 -> true; 1 -> false; else -> prefs.b("lastDebit", true) })
    }
    var err by remember { mutableStateOf<String?>(null) }
    CardDialog("إضافة عملية", onDismiss) {
        FieldBox(amount, { amount = it; err = null }, "المبلغ", Icons.Filled.Calculate, keyboard = KeyboardType.Decimal, error = err)
        ClickBox(date, Icons.Filled.DateRange, { pickDate(ctx, date) { date = it } })
        FieldBox(details, { details = it }, "التفاصيل", Icons.Filled.Description)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DebitCreditRadios(debit, dW, cW) { debit = it }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (num(amount) <= 0) {
                    err = "أدخل مبلغاً صحيحاً"
                } else {
                    vm.addEntry(accountId, num(amount), debit, stamp(prefs, date), details.trim())
                    prefs.put("lastDebit", debit)
                    beep(prefs)
                    onDismiss()
                }
            }) { Text("حفظ") }
        }
    }
}

@Composable
fun AccountScreen(vm: LedgerVM, prefs: Prefs, id: Long, back: () -> Unit) {
    val ctx = LocalContext.current
    val rows by vm.rows.collectAsState()
    val curs by vm.currencies.collectAsState()
    val entries by remember(id) { vm.entries(id) }.collectAsState(initial = emptyList())
    var dlg by remember { mutableStateOf("") }
    var delEntry by remember { mutableStateOf<Entry?>(null) }
    val dW = prefs.s("dWord", "عليه")
    val cW = prefs.s("cWord", "له")
    val row = rows.firstOrNull { it.id == id }
    if (row == null) return
    val cur = curs.firstOrNull { it.id == row.currencyId }?.name ?: ""

    fun statement(): ReportDoc {
        val list = entries.sortedBy { it.date }
        return ReportDoc(
            "كشف حساب: ${row.name}",
            listOf("التاريخ", "التفاصيل", dW, cW),
            list.map { listOf(it.date, it.details, if (it.isDebit) fmt(it.amount) else "", if (!it.isDebit) fmt(it.amount) else "") },
            listOf("$dW ${fmt(row.debit)}  |  $cW ${fmt(row.credit)}  |  الرصيد ${fmt(row.balance)} $cur")
        )
    }

    Scaffold(
        containerColor = PageBg,
        topBar = {
            SubTopBar(row.name, back) {
                if (row.phone.isNotBlank()) {
                    IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${row.phone}"))) }) {
                        Icon(Icons.Filled.Call, null, tint = Color.White)
                    }
                }
                IconButton(onClick = { dlg = "share" }) { Icon(Icons.Filled.Share, null, tint = Color.White) }
                IconButton(onClick = { dlg = "delAcc" }) { Icon(Icons.Filled.Delete, null, tint = Color.White) }
            }
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { dlg = "add" }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.NoteAdd, null, tint = Green, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(6.dp))
                Surface(color = BarGrey, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$dW: ${fmt(row.debit)}", fontSize = 14.sp)
                            Text("$cW: ${fmt(row.credit)}", fontSize = 14.sp)
                        }
                        Text("الرصيد: ${fmt(row.balance)} $cur", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 14.sp)
                    }
                }
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            if (row.phone.isNotBlank()) {
                item { Text("☎ ${row.phone}", Modifier.padding(12.dp), color = Color.Gray, fontSize = 13.sp) }
            }
            items(entries, key = { it.id }) { e ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                        .combinedClickable(onClick = {}, onLongClick = { delEntry = e; dlg = "delEntry" })
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.date, fontSize = 12.sp, color = Color.Gray)
                            if (e.details.isNotBlank()) Text(e.details, fontSize = 14.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${fmt(e.amount)} $cur", fontWeight = FontWeight.Bold, color = if (e.isDebit) Debt else Green)
                            Text(if (e.isDebit) dW else cW, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }

    when (dlg) {
        "add" -> AddEntryDialog(vm, prefs, id) { dlg = "" }
        "share" -> CardDialog("مشاركة كشف الحساب", { dlg = "" }) {
            ExportRow(
                onPdf = { Exporter.sharePdf(ctx, statement(), prefs) },
                onCsv = { Exporter.shareCsv(ctx, statement()) },
                onWa = { Exporter.shareWhatsapp(ctx, statement()) }
            )
        }
        "delAcc" -> ConfirmDialog("حذف الحساب \"${row.name}\" وجميع عملياته؟", { vm.deleteAccount(id); dlg = ""; back() }, { dlg = "" })
        "delEntry" -> delEntry?.let { e ->
            ConfirmDialog("حذف هذه العملية؟", { vm.deleteEntry(e.id); dlg = "" }, { dlg = "" })
        }
    }
}

// ───────────────────────── Settings ─────────────────────────

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(vm: LedgerVM, prefs: Prefs, back: () -> Unit) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf("") }
    val dW = prefs.s("dWord", "عليه")
    val cW = prefs.s("cWord", "له")
    val defMode = prefs.i("defMode", 0)

    Scaffold(containerColor = PageBg, topBar = { SubTopBar("الإعدادات العامة", back) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            SwitchRow("الحماية بالبصمة", prefs.b("fp")) { on ->
                if (!on) {
                    prefs.put("fp", false)
                } else if (BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    == BiometricManager.BIOMETRIC_SUCCESS
                ) {
                    prefs.put("fp", true)
                } else {
                    toast(ctx, "لا توجد بصمة مسجلة على الجهاز")
                }
            }
            SwitchRow("الحماية بكلمة مرور", prefs.s("pass").isNotEmpty()) { on ->
                if (on) dlg = "pass" else prefs.put("pass", "")
            }
            SwitchRow("تثبيت قائمة الحسابات عند البحث", prefs.b("pinSearch", false)) { prefs.put("pinSearch", it) }
            SwitchRow("إظهار المبلغ كتابة عند الإدخال", prefs.b("showAmount", true)) { prefs.put("showAmount", it) }
            SwitchRow("إظهار/إخفاء سقف الحساب عند اضافة حساب", prefs.b("ceilingField", false)) { prefs.put("ceilingField", it) }
            SwitchRow("تفعيل التنبيه الصوتي", prefs.b("sound", true)) { prefs.put("sound", it) }
            SwitchRow("إظهار/إخفاء الوقت في العمليات", prefs.b("showTime", true)) { prefs.put("showTime", it) }
            Row(
                Modifier.fillMaxWidth().clickable { dlg = "close" }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("الإغلاق السنوي للحسابات", Modifier.weight(1f), fontSize = 14.sp)
                Icon(Icons.Filled.Edit, null, tint = Green)
            }
            Surface(color = Tint, modifier = Modifier.fillMaxWidth().padding(8.dp), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("تغيير عبارة (مدين)", Modifier.weight(1f), fontSize = 14.sp)
                        ClickBox(dW, Icons.Filled.Edit, { dlg = "dWord" }, Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("تغيير عبارة (دائن)", Modifier.weight(1f), fontSize = 14.sp)
                        ClickBox(cW, Icons.Filled.Edit, { dlg = "cWord" }, Modifier.weight(1f))
                    }
                    Text("تحديد الافتراضي عند إضافة عملية جديدة", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GreenDark)
                    listOf(dW, cW, "الاحتفاظ باخر عملية").forEachIndexed { i, label ->
                        Row(
                            Modifier.fillMaxWidth().clickable { prefs.put("defMode", i) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = defMode == i, onClick = { prefs.put("defMode", i) })
                            Text(label, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    when (dlg) {
        "pass" -> TextEditDialog("كلمة المرور الجديدة", "", {
            if (it.length >= 4) { prefs.put("pass", sha256(it)); dlg = "" } else toast(ctx, "٤ أحرف على الأقل")
        }, { dlg = "" })
        "dWord" -> TextEditDialog("تحديث النص", dW, { if (it.isNotBlank()) prefs.put("dWord", it); dlg = "" }, { dlg = "" })
        "cWord" -> TextEditDialog("تحديث النص", cW, { if (it.isNotBlank()) prefs.put("cWord", it); dlg = "" }, { dlg = "" })
        "close" -> ConfirmDialog(
            "سيتم دمج عمليات كل حساب في رصيد مرحّل واحد وحذف التفاصيل السابقة. يُنصح بحفظ نسخة احتياطية أولاً. متابعة؟",
            { vm.closeYear(today(), "رصيد مرحّل") { toast(ctx, "تم الإغلاق السنوي") }; dlg = "" },
            { dlg = "" }
        )
    }
}

// ───────────────────────── Categories & currencies ─────────────────────────

@Composable
fun CategoriesScreen(vm: LedgerVM, back: () -> Unit) {
    val ctx = LocalContext.current
    val cats by vm.categories.collectAsState()
    val rows by vm.rows.collectAsState()
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Category?>(null) }
    Scaffold(
        containerColor = PageBg,
        topBar = { SubTopBar("إدارة التصنيفات", back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { add = true }, containerColor = Green) { Icon(Icons.Filled.Add, null, tint = Color.White) }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            itemsIndexed(cats, key = { _, c -> c.id }) { i, c ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("${rows.count { it.categoryId == c.id }}", color = Color.Gray)
                        IconButton(onClick = { if (i > 0) vm.swapCategories(c, cats[i - 1]) }) { Icon(Icons.Filled.ArrowUpward, null, tint = Green) }
                        IconButton(onClick = { if (i < cats.size - 1) vm.swapCategories(c, cats[i + 1]) }) { Icon(Icons.Filled.ArrowDownward, null, tint = Green) }
                        IconButton(onClick = { edit = c }) { Icon(Icons.Filled.Edit, null, tint = Green) }
                        IconButton(onClick = {
                            vm.deleteCategory(c.id) { ok -> if (!ok) toast(ctx, "لا يمكن حذف تصنيف يحتوي حسابات أو التصنيف الأخير") }
                        }) { Icon(Icons.Filled.Delete, null, tint = Debt) }
                    }
                }
            }
        }
    }
    if (add) TextEditDialog("إدخل إسم التصنيف", "", { if (it.isNotBlank()) vm.addCategory(it); add = false }, { add = false })
    edit?.let { c -> TextEditDialog("تعديل التصنيف", c.name, { if (it.isNotBlank()) vm.renameCategory(c.id, it); edit = null }, { edit = null }) }
}

@Composable
fun CurrenciesScreen(vm: LedgerVM, back: () -> Unit) {
    val ctx = LocalContext.current
    val curs by vm.currencies.collectAsState()
    val rows by vm.rows.collectAsState()
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Currency?>(null) }
    Scaffold(
        containerColor = PageBg,
        topBar = { SubTopBar("إدارة العملات", back) },
        floatingActionButton = {
            FloatingActionButton(onClick = { add = true }, containerColor = Green) { Icon(Icons.Filled.Add, null, tint = Color.White) }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(curs, key = { it.id }) { c ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("${rows.count { it.currencyId == c.id }}", color = Color.Gray)
                        IconButton(onClick = { edit = c }) { Icon(Icons.Filled.Edit, null, tint = Green) }
                        IconButton(onClick = {
                            vm.deleteCurrency(c.id) { ok -> if (!ok) toast(ctx, "لا يمكن حذف عملة مستخدمة أو العملة الأخيرة") }
                        }) { Icon(Icons.Filled.Delete, null, tint = Debt) }
                    }
                }
            }
        }
    }
    if (add) TextEditDialog("إدخل إسم العملة", "", { if (it.isNotBlank()) vm.addCurrency(it); add = false }, { add = false })
    edit?.let { c -> TextEditDialog("تعديل العملة", c.name, { if (it.isNotBlank()) vm.renameCurrency(c.id, it); edit = null }, { edit = null }) }
}

// ───────────────────────── Personal data ─────────────────────────

fun saveImage(ctx: Context, uri: Uri, name: String) {
    try {
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return
        val scale = 512f / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        File(ctx.filesDir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } catch (e: Exception) {
        toast(ctx, "تعذر تحميل الصورة")
    }
}

@Composable
fun PersonalScreen(prefs: Prefs, back: () -> Unit) {
    val ctx = LocalContext.current
    var imgTick by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<Pair<String, String>?>(null) }
    val logo = remember(imgTick) { Exporter.loadImage(ctx, "logo") }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { saveImage(ctx, uri, "logo"); imgTick++ }
    }
    val pickStamp = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { saveImage(ctx, uri, "stamp"); prefs.put("stamp", true); imgTick++ }
    }
    val fields = listOf(
        Triple("nameAr", "الإسم (عربي)", ""), Triple("nameEn", "الإسم (انجليزي)", ""),
        Triple("addrAr", "العنوان (عربي)", ""), Triple("addrEn", "العنوان (انجليزي)", ""),
        Triple("phone", "رقم الهاتف", ""), Triple("email", "البريد", "")
    )
    Scaffold(containerColor = PageBg, topBar = { SubTopBar("البيانات الشخصية", back) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (logo != null) Image(logo.asImageBitmap(), null, Modifier.size(120.dp))
            else Icon(Icons.Filled.AccountBalanceWallet, null, tint = Green, modifier = Modifier.size(100.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickLogo.launch("image/*") }) { Text("تغيير الشعار") }
                OutlinedButton(onClick = { File(ctx.filesDir, "logo.png").delete(); imgTick++ }) { Text("حذف الشعار") }
            }
            Text("البيانات التي تظهر في ترويسة التقارير", fontSize = 12.sp, color = Color.Gray)
            fields.forEach { (key, label, _) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.width(110.dp), fontSize = 13.sp)
                    ClickBox(prefs.s(key).ifBlank { "—" }, null, { edit = key to label }, Modifier.weight(1f))
                }
            }
            SwitchRow("إضافة توقيع", prefs.b("signature")) { prefs.put("signature", it) }
            SwitchRow("اختيار الختم", prefs.b("stamp")) { on ->
                if (on) pickStamp.launch("image/*") else prefs.put("stamp", false)
            }
        }
    }
    edit?.let { (key, label) ->
        TextEditDialog(label, prefs.s(key), { prefs.put(key, it); edit = null }, { edit = null })
    }
}

// ───────────────────────── Lock screen ─────────────────────────

@Composable
fun LockScreen(prefs: Prefs, act: FragmentActivity, onUnlock: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val hasPass = prefs.s("pass").isNotEmpty()
    val hasFp = prefs.b("fp")

    fun askBiometric() {
        val ok = BiometricManager.from(act).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        if (!ok) {
            if (!hasPass) onUnlock()
            return
        }
        val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onUnlock() }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("فتح التطبيق")
            .setNegativeButtonText("إلغاء")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(Unit) { if (hasFp) askBiometric() }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Lock, null, tint = Green, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("التطبيق مقفل", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        if (hasPass) {
            OutlinedTextField(
                value = pw, onValueChange = { pw = it; wrong = false }, singleLine = true,
                label = { Text("كلمة المرور") }, isError = wrong,
                visualTransformation = PasswordVisualTransformation()
            )
            if (wrong) Text("كلمة المرور غير صحيحة", color = Color.Red, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { if (sha256(pw) == prefs.s("pass")) onUnlock() else wrong = true }) { Text("دخول") }
        }
        if (hasFp) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { askBiometric() }) {
                Icon(Icons.Filled.Fingerprint, null)
                Spacer(Modifier.width(6.dp))
                Text("البصمة")
            }
        }
    }
}
