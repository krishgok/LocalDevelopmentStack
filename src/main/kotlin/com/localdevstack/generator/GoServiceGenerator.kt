package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class GoServiceGenerator : ServiceGenerator {

    override val runCommand = "go run ./..."

    override fun generate(outputDir: Path, projectName: String) {
        val serviceDir = outputDir.resolve("service")
        val handlerDir = serviceDir.resolve("handler")
        val serviceLayerDir = serviceDir.resolve("service")

        listOf(serviceDir, handlerDir, serviceLayerDir).forEach {
            Files.createDirectories(it)
        }

        Files.writeString(serviceDir.resolve("go.mod"), goMod(projectName))
        Files.writeString(serviceDir.resolve("main.go"), mainGo(projectName))
        Files.writeString(handlerDir.resolve("health_handler.go"), healthHandlerGo())
        Files.writeString(serviceLayerDir.resolve("health_service.go"), healthServiceGo())

        println("  [OK] Go service           ->  $serviceDir")
    }

    private fun goMod(projectName: String) = """
        module $projectName

        go 1.22
    """.trimIndent()

    private fun mainGo(projectName: String) = """
        package main

        import (
        	"log"
        	"net/http"

        	"$projectName/handler"
        )

        func main() {
        	mux := http.NewServeMux()

        	healthHandler := handler.NewHealthHandler()
        	mux.HandleFunc("/health", healthHandler.Health)

        	log.Println("Server starting on :8080")
        	log.Fatal(http.ListenAndServe(":8080", mux))
        }
    """.trimIndent()

    private fun healthHandlerGo() = """
        package handler

        import (
        	"encoding/json"
        	"net/http"
        )

        type HealthHandler struct{}

        func NewHealthHandler() *HealthHandler {
        	return &HealthHandler{}
        }

        func (h *HealthHandler) Health(w http.ResponseWriter, r *http.Request) {
        	w.Header().Set("Content-Type", "application/json")
        	if err := json.NewEncoder(w).Encode(map[string]string{"status": "ok"}); err != nil {
        		http.Error(w, "encoding response", http.StatusInternalServerError)
        	}
        }
    """.trimIndent()

    private fun healthServiceGo() = """
        package service

        type HealthService struct{}

        func NewHealthService() *HealthService {
        	return &HealthService{}
        }

        func (s *HealthService) Status() string {
        	return "ok"
        }
    """.trimIndent()
}
