package com.api.somoscardoso.player

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** Store international E.164; bare national numbers use Brazil's +55 dial code. */
object PhoneNumbers {
  const val INVALID = "Enter a valid phone number, including area code"

  fun normalize(input: String): String {
    val text = input.trim()
    if (text.isBlank() || !text.matches(Regex("^[+0-9() .-]+$"))) invalid()
    val compact = text.replace(Regex("[() .-]"), "")
    val normalized =
        when {
          compact.startsWith("+") -> compact
          compact.length in 10..11 -> "+55$compact"
          compact.startsWith("55") && compact.length in 12..13 -> "+$compact"
          else -> invalid()
        }
    if (!normalized.matches(Regex("^\\+[1-9][0-9]{7,14}$"))) invalid()
    if (normalized.startsWith("+55")) {
      val national = normalized.substring(3)
      if (national.length !in 10..11 || national[0] == '0' || national[1] == '0') invalid()
    }
    return normalized
  }

  private fun invalid(): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID)
}
