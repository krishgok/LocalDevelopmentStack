package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class NodeServiceGenerator : ServiceGenerator {

    override val runCommand = "npm install && node index.js"

    override fun generate(outputDir: Path, projectName: String) {
        val serviceDir = outputDir.resolve("service")
        val routesDir = serviceDir.resolve("routes")
        val servicesDir = serviceDir.resolve("services")

        listOf(serviceDir, routesDir, servicesDir).forEach {
            Files.createDirectories(it)
        }

        Files.writeString(serviceDir.resolve("package.json"), packageJson(projectName))
        Files.writeString(serviceDir.resolve("index.js"), indexJs())
        Files.writeString(routesDir.resolve("hello.js"), helloRouteJs())
        Files.writeString(servicesDir.resolve("helloService.js"), helloServiceJs())

        println("  [OK] Node.js service      ->  $serviceDir")
    }

    private fun packageJson(projectName: String) = """
        {
          "name": "$projectName",
          "version": "1.0.0",
          "main": "index.js",
          "scripts": {
            "start": "node index.js"
          },
          "dependencies": {
            "express": "^4.18.2"
          }
        }
    """.trimIndent()

    private fun indexJs() = """
        const express = require('express');
        const helloRoute = require('./routes/hello');

        const app = express();
        app.use(express.json());
        app.use('/api', helloRoute);

        app.listen(8080, () => console.log('Server running on :8080'));
    """.trimIndent()

    private fun helloRouteJs() = """
        const express = require('express');
        const router = express.Router();
        const helloService = require('../services/helloService');

        router.get('/hello', (req, res) => {
            res.json({ message: helloService.getGreeting() });
        });

        module.exports = router;
    """.trimIndent()

    private fun helloServiceJs() = """
        function getGreeting() {
            return 'Hello, World!';
        }

        module.exports = { getGreeting };
    """.trimIndent()
}
