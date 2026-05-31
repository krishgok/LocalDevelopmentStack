package com.example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class Application

@RestController
class HealthController {
    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "ok")
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
