package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class RubyServiceGenerator : ServiceGenerator {

    override val runCommand = "bundle install && ruby app.rb"

    override fun generate(outputDir: Path, projectName: String) {
        val serviceDir = outputDir.resolve("service")
        val servicesDir = serviceDir.resolve("app/services")

        listOf(serviceDir, servicesDir).forEach {
            Files.createDirectories(it)
        }

        Files.writeString(serviceDir.resolve("Gemfile"), gemfile())
        Files.writeString(serviceDir.resolve("app.rb"), appRb())
        Files.writeString(servicesDir.resolve("health_service.rb"), healthServiceRb())

        println("  [OK] Ruby (Sinatra) service ->  $serviceDir")
    }

    private fun gemfile() = """
        source 'https://rubygems.org'

        gem 'sinatra', '~> 4.0'
        gem 'puma', '~> 6.0'
        gem 'rackup', '~> 2.1'
    """.trimIndent()

    private fun appRb() = """
        require 'sinatra'
        require 'json'
        require_relative 'app/services/health_service'

        set :bind, '0.0.0.0'
        set :port, 8080

        get '/health' do
          content_type :json
          { status: HealthService.new.status }.to_json
        end
    """.trimIndent()

    private fun healthServiceRb() = """
        class HealthService
          def status
            'ok'
          end
        end
    """.trimIndent()
}
