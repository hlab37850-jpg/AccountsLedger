@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.ledger

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun visible(all: List<AccountRow>, catId: Long?, q: String, hit: Set<Long>, sort: Int): List<AccountRow> {
    val t = q.trim()
    val base = if (t.isEmpty()) all.filter { it.categoryId == catId }
    else all.filter { it.name.contains(t, true) || it.phone.contains(t) || it.id in hit }
    return when (sort) {
        1 -> base.sortedByDescending { abs(it.balance) }
        2 -> base.sortedByDescending { it.createdAt }
        3 -> base.sortedBy { it.createdAt }
        else -> base.sortedBy { it.name }
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Green)
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 14.sp)
    }
}

@Composable
fun AccountCard(
    r: AccountRow, cur: String, dW: String, cW: String, balMode: Int,
    onClick: () -> Unit, onLong: () -> Unit
) {
    val over = r.ceiling > 0 && (r.debit - r.credit) > r.ceiling
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Person, null, tint = Green)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, fontWeight = FontWeight.Bold, color = if (over) Color.Red else Color.Black)
                if (balMode == 1) Text("$dW: ${fmt(r.debit)}   $cW: ${fmt(r.credit)}", fontSize = 12.sp, color = Color.Gray)
                if (over) Text("تجاوز سقف الحساب", fontSize = 11.sp, color = Color.Red)
            }
            val b = r.balance
            Column(horizontalAlignment = Alignment.End) {
                Text("${fmt(abs(b))} $cur", fontWeight = FontWeight.Bold, color = if (b >= 0) Green else Debt)
                Text(if (b > 0) cW else if (b < 0) dW else "", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun HomeScreen(vm: LedgerVM, prefs: Prefs, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cats by vm.categories.collectAsState()
    val curs by vm.currencies.collectAsState()
    val rows by vm.rows.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val pager = rememberPagerState { cats.size }
    var menu by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var dlg by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<AccountRow?>(null) }
    var hitIds by remember { mutableStateOf(emptySet<Long>()) }

    val dW = prefs.s("dWord", "عليه")
    val cW = prefs.s("cWord", "له")
    val sortMode = prefs.i("sort", 0)
    val balMode = prefs.i("balMode", 0)
    val pinned = prefs.b("pinSearch", false)
    val curName = curs.associate { it.id to it.name }
    val cat = cats.getOrNull(pager.currentPage)

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) toast(ctx, if (Backup.backupTo(ctx, uri)) "تم حفظ النسخة الاحتياطية" else "تعذر حفظ النسخة")
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            if (Backup.restoreFrom(ctx, uri)) Backup.restart(ctx) else toast(ctx, "الملف غير صالح")
        }
    }

    LaunchedEffect(query) {
        hitIds = if (query.isBlank()) emptySet() else vm.dao.searchEntryAccounts(query.trim()).toSet()
    }

    fun closeDrawer(then: () -> Unit) {
        scope.launch { drawer.close() }
        then()
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        Modifier.fillMaxWidth().background(Green).padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(prefs.s("nameAr").ifBlank { "دفتر الحسابات" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    DrawerRow(Icons.Filled.Badge, "البيانات الشخصية") { closeDrawer { go(Screen.Personal) } }
                    DrawerRow(Icons.Filled.Settings, "الإعدادات") { closeDrawer { go(Screen.Settings) } }
                    DrawerRow(Icons.Filled.Category, "التصنيفات") { closeDrawer { go(Screen.Categories) } }
                    DrawerRow(Icons.Filled.MonetizationOn, "العملات") { closeDrawer { go(Screen.Currencies) } }
                    DrawerRow(Icons.Filled.Backup, "حفظ نسخة احتياطية") { closeDrawer { backupLauncher.launch("ledger-backup.db") } }
                    DrawerRow(Icons.Filled.Restore, "استرجاع نسخة احتياطية") { closeDrawer { restoreLauncher.launch(arrayOf("*/*")) } }
                    DrawerRow(Icons.Filled.Storage, "إرسال قاعدة البيانات") { closeDrawer { Backup.shareDb(ctx) } }
                    DrawerRow(Icons.Filled.Share, "مشاركة التطبيق") { closeDrawer { shareApp(ctx) } }
                    DrawerRow(Icons.Filled.Star, "قيّم التطبيق") { closeDrawer { rateApp(ctx) } }
                    DrawerRow(Icons.Filled.PrivacyTip, "سياسة الخصوصية") { closeDrawer { dlg = "privacy" } }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = PageBg,
            topBar = {
                TopAppBar(
                    title = { Text(cat?.name ?: "", color = Color.White, fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Filled.Menu, null, tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(Icons.Filled.Search, null, tint = Color.White)
                        }
                        IconButton(onClick = { dlg = "report" }) {
                            Icon(Icons.Filled.PictureAsPdf, null, tint = Color.White)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = Color.White) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                val menuItems = listOf(
                                    "البحث في جميع الحسابات والتفاصيل" to { searching = true },
                                    "إرسال قاعدة البيانات" to { Backup.shareDb(ctx) },
                                    "تصدير تقرير" to { dlg = "report" },
                                    "طريقة ترتيب الحسابات" to { dlg = "sort" },
                                    "طريقة عرض الرصيد في التصنيف" to { dlg = "bal" },
                                    "طريقة ترتيب التصنيفات" to { go(Screen.Categories) },
                                    "الإعدادات" to { go(Screen.Settings) },
                                    "الانتقال إلى تصنيف" to { dlg = "go" },
                                    "شارك التطبيق" to { shareApp(ctx) },
                                    "إمنحنا خمس نجمات" to { rateApp(ctx) },
                                    "حول التطبيق" to { dlg = "about" }
                                )
                                menuItems.forEach { (label, action) ->
                                    DropdownMenuItem(text = { Text(label, fontSize = 14.sp) }, onClick = { menu = false; action() })
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Green)
                )
            },
            bottomBar = {
                val catRows = rows.filter { it.categoryId == cat?.id }
                val lines = curs.mapNotNull { c ->
                    val rs = catRows.filter { it.currencyId == c.id }
                    if (rs.isEmpty() && c.id != curs.first().id) null
                    else Triple(c.name, rs.sumOf { it.debit }, rs.sumOf { it.credit })
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = { dlg = "add" }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.NoteAdd, null, tint = Green, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(color = BarGrey, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(8.dp)) {
                            lines.forEach { (name, d, c) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("$dW: ${fmt(d)}", fontSize = 14.sp)
                                    Text("$cW: ${fmt(c)}", fontSize = 14.sp)
                                }
                                Text("الرصيد: ${fmt(c - d)} $name", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                if (searching || pinned) {
                    FieldBox(query, { query = it }, "بحث في الأسماء والأرقام والتفاصيل", Icons.Filled.Search, Modifier.padding(8.dp))
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                    val c = cats.getOrNull(page)
                    if (c != null) {
                        val list = visible(rows, c.id, query, hitIds, sortMode)
                        Column {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Groups, null, tint = Green)
                                Spacer(Modifier.width(6.dp))
                                Text("(${list.size})", color = Green)
                            }
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(list, key = { it.id }) { r ->
                                    AccountCard(r, curName[r.currencyId] ?: "", dW, cW, balMode,
                                        onClick = { go(Screen.Detail(r.id)) },
                                        onLong = { picked = r; dlg = "acct" })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (dlg) {
        "add" -> if (cat != null) AddAccountDialog(vm, prefs, curs, cat.id) { dlg = "" }
        "report" -> if (cat != null) ReportDialog(vm, prefs, cat.id) { dlg = "" }
        "sort" -> ChoiceDialog(
            "طريقة ترتيب الحسابات",
            listOf("حسب الاسم", "حسب الرصيد (الأكبر أولاً)", "الأحدث أولاً", "الأقدم أولاً"),
            sortMode, { prefs.put("sort", it); dlg = "" }, { dlg = "" }
        )
        "bal" -> ChoiceDialog(
            "طريقة عرض الرصيد في التصنيف",
            listOf("الرصيد الصافي فقط", "$dW و$cW والرصيد"),
            balMode, { prefs.put("balMode", it); dlg = "" }, { dlg = "" }
        )
        "go" -> ChoiceDialog(
            "الانتقال إلى تصنيف", cats.map { it.name }, pager.currentPage,
            { i -> scope.launch { pager.scrollToPage(i) }; dlg = "" }, { dlg = "" }
        )
        "about" -> AlertDialog(
            onDismissRequest = { dlg = "" },
            title = { Text("دفتر الحسابات") },
            text = { Text("تطبيق لتسجيل الحسابات والديون ومتابعة الأرصدة. الإصدار 1.0") },
            confirmButton = { TextButton(onClick = { dlg = "" }) { Text("حسناً") } }
        )
        "privacy" -> AlertDialog(
            onDismissRequest = { dlg = "" },
            title = { Text("سياسة الخصوصية") },
            text = { Text("جميع البيانات تُخزَّن على جهازك فقط ولا تُرسل إلى أي جهة. النسخ الاحتياطية تُحفظ حيث تختار أنت.") },
            confirmButton = { TextButton(onClick = { dlg = "" }) { Text("حسناً") } }
        )
        "acct" -> picked?.let { r ->
            ChoiceDialog(
                r.name, listOf("فتح الحساب", "تعديل الاسم والهاتف", "نقل إلى تصنيف", "حذف الحساب"), -1,
                { i ->
                    when (i) {
                        0 -> { dlg = ""; go(Screen.Detail(r.id)) }
                        1 -> { dlg = "rename" }
                        2 -> { dlg = "move" }
                        else -> { dlg = "del" }
                    }
                },
                { dlg = "" }
            )
        }
        "rename" -> picked?.let { r -> EditAccountDialog(r) { n, p -> vm.editAccount(r.id, n, p); dlg = "" } }
        "move" -> picked?.let { r ->
            ChoiceDialog(
                "نقل إلى تصنيف", cats.map { it.name }, cats.indexOfFirst { it.id == r.categoryId },
                { i -> vm.moveAccount(r.id, cats[i].id); dlg = "" }, { dlg = "" }
            )
        }
        "del" -> picked?.let { r ->
            ConfirmDialog("حذف الحساب \"${r.name}\" وجميع عملياته؟", { vm.deleteAccount(r.id); dlg = "" }, { dlg = "" })
        }
    }
}

fun shareApp(ctx: android.content.Context) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "جرّب تطبيق دفتر الحسابات لتسجيل الديون والأرصدة.")
    }
    ctx.startActivity(Intent.createChooser(i, "مشاركة التطبيق"))
}

fun rateApp(ctx: android.content.Context) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ctx.packageName}")))
    } catch (e: Exception) {
        toast(ctx, "المتجر غير متوفر")
    }
}

@Composable
fun EditAccountDialog(r: AccountRow, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(r.name) }
    var phone by remember { mutableStateOf(r.phone) }
    var err by remember { mutableStateOf<String?>(null) }
    val pickForName = rememberContactPicker { n, p -> name = n; err = null; if (phone.isBlank()) phone = p }
    val pickForPhone = rememberContactPicker { n, p -> phone = p; if (name.isBlank()) name = n }
    CardDialog("تعديل الحساب", onDismiss = { onSave(r.name, r.phone) }) {
        FieldBox(name, { name = it; err = null }, "إسم الحساب", Icons.Filled.Person, error = err, onIconClick = { pickForName() })
        FieldBox(phone, { phone = it }, "رقم الهاتف", Icons.Filled.Phone, keyboard = KeyboardType.Phone, onIconClick = { pickForPhone() })
        Button(onClick = { if (name.isBlank()) err = "لا يمكن ترك الحقل خالياً" else onSave(name.trim(), phone.trim()) }) { Text("حفظ") }
    }
}

@Composable
fun AddAccountDialog(vm: LedgerVM, prefs: Prefs, curs: List<Currency>, catId: Long, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val dW = prefs.s("dWord", "عليه")
    val cW = prefs.s("cWord", "له")
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var ceiling by remember { mutableStateOf("") }
    var curId by remember { mutableStateOf(curs.firstOrNull()?.id ?: 1L) }
    var date by remember { mutableStateOf(today()) }
    var debit by remember {
        mutableStateOf(
            when (prefs.i("defMode", 0)) { 0 -> true; 1 -> false; else -> prefs.b("lastDebit", true) }
        )
    }
    var err by remember { mutableStateOf<String?>(null) }
    var pickCur by remember { mutableStateOf(false) }
    var calc by remember { mutableStateOf(false) }
    val pickForName = rememberContactPicker { n, p -> name = n; err = null; if (phone.isBlank()) phone = p }
    val pickForPhone = rememberContactPicker { n, p -> phone = p; if (name.isBlank()) name = n }

    CardDialog("إضافة حساب جديد", onDismiss) {
        FieldBox(name, { name = it; err = null }, "إسم الحساب", Icons.Filled.Person, error = err, onIconClick = { pickForName() })
        FieldBox(amount, { amount = it }, "المبلغ", Icons.Filled.Calculate, keyboard = KeyboardType.Decimal, onIconClick = { calc = true })
        if (prefs.b("showAmount", true) && num(amount) > 0) {
            Text(fmt(num(amount)), color = GreenDark, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        if (prefs.b("ceilingField", false)) {
            FieldBox(ceiling, { ceiling = it }, "سقف الحساب (اختياري)", Icons.Filled.Lock, keyboard = KeyboardType.Decimal)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("العملة", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Surface(
                color = Color(0xFFD3EBE2), shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).clickable { pickCur = true }
            ) {
                Text(
                    curs.firstOrNull { it.id == curId }?.name ?: "", textAlign = TextAlign.Center,
                    modifier = Modifier.padding(10.dp).fillMaxWidth(), fontWeight = FontWeight.Bold
                )
            }
        }
        ClickBox(date, Icons.Filled.DateRange, { pickDate(ctx, date) { date = it } })
        FieldBox(details, { details = it }, "التفاصيل", Icons.Filled.Description)
        FieldBox(phone, { phone = it }, "رقم الهاتف", Icons.Filled.Phone, keyboard = KeyboardType.Phone, onIconClick = { pickForPhone() })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DebitCreditRadios(debit, dW, cW) { debit = it }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (name.isBlank()) {
                    err = "لا يمكن ترك الحقل خالياً"
                } else {
                    vm.addAccount(
                        name.trim(), phone.trim(), catId, curId, num(ceiling),
                        num(amount), debit, stamp(prefs, date), details.trim()
                    )
                    prefs.put("lastDebit", debit)
                    beep(prefs)
                    onDismiss()
                }
            }) { Text("حفظ") }
        }
    }
    if (calc) CalculatorDialog(amount, { amount = it; calc = false }, { calc = false })
    if (pickCur) {
        ChoiceDialog(
            "حدد نوع العملة", curs.map { it.name }, curs.indexOfFirst { it.id == curId },
            { i -> curId = curs[i].id; pickCur = false }, { pickCur = false }
        )
    }
}

@Composable
fun ExportButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(34.dp))
        Text(label, fontSize = 11.sp)
    }
}

