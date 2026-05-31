package com.example;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Application {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
