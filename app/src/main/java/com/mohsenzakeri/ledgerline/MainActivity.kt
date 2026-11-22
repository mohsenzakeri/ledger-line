package com.mohsenzakeri.ledgerline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.lazy.itemsIndexed
import java.time.YearMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohsenzakeri.ledgerline.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = LedgerDatabase.get(this).ledgerDao()
        setContent { LedgerLineApp(dao) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("+ Add new…") }, onClick = {
                expanded = false
                newCategory = ""
                showAddDialog = true
            })
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add $label") },
            text = {
                OutlinedTextField(value = newCategory, onValueChange = { newCategory = it }, label = { Text("Name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleaned = newCategory.trim()
                    if (cleaned.isNotEmpty()) onAdd(cleaned)
                    showAddDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

private data class DraftLine(
    val amount: String = "",
    val category: String = "",
    val secondary: String = "",
    val necessity: Int = 3,
    val details: String = "",
    val notes: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerLineApp(dao: LedgerDao) {
    var screen by remember { mutableStateOf("home") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var search by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("Month") }
    var historyCategory by remember { mutableStateOf("All") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var retailer by remember { mutableStateOf("") }
    var receiptTotal by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf(listOf(DraftLine())) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("ledgerline_preferences", android.content.Context.MODE_PRIVATE) }
    val defaultCategories = listOf("bills", "administration", "groceries", "food", "health", "snacks", "transportation", "entertainment", "home", "clothes", "vacation", "decoration", "wedding", "beauty", "socializing")
    var categories by remember { mutableStateOf(preferences.getString("categories", defaultCategories.joinToString("\n"))!!.lines().filter { it.isNotBlank() }) }
    var secondaryCategories by remember { mutableStateOf(preferences.getString("secondary_categories", "food\ndecoration\nsubscription\nsocializing\ntransportation")!!.lines().filter { it.isNotBlank() }) }
    val purchases by dao.observePurchases().collectAsStateWithLifecycle(emptyList())
    val total by dao.observeTotalSpent().collectAsStateWithLifecycle(0.0)
    val categoryTotals by dao.observeCategoryTotals().collectAsStateWithLifecycle(emptyList())
    val allEntries by dao.observeAllEntries().collectAsStateWithLifecycle(emptyList())
    val spendingByPurchase = remember(allEntries) { allEntries.groupBy { it.purchaseId }.mapValues { (_, entries) -> entries.sumOf { it.amount } } }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.bufferedReader().use { LedgerCsv.readRows(it) }
                }
                val validated = parsed.mapIndexed { index, row ->
                    val amount = row.getValue("Amount").trim().replace(",", "").toDoubleOrNull()
                        ?: error("Invalid amount on CSV row " + (index + 2))
                    require(amount > 0 && amount.isFinite()) { "Invalid amount on CSV row " + (index + 2) }
                    require(row.getValue("Date").isNotBlank()) { "Missing date on CSV row " + (index + 2) }
                    require(row.getValue("Category").isNotBlank()) { "Missing category on CSV row " + (index + 2) }
                    val necessityText = row.getValue("Necessity").trim()
                    require(necessityText.isBlank() || necessityText.toIntOrNull() in 1..5) { "Invalid necessity on CSV row " + (index + 2) }
                    row to amount
                }
                withContext(Dispatchers.IO) { dao.importCsvRows(validated) }
                message = "Imported " + validated.size + " rows"
            } catch (e: Exception) { message = "Import failed: ${e.message}" }
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            try {
                val byId = dao.getAllPurchases().associateBy { it.id }
                val rows = dao.getAllEntries().mapNotNull { e ->
                    val p = byId[e.purchaseId] ?: return@mapNotNull null
                    listOf(p.date, e.amount.toString(), e.legacyPerson, e.category, e.secondaryCategory,
                        e.necessity?.toString() ?: "", p.retailer, e.details, e.moreInformation)
                }
                context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(LedgerCsv.encode(rows)) }
                message = "CSV exported"
            } catch (e: Exception) { message = "Export failed: ${e.message}" }
        }
    }

    if (deleteId != null) AlertDialog(
        onDismissRequest = { deleteId = null },
        title = { Text("Delete purchase?") },
        text = { Text("All expense lines in this purchase will be deleted.") },
        confirmButton = { TextButton(onClick = {
            val id = deleteId!!
            deleteId = null
            scope.launch { dao.deletePurchase(id); message = "Purchase deleted" }
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
    )
    MaterialTheme(colorScheme = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF176B5B),
        secondary = androidx.compose.ui.graphics.Color(0xFF64877E),
        background = androidx.compose.ui.graphics.Color(0xFFF6F8F7)
    )) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("LedgerLine", fontWeight = FontWeight.Bold)
                            Text("Personal finance, clearly organized", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { editingId = null; date = LocalDate.now().toString(); retailer = ""; receiptTotal = ""; lines = listOf(DraftLine()); screen = "add" }) {
                    Icon(Icons.Default.Add, contentDescription = "Add purchase")
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = screen == "home", onClick = { screen = "home" }, icon = { Icon(Icons.Default.ReceiptLong, null) }, label = { Text("Home") })
                    NavigationBarItem(selected = screen == "history", onClick = { screen = "history" }, icon = { Icon(Icons.Default.History, null) }, label = { Text("History") })
                    NavigationBarItem(selected = screen == "add", onClick = { editingId = null; date = LocalDate.now().toString(); retailer = ""; receiptTotal = ""; lines = listOf(DraftLine()); screen = "add" }, icon = { Icon(Icons.Default.Add, null) }, label = { Text("Add") })
                    NavigationBarItem(selected = screen == "analytics", onClick = { screen = "analytics" }, icon = { Icon(Icons.Default.Analytics, null) }, label = { Text("Analytics") })
                    NavigationBarItem(selected = screen == "settings", onClick = { screen = "settings" }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                }
            }
        ) { padding ->
            if (screen == "home") {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                Text("All-time recorded spending", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                                Text("$" + "%,.2f".format(total), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                                Text("${purchases.size} purchases recorded", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { importCsv.launch(arrayOf("text/*", "application/csv")) }) { Text("Import CSV") }
                            OutlinedButton(onClick = { exportCsv.launch("ledgerline.csv") }) { Text("Export CSV") }
                        }
                        if (message.isNotBlank()) Text(message)
                    }
                    if (categoryTotals.isNotEmpty()) {
                        item { Text("Spending by category", style = MaterialTheme.typography.titleLarge) }
                        items(categoryTotals) { entry ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(entry.category)
                                Text("$" + "%.2f".format(entry.total))
                            }
                        }
                    }
                    item { Text("Recent purchases", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                    if (purchases.isEmpty()) {
                        item {
                            Card {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ReceiptLong, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("Your ledger starts here", fontWeight = FontWeight.SemiBold)
                                    Text("Add a purchase and split its receipt into as many categories as you need.", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    } else {
                        items(purchases, key = { it.id }) { purchase ->
                            Card {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(purchase.retailer.ifBlank { "Purchase" }, fontWeight = FontWeight.SemiBold)
                                    Text(purchase.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (purchase.receiptTotal != null) Text("$" + "%.2f".format(purchase.receiptTotal), style = MaterialTheme.typography.titleMedium)
                                    Row {
                                        TextButton(onClick = {
                                            scope.launch {
                                                val entries = dao.getEntries(purchase.id)
                                                editingId = purchase.id
                                                date = purchase.date
                                                retailer = purchase.retailer
                                                receiptTotal = purchase.receiptTotal?.toString() ?: ""
                                                lines = entries.map { DraftLine(it.amount.toString(), it.category, it.secondaryCategory,
                                                    it.necessity ?: 3, it.details, it.moreInformation) }.ifEmpty { listOf(DraftLine()) }
                                                screen = "add"
                                            }
                                        }) { Text("Edit") }
                                        TextButton(onClick = { deleteId = purchase.id }) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (screen == "history") {
                val filtered = purchases.filter { p ->
                    (search.isBlank() || p.retailer.contains(search, ignoreCase = true) || p.date.contains(search)) &&
                    (historyCategory == "All" || allEntries.any { it.purchaseId == p.id && it.category == historyCategory })
                }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("Purchase history", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                    item { OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search retailer or date") }, modifier = Modifier.fillMaxWidth()) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = historyCategory == "All", onClick = { historyCategory = "All" }, label = { Text("All") })
                            categories.take(3).forEach { cat ->
                                FilterChip(selected = historyCategory == cat, onClick = { historyCategory = cat }, label = { Text(cat) })
                            }
                        }
                    }
                    items(filtered, key = { it.id }) { p ->
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(p.retailer.ifBlank { "Purchase" }, fontWeight = FontWeight.SemiBold)
                                    Text("$" + "%,.2f".format(spendingByPurchase[p.id] ?: 0.0))
                                }
                                Text(p.date, style = MaterialTheme.typography.labelMedium)
                                Row {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val entries = dao.getEntries(p.id)
                                            editingId = p.id; date = p.date; retailer = p.retailer
                                            receiptTotal = p.receiptTotal?.toString() ?: ""
                                            lines = entries.map { DraftLine(it.amount.toString(), it.category, it.secondaryCategory, it.necessity ?: 3, it.details, it.moreInformation) }.ifEmpty { listOf(DraftLine()) }
                                            screen = "add"
                                        }
                                    }) { Text("Edit") }
                                    TextButton(onClick = { deleteId = p.id }) { Text("Delete") }
                                }
                            }
                        }
                    }
                    if (filtered.isEmpty()) item { Text("No matching purchases") }
                }
            } else if (screen == "analytics") {
                val monthTotals = remember(purchases, spendingByPurchase) {
                    purchases.groupBy { it.date.take(7) }.mapValues { (_, ps) -> ps.sumOf { spendingByPurchase[it.id] ?: 0.0 } }.toSortedMap()
                }
                val month = YearMonth.now().toString()
                val monthSpent = purchases.filter { it.date.startsWith(month) }.sumOf { spendingByPurchase[it.id] ?: 0.0 }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { Text("Analytics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                Text("This month")
                                Text("$" + "%,.2f".format(monthSpent), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Text("All-time: $" + "%,.2f".format(total))
                            }
                        }
                    }
                    item { Text("Monthly spending", style = MaterialTheme.typography.titleLarge) }
                    items(monthTotals.toList().takeLast(12).reversed()) { (monthKey, value) ->
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(monthKey)
                                Text("$" + "%,.2f".format(value))
                            }
                            LinearProgressIndicator(progress = { if (total > 0) (value / total).toFloat().coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                    item { Text("By category", style = MaterialTheme.typography.titleLarge) }
                    items(categoryTotals) { category ->
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(category.category.ifBlank { "Uncategorized" })
                                Text("$" + "%,.2f".format(category.total))
                            }
                            LinearProgressIndicator(progress = { if (total > 0) (category.total / total).toFloat().coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            } else if (screen == "settings") {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { Text("Settings & data", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Your data", style = MaterialTheme.typography.titleMedium)
                                Text("Stored locally on this device. Export a CSV to make your own backup.")
                                OutlinedButton(onClick = { importCsv.launch(arrayOf("text/*", "application/csv")) }) { Text("Import spreadsheet CSV") }
                                OutlinedButton(onClick = { exportCsv.launch("ledgerline-backup.csv") }) { Text("Export CSV backup") }
                                if (message.isNotBlank()) Text(message)
                            }
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Categories", style = MaterialTheme.typography.titleMedium)
                                Text(categories.joinToString(", "))
                                Text("Add new categories using the purchase entry dropdowns.")
                            }
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Cloud backup & sync", style = MaterialTheme.typography.titleMedium)
                                Text("Not connected yet. Your transactions are not uploaded to a cloud account.")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Text(if (editingId == null) "New purchase" else "Edit purchase", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                    item {
                        OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = retailer, onValueChange = { retailer = it }, label = { Text("Retailer") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        val retailerSuggestions = purchases.map { it.retailer.trim() }.filter { it.isNotBlank() && it.contains(retailer, ignoreCase = true) && it != retailer }.distinct().take(4)
                        if (retailer.isNotBlank() && retailerSuggestions.isNotEmpty()) {
                            Text("Previously used retailers", style = MaterialTheme.typography.labelSmall)
                            retailerSuggestions.forEach { suggestion ->
                                TextButton(onClick = { retailer = suggestion }) { Text(suggestion) }
                            }
                        }
                        OutlinedTextField(value = receiptTotal, onValueChange = { receiptTotal = it }, label = { Text("Receipt total (optional)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                    items(lines.size) { index ->
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Expense line ${index + 1}", fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(value = lines[index].amount, onValueChange = { v -> lines = lines.toMutableList().also { it[index] = it[index].copy(amount = v) } }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                                CategoryDropdown(
                                    label = "Category",
                                    value = lines[index].category,
                                    options = categories,
                                    onSelect = { v -> lines = lines.toMutableList().also { it[index] = it[index].copy(category = v) } },
                                    onAdd = { v -> categories = (categories + v).distinct(); preferences.edit().putString("categories", categories.joinToString("\n")).apply(); lines = lines.toMutableList().also { it[index] = it[index].copy(category = v) } }
                                )
                                CategoryDropdown(
                                    label = "Secondary category",
                                    value = lines[index].secondary,
                                    options = secondaryCategories,
                                    onSelect = { v -> lines = lines.toMutableList().also { it[index] = it[index].copy(secondary = v) } },
                                    onAdd = { v -> secondaryCategories = (secondaryCategories + v).distinct(); preferences.edit().putString("secondary_categories", secondaryCategories.joinToString("\n")).apply(); lines = lines.toMutableList().also { it[index] = it[index].copy(secondary = v) } }
                                )
                                OutlinedTextField(value = lines[index].necessity.toString(), onValueChange = { v -> v.toIntOrNull()?.let { n -> if (n in 1..5) lines = lines.toMutableList().also { it[index] = it[index].copy(necessity = n) } } }, label = { Text("Necessity (1–5)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                                OutlinedTextField(value = lines[index].details, onValueChange = { v -> lines = lines.toMutableList().also { it[index] = it[index].copy(details = v) } }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                                OutlinedTextField(value = lines[index].notes, onValueChange = { v -> lines = lines.toMutableList().also { it[index] = it[index].copy(notes = v) } }, label = { Text("More information") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                                if (lines.size > 1) TextButton(onClick = { lines = lines.toMutableList().also { it.removeAt(index) } }) { Text("Remove this line") }
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { lines = lines + DraftLine() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add another expense line")
                        }
                    }
                    item {
                        val lineTotal = lines.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                        Text("Expense lines total: $" + "%.2f".format(lineTotal), fontWeight = FontWeight.SemiBold)
                        Button(
                            onClick = {
                                val valid = date.isNotBlank() && lines.isNotEmpty() && lines.all { (it.amount.toDoubleOrNull() ?: 0.0) > 0 && it.category.isNotBlank() }
                                if (!valid) {
                                    message = "Enter a date, a positive amount, and a category for every line."
                                } else {
                                    scope.launch {
                                        val id = editingId ?: dao.insertPurchase(PurchaseEntity(date = date, retailer = retailer, receiptTotal = receiptTotal.toDoubleOrNull() ?: lineTotal))
                                        val prior = if (editingId != null) dao.getEntries(id) else emptyList()
                                        val entries = lines.mapIndexed { index, line -> ExpenseEntryEntity(purchaseId = id, amount = line.amount.toDouble(), category = line.category.trim(), secondaryCategory = line.secondary.trim(), necessity = line.necessity, details = line.details, moreInformation = line.notes, legacyPerson = prior.getOrNull(index)?.legacyPerson ?: "") }
                                        if (editingId != null) dao.replacePurchase(PurchaseEntity(id = id, date = date, retailer = retailer, receiptTotal = receiptTotal.toDoubleOrNull() ?: lineTotal), entries)
                                        else dao.insertEntries(entries)
                                        editingId = null
                                        date = LocalDate.now().toString(); retailer = ""; receiptTotal = ""; lines = listOf(DraftLine()); message = "Purchase saved."; screen = "home"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (editingId == null) "Save purchase" else "Save changes") }
                        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
