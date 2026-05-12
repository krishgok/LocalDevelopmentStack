package com.example.controller

import com.example.service.HealthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(private val healthService: HealthService) {

    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to healthService.status())
}