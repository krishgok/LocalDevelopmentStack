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
        Files.writeString(routerDir.resolve("hello_router.py"), helloRouterPy())
        Files.writeString(serviceLayerDir.resolve("__init__.py"), "")
        Files.writeString(serviceLayerDir.resolve("hello_service.py"), helloServicePy())

        println("  [OK] Python service       ->  $serviceDir")
    }

    private fun requirementsTxt() = """
        fastapi==0.110.0
        uvicorn==0.28.0
    """.trimIndent()

    private fun mainPy() = """
        from fastapi import FastAPI
        from router.hello_router import router

        app = FastAPI()
        app.include_router(router)
    """.trimIndent()

    private fun helloRouterPy() = """
        from fastapi import APIRouter
        from service.hello_service import HelloService

        router = APIRouter(prefix="/api")
        _hello_service = HelloService()

        @router.get("/hello")
        def hello():
            return {"message": _hello_service.get_greeting()}
    """.trimIndent()

    private fun helloServicePy() = """
        class HelloService:
            def get_greeting(self) -> str:
                return "Hello, World!"
    """.trimIndent()
}
