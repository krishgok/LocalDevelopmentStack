package main

import (
	"log"
	"net/http"

	"my-test-api-service/handler"
)

func main() {
	mux := http.NewServeMux()

	healthHandler := handler.NewHealthHandler()
	mux.HandleFunc("/health", healthHandler.Health)

	log.Println("Server starting on :8080")
	log.Fatal(http.ListenAndServe(":8080", mux))
}