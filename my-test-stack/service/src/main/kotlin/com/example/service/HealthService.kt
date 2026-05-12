package com.example.service

import org.springframework.stereotype.Service

@Service
class HealthService {
    fun status(): String = "ok"
}