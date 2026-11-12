package com.api.somoscardoso.player

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException

class PhoneNumbersTest {
  @Test
  fun `bare and formatted Brazilian national numbers default to 55`() {
    for (input in listOf("11999999999", "(11) 99999-9999", " 11 99999 9999 ")) assertEquals(
        "+5511999999999",
        PhoneNumbers.normalize(input),
    )
    assertEquals("+558132345678", PhoneNumbers.normalize("(81) 3234-5678"))
  }

  @Test
  fun `explicit international numbers keep their country code`() {
    assertEquals("+14155552671", PhoneNumbers.normalize("+1 (415) 555-2671"))
    assertEquals("+442079460958", PhoneNumbers.normalize("+44 20 7946 0958"))
  }

  @Test
  fun `bare existing Brazilian country code is not duplicated`() {
    assertEquals("+5511999999999", PhoneNumbers.normalize("55 11 99999-9999"))
    assertEquals("+558132345678", PhoneNumbers.normalize("558132345678"))
  }

  @Test
  fun `normalization is idempotent`() {
    val normalized = PhoneNumbers.normalize("(11) 99999-9999")
    assertEquals(normalized, PhoneNumbers.normalize(normalized))
  }

  @Test
  fun `invalid and ambiguous numbers are rejected`() {
    for (input in
        listOf(
            "",
            "11999",
            "foo11999999999",
            "++5511999999999",
            "+05511999999999",
            "+551999",
            "01199999999",
            "5510999999999",
            "1234567890123456",
            "+1234567890123456",
        )) {
      assertEquals(
          400,
          assertThrows(
                  ResponseStatusException::class.java,
                  { PhoneNumbers.normalize(input) },
                  input,
              )
              .statusCode
              .value(),
      )
    }
  }
}