@Composable
fun ExportRow(onPdf: () -> Unit, onCsv: () -> Unit, onWa: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        ExportButton(Icons.Filled.PictureAsPdf, "PDF", Color(0xFFD32F2F), onPdf)
        ExportButton(Icons.Filled.TableChart, "Excel", Color(0xFF1E7B34), onCsv)
        ExportButton(Icons.Filled.Send, "واتساب", Color(0xFF25D366), onWa)
    }
}

@Composable
fun ReportDialog(vm: LedgerVM, prefs: Prefs, catId: Long, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cats by vm.categories.collectAsState()
    val curs by vm.currencies.collectAsState()
    val rows by vm.rows.collectAsState()
    val dW = prefs.s("dWord", "عليه")
    val cW = prefs.s("cWord", "له")
    var type by remember { mutableIntStateOf(0) }
    var showSort by remember { mutableStateOf(false) }
    var sortBal by remember { mutableStateOf(false) }
    var useRange by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf(today().take(8) + "01") }
    var to by remember { mutableStateOf(today()) }
    val names = listOf(
        "إجمالي المبالغ", "تفصيل جميع المبالغ", "إجمالي المبالغ شهرياً",
        "إجمالي التصنيفات والعملات", "تفصيلي المبالغ شهرياً للتصنيف الحالي"
    )

    fun exportWith(action: (ReportDoc) -> Unit) {
        scope.launch {
            val doc = ReportBuilder.build(
                type, vm.dao.allEntryRows(), rows, cats, curs, catId,
                if (useRange) from else null, if (useRange) to else null, sortBal, dW, cW
            )
            action(doc)
        }
    }

    CardDialog("التقارير", onDismiss) {
        names.forEachIndexed { i, n ->
            Row(Modifier.fillMaxWidth().clickable { type = i }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = type == i, onClick = { type = i })
                Text(n, fontSize = 14.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showSort, onCheckedChange = { showSort = it })
            Text("إظهار خيارات ترتيب التقرير", fontSize = 14.sp)
        }
        if (showSort) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sortBal, onCheckedChange = { sortBal = it })
                Text("الترتيب حسب الرصيد (الأكبر أولاً)", fontSize = 14.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useRange, onCheckedChange = { useRange = it })
            Text("تحديد تاريخ الفترة", fontSize = 14.sp)
        }
        if (useRange) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = { pickDate(ctx, from) { from = it } }) { Text("من: $from", fontSize = 12.sp) }
                OutlinedButton(onClick = { pickDate(ctx, to) { to = it } }) { Text("إلى: $to", fontSize = 12.sp) }
            }
        }
        ExportRow(
            onPdf = { exportWith { Exporter.sharePdf(ctx, it, prefs) } },
            onCsv = { exportWith { Exporter.shareCsv(ctx, it) } },
            onWa = { exportWith { Exporter.shareWhatsapp(ctx, it) } }
        )
    }
}
