package com.mohsenzakeri.ledgerline.data

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Simple arithmetic for money fields. No scripting or executable code. */
object AmountMath {
    private val context = MathContext(16, RoundingMode.HALF_UP)

    fun evaluate(input: String): BigDecimal? = try {
        if (input.isBlank()) null else Parser(input).parse().setScale(2, RoundingMode.HALF_UP)
    } catch (_: Exception) { null }

    fun display(amount: BigDecimal): String = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private class Parser(private val input: String) {
        private var pos = 0
        fun parse(): BigDecimal {
            val result = expression()
            skip()
            require(pos == input.length) { "Unexpected input" }
            return result
        }
        private fun skip() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
        private fun eat(c: Char): Boolean {
            skip()
            if (pos < input.length && input[pos] == c) { pos++; return true }
            return false
        }
        private fun expression(): BigDecimal {
            var result = term()
            while (true) {
                result = when {
                    eat('+') -> result.add(term(), context)
                    eat('-') -> result.subtract(term(), context)
                    else -> return result
                }
            }
        }
        private fun term(): BigDecimal {
            var result = factor()
            while (true) {
                result = when {
                    eat('*') -> result.multiply(factor(), context)
                    eat('/') -> result.divide(factor().also { require(it.signum() != 0) }, context)
                    else -> return result
                }
            }
        }
        private fun factor(): BigDecimal {
            if (eat('+')) return factor()
            if (eat('-')) return factor().negate()
            if (eat('(')) {
                val result = expression()
                require(eat(')')) { "Missing closing parenthesis" }
                return result
            }
            skip()
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
            require(pos > start) { "Expected number" }
            return BigDecimal(input.substring(start, pos))
        }
    }
}
