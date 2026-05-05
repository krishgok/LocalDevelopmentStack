package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class PythonServiceGenerator : ServiceGenerator {

    override val runCommand = "pip install -r requirements.txt && uvicorn main:app --reload"

    override fun generate(outputDir: Path, projectName: String) {
        val serviceDir = outputDir.resolve("service")
        val routerDir = serviceDir.resolve("router")
        val serviceLayerDir = serviceDir.resolve("service")

        listOf(serviceDir, routerDir, serviceLayerDir).forEach {
            Files.createDirectories(it)
        }

        Files.writeString(serviceDir.resolve("requirements.txt"), requirementsTxt())
        Files.writeString(serviceDir.resolve("main.py"), mainPy())
        Files.writeString(routerDir.resolve("__init__.py"), "")
        Files.writeString(routerDir.resolve("health_router.py"), healthRouterPy())
        Files.writeString(serviceLayerDir.resolve("__init__.py"), "")
        Files.writeString(serviceLayerDir.resolve("health_service.py"), healthServicePy())

        println("  [OK] Python service       ->  $serviceDir")
    }

    private fun requirementsTxt() = """
        fastapi==0.110.0
        uvicorn==0.28.0
    """.trimIndent()

    private fun mainPy() = """
        from fastapi import FastAPI
        from router.health_router import router

        app = FastAPI()
        app.include_router(router)
    """.trimIndent()

    private fun healthRouterPy() = """
        from fastapi import APIRouter
        from service.health_service import HealthService

        router = APIRouter()
        _health_service = HealthService()

        @router.get("/health")
        def health():
            return {"status": _health_service.status()}
    """.trimIndent()

    private fun healthServicePy() = """
        class HealthService:
            def status(self) -> str:
                return "ok"
    """.trimIndent()
}
