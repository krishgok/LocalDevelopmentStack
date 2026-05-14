package com.localdevstack.generator

class PhpDockerfileGenerator : DockerfileGenerator() {
    override fun dockerfile() = """
        FROM php:8.2-cli-alpine
        RUN apk add --no-cache --virtual .build-deps postgresql-dev \
            && apk add --no-cache libpq \
            && docker-php-ext-install pdo pdo_mysql pdo_pgsql \
            && apk del .build-deps \
            && curl -sS https://getcomposer.org/installer | php -- --install-dir=/usr/local/bin --filename=composer
        WORKDIR /app
        COPY composer.json composer.lock* ./
        RUN composer install --no-interaction --no-scripts --prefer-dist
        EXPOSE 8080
        CMD ["php", "-S", "0.0.0.0:8080", "-t", "public"]
    """.trimIndent()
}
