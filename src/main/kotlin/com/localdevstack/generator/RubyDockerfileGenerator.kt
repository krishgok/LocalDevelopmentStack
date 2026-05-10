package com.localdevstack.generator

class RubyDockerfileGenerator : DockerfileGenerator() {
    override fun dockerfile() = """
        FROM ruby:3.2-slim
        RUN apt-get update && apt-get install -y --no-install-recommends build-essential libpq-dev && rm -rf /var/lib/apt/lists/*
        WORKDIR /app
        COPY Gemfile* ./
        RUN bundle install
        EXPOSE 8080
        CMD ["bundle", "exec", "rails", "server", "-b", "0.0.0.0", "-p", "8080"]
    """.trimIndent()
}
