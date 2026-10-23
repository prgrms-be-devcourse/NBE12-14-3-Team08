package com.back.global.test

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ConnectionTestController {

    @GetMapping("/api/connection-test")
    fun connectionTest(): String = "backend connected"
}