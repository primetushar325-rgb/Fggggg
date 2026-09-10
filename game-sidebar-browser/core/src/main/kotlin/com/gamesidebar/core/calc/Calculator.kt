package com.gamesidebar.core.calc

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * A small, dependency-free expression evaluator for the overlay calculator.
 *
 * Supports `+ - × ÷ %` and parentheses, unary minus, and postfix percent (`50%` -> 0.5,
 * `200 + 10%` -> 220 is NOT applied - percentages are plain postfix division by 100, which is what
 * a pocket calculator does and what users expect from a tool this size).
 *
 * Tokenizing plus shunting-yard means no reflection, no script engine, no eval of user strings.
 */
object Calculator {

    sealed interface Result {
        data class Value(val value: Double, val display: String) : Result
        data class Error(val reasonKey: String) : Result
        /** Expression ends on an operator ("12 +"): valid so far, no answer yet. */
        data object Incomplete : Result
    }

    private enum class TokenType { NUMBER, OPERATOR, LEFT_PAREN, RIGHT_PAREN, POSTFIX }

    private data class Token(val type: TokenType, val text: String, val value: Double = 0.0)

    private data class Operator(
        val symbol: String,
        val precedence: Int,
        val rightAssociative: Boolean = false,
        val unary: Boolean = false,
    )

    private val BINARY_OPS = mapOf(
        "+" to Operator("+", 1),
        "-" to Operator("-", 1),
        "*" to Operator("*", 2),
        "/" to Operator("/", 2),
    )
    private const val UNARY_PRECEDENCE = 3
    private const val POSTFIX_PRECEDENCE = 4

    /** Maps what the keypad shows to what the parser eats. */
    fun toEngineExpression(display: String): String = display
        .replace('×', '*')
        .replace('÷', '/')
        .replace('−', '-')
        .replace('(', '(')
        .replace(')', ')')
        .trim()

    /** Inverse of [toEngineExpression]; used to keep the on-screen expression pretty. */
    fun toDisplayExpression(engine: String): String = engine
        .replace('*', '×')
        .replace('/', '÷')

    fun evaluate(displayExpression: String): Result {
        val expression = toEngineExpression(displayExpression)
        if (expression.isBlank()) return Result.Incomplete

        val tokens = tokenize(expression) ?: return Result.Error("calc_error_syntax")
        if (tokens.isEmpty()) return Result.Incomplete
        // Trailing operator means the user is still typing.
        val last = tokens.last()
        if (last.type == TokenType.OPERATOR || (last.type == TokenType.LEFT_PAREN)) return Result.Incomplete

        val rpn = toRpn(tokens) ?: return Result.Error("calc_error_parentheses")
        return evalRpn(rpn)
    }

