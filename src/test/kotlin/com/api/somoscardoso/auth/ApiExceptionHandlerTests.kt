package com.api.somoscardoso.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException

class ApiExceptionHandlerTests {
  private val handler = ApiExceptionHandler()

  @Test
  fun `should describe the first validation field error`() {
    val binding = BeanPropertyBindingResult("invalid", "request")
    binding.addError(FieldError("request", "email", "must be valid"))
    val method = ValidationEndpoint::class.java.getDeclaredMethod("accept", String::class.java)
    val error = MethodArgumentNotValidException(MethodParameter(method, 0), binding)

    val response = handler.validation(error)

    assertEquals(400, response.statusCode.value())
    assertEquals("email: must be valid", response.body!!["message"])
  }

  @Test
  fun `should return a fallback validation message when no field errors exist`() {
    val binding = BeanPropertyBindingResult("invalid", "request")
    binding.reject("invalid")
    val method = ValidationEndpoint::class.java.getDeclaredMethod("accept", String::class.java)
    val error = MethodArgumentNotValidException(MethodParameter(method, 0), binding)

    assertEquals("Invalid request", handler.validation(error).body!!["message"])
  }

  @Test
  fun `should map data conflicts to a stable response`() {
    val response = handler.conflict()

    assertEquals(409, response.statusCode.value())
    assertEquals("The request conflicts with existing data", response.body!!["message"])
  }

  private class ValidationEndpoint {
    fun accept(value: String) = value
  }
}
