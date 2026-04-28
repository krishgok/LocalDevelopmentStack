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
        Files.writeString(handlerDir.resolve("hello_handler.go"), helloHandlerGo(projectName))
        Files.writeString(serviceLayerDir.resolve("hello_service.go"), helloServiceGo())

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

        	helloHandler := handler.NewHelloHandler()
        	mux.HandleFunc("/api/hello", helloHandler.Hello)

        	log.Println("Server starting on :8080")
        	log.Fatal(http.ListenAndServe(":8080", mux))
        }
    """.trimIndent()

    private fun helloHandlerGo(projectName: String) = """
        package handler

        import (
        	"encoding/json"
        	"net/http"

        	"$projectName/service"
        )

        type HelloHandler struct {
        	helloService *service.HelloService
        }

        func NewHelloHandler() *HelloHandler {
        	return &HelloHandler{helloService: service.NewHelloService()}
        }

        func (h *HelloHandler) Hello(w http.ResponseWriter, r *http.Request) {
        	w.Header().Set("Content-Type", "application/json")
        	json.NewEncoder(w).Encode(map[string]string{"message": h.helloService.GetGreeting()})
        }
    """.trimIndent()

    private fun helloServiceGo() = """
        package service

        type HelloService struct{}

        func NewHelloService() *HelloService {
        	return &HelloService{}
        }

        func (s *HelloService) GetGreeting() string {
        	return "Hello, World!"
        }
    """.trimIndent()
}