    private fun tokenize(expression: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < expression.length) {
            val c = expression[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    var dots = 0
                    while (i < expression.length && (expression[i].isDigit() || expression[i] == '.')) {
                        if (expression[i] == '.') dots++
                        i++
                    }
                    if (dots > 1) return null
                    val text = expression.substring(start, i)
                    val value = text.toDoubleOrNull() ?: return null
                    tokens += Token(TokenType.NUMBER, text, value)
                }

                c == '(' -> {
                    tokens += Token(TokenType.LEFT_PAREN, "(")
                    i++
                }

                c == ')' -> {
                    tokens += Token(TokenType.RIGHT_PAREN, ")")
                    i++
                }

                c == '%' -> {
                    tokens += Token(TokenType.POSTFIX, "%")
                    i++
                }

                c == '+' || c == '-' || c == '*' || c == '/' -> {
                    tokens += Token(TokenType.OPERATOR, c.toString())
                    i++
                }

                else -> return null
            }
        }
        return tokens
    }

    private fun toRpn(tokens: List<Token>): List<Token>? {
        val output = mutableListOf<Token>()
        val stack = ArrayDeque<Token>()

        for ((index, token) in tokens.withIndex()) {
            when (token.type) {
                TokenType.NUMBER -> output += token

                TokenType.POSTFIX -> output += token

                TokenType.LEFT_PAREN -> stack.addLast(token)

                TokenType.RIGHT_PAREN -> {
                    var found = false
                    while (stack.isNotEmpty()) {
                        val top = stack.removeLast()
                        if (top.type == TokenType.LEFT_PAREN) {
                            found = true
                            break
                        }
                        output += top
                    }
                    if (!found) return null
                }

                TokenType.OPERATOR -> {
                    val unary = isUnaryPosition(tokens, index)
                    if (unary && token.text == "-") {
                        stack.addLast(Token(TokenType.OPERATOR, "u-"))
                    } else if (unary && token.text == "+") {
                        // Unary plus is a no-op; drop it.
                    } else {
                        val op = BINARY_OPS[token.text] ?: return null
                        while (stack.isNotEmpty()) {
                            val top = stack.last()
                            if (top.type == TokenType.LEFT_PAREN) break
                            val topOp = operatorOf(top.text) ?: return null
                            val shouldPop = if (op.rightAssociative) {
                                topOp.precedence > op.precedence
                            } else {
                                topOp.precedence >= op.precedence
                            }
                            if (!shouldPop) break
                            output += stack.removeLast()
                        }
                        stack.addLast(token)
                    }
                }
            }
        }
        while (stack.isNotEmpty()) {
            val top = stack.removeLast()
            if (top.type == TokenType.LEFT_PAREN) return null
            output += top
        }
        return output
    }

    private fun isUnaryPosition(tokens: List<Token>, index: Int): Boolean {
        if (index == 0) return true
        val previous = tokens[index - 1]
        // After a postfix percent ("50% - 2") a minus is binary; at the start, after '(' or after
        // another operator it is a sign.
        return previous.type == TokenType.OPERATOR || previous.type == TokenType.LEFT_PAREN
    }

    private fun operatorOf(symbol: String): Operator? = when (symbol) {
        "u-" -> Operator("u-", UNARY_PRECEDENCE, rightAssociative = true, unary = true)
        else -> BINARY_OPS[symbol]
    }

    private fun evalRpn(rpn: List<Token>): Result {
        val stack = ArrayDeque<Double>()
        for (token in rpn) {
            when (token.type) {
                TokenType.NUMBER -> stack.addLast(token.value)
                TokenType.POSTFIX -> {
                    if (stack.isEmpty()) return Result.Error("calc_error_syntax")
                    stack.addLast(stack.removeLast() / 100.0)
                }

                TokenType.OPERATOR -> {
                    if (token.text == "u-") {
                        if (stack.isEmpty()) return Result.Error("calc_error_syntax")
                        stack.addLast(-stack.removeLast())
                    } else {
                        if (stack.size < 2) return Result.Error("calc_error_syntax")
                        val b = stack.removeLast()
                        val a = stack.removeLast()
                        val value = when (token.text) {
                            "+" -> a + b
                            "-" -> a - b
                            "*" -> a * b
                            "/" -> {
                                if (b == 0.0) return Result.Error("calc_error_div_zero")
                                a / b
                            }

                            else -> return Result.Error("calc_error_syntax")
                        }
                        stack.addLast(value)
                    }
                }

                else -> return Result.Error("calc_error_syntax")
            }
        }
        if (stack.size != 1) return Result.Error("calc_error_syntax")
        val value = stack.last()
        if (value.isNaN() || value.isInfinite()) return Result.Error("calc_error_overflow")
        return Result.Value(value, format(value))
    }

    /** Human-friendly rendering: no trailing zeros, scientific only when truly huge or tiny. */
    fun format(value: Double): String {
        if (value == 0.0) return "0"
        val magnitude = abs(value)
        if (magnitude >= 1e12 || magnitude < 1e-9) {
            return String.format(java.util.Locale.US, "%.6e", value)
        }
        val rounded = round(value * 1e10) / 1e10
        val text = String.format(java.util.Locale.US, "%.10f", rounded)
            .trimEnd('0')
            .trimEnd('.')
        return if (text == "-0") "0" else text
    }

    /** `2 ^ 10`-style helper kept out of the keypad; exposed for tests and future expansion. */
    fun pow(base: Double, exponent: Double): Double = base.pow(exponent)
}
