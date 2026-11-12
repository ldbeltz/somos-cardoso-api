package com.api.somoscardoso.auth

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(ResponseStatusException::class)
  fun status(error: ResponseStatusException): ResponseEntity<Map<String, String>> =
      ResponseEntity.status(error.statusCode)
          .body(mapOf("message" to (error.reason ?: "Request failed")))

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun validation(error: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> =
      ResponseEntity.badRequest()
          .body(
              mapOf(
                  "message" to
                      (error.bindingResult.fieldErrors.firstOrNull()?.let {
                        "${it.field}: ${it.defaultMessage}"
                      } ?: "Invalid request")
              )
          )

  @ExceptionHandler(DataIntegrityViolationException::class)
  fun conflict(): ResponseEntity<Map<String, String>> =
      ResponseEntity.status(409)
          .body(mapOf("message" to "The request conflicts with existing data"))
}
