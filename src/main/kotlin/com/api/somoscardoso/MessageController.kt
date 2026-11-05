package com.api.somoscardoso

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MessageController {
  @GetMapping("/") fun index() = "Hello World!"
}
