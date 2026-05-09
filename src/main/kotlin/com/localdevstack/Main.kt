package com.localdevstack

import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    Logging.init()
    exitProcess(CommandLine(LocalDevStackCli()).execute(*args))
}
