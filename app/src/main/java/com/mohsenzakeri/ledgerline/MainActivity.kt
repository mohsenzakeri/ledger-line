package com.mohsenzakeri.ledgerline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ReceiptLong
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
                FloatingActionButton(onClick = { screen = "add" }) {
                    Icon(Icons.Default.Add, contentDescription = "Add purchase")
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = screen == "home", onClick = { screen = "home" }, icon = { Icon(Icons.Default.ReceiptLong, null) }, label = { Text("Overview") })
                    NavigationBarItem(selected = screen == "add", onClick = { screen = "add" }, icon = { Icon(Icons.Default.Add, null) }, label = { Text("Add") })
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
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Text("New purchase", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                    item {
                        OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = retailer, onValueChange = { retailer = it }, label = { Text("Retailer") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
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
                                        val id = dao.insertPurchase(PurchaseEntity(date = date, retailer = retailer, receiptTotal = receiptTotal.toDoubleOrNull() ?: lineTotal))
                                        dao.insertEntries(lines.map { ExpenseEntryEntity(purchaseId = id, amount = it.amount.toDouble(), category = it.category.trim(), secondaryCategory = it.secondary.trim(), necessity = it.necessity, details = it.details, moreInformation = it.notes) })
                                        date = LocalDate.now().toString(); retailer = ""; receiptTotal = ""; lines = listOf(DraftLine()); message = "Purchase saved."; screen = "home"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save purchase") }
                        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
