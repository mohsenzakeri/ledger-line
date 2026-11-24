package com.mohsenzakeri.ledgerline.data

import java.io.Reader

object LedgerCsv {
    val headers = listOf("Date", "Amount", "Payment Method", "Category", "Secondary-Category", "Necessity", "Retailer", "Details", "More Information")

    fun encode(rows: List<List<String>>): String = buildString {
        appendLine(headers.joinToString(","))
        rows.forEach { row ->
            appendLine(row.joinToString(",") { cell ->
                if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
                    "\"" + cell.replace("\"", "\"\"") + "\""
                else cell
            })
        }
    }

    fun parse(reader: Reader): List<List<String>> {
        val input = reader.readText().removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                ch == '"' && quoted && i + 1 < input.length && input[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { row.add(cell.toString()); cell.clear() }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    if (ch == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.clear()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (quoted) error("Unclosed quoted CSV field")
        row.add(cell.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }

    fun readRows(reader: Reader): List<Map<String, String>> {
        val rows = parse(reader)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim() }
        require(headers.filter { it != "Payment Method" }.all { it in header }) { "CSV must contain the nine LedgerLine spreadsheet columns" }
        return rows.drop(1).map { row ->
            (headers + "Person").associateWith { key -> row.getOrElse(header.indexOf(key)) { "" } }
        }
    }
}
