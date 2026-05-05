package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class RubyServiceGenerator : ServiceGenerator {

    override val runCommand = "bundle install && rails server -p 8080 -b 0.0.0.0"

    override fun generate(outputDir: Path, projectName: String) {
        val serviceDir = outputDir.resolve("service")
        val controllersDir = serviceDir.resolve("app/controllers")
        val servicesDir = serviceDir.resolve("app/services")
        val configDir = serviceDir.resolve("config")

        listOf(serviceDir, controllersDir, servicesDir, configDir).forEach {
            Files.createDirectories(it)
        }

        Files.writeString(serviceDir.resolve("Gemfile"), gemfile())
        Files.writeString(controllersDir.resolve("health_controller.rb"), healthControllerRb())
        Files.writeString(servicesDir.resolve("health_service.rb"), healthServiceRb())
        Files.writeString(configDir.resolve("routes.rb"), routesRb())
        Files.writeString(configDir.resolve("database.yml"), databaseYml())

        println("  [OK] Ruby (Rails) service ->  $serviceDir")
    }

    private fun gemfile() = """
        source 'https://rubygems.org'

        ruby '3.2.0'

        gem 'rails', '~> 7.1'
        gem 'pg', '~> 1.1'
        gem 'puma', '~> 6.0'
    """.trimIndent()

    private fun healthControllerRb() = """
        class HealthController < ApplicationController
          def index
            render json: { status: HealthService.new.status }
          end
        end
    """.trimIndent()

    private fun healthServiceRb() = """
        class HealthService
          def status
            'ok'
          end
        end
    """.trimIndent()

    private fun routesRb() = """
        Rails.application.routes.draw do
          get '/health', to: 'health#index'
        end
    """.trimIndent()

    private fun databaseYml() = """
        default: &default
          adapter: postgresql
          encoding: unicode
          host: db
          port: 5432
          username: postgres
          password: postgres_dev_only

        development:
          <<: *default
          database: app_db
    """.trimIndent()
}
