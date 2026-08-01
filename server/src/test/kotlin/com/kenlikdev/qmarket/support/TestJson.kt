package com.kenlikdev.qmarket.support

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * Shared JSON helpers for integration tests (Jackson 3 / Spring Boot 4).
 * Uses core ObjectMapper only — no kotlin-module extensions required on test CP.
 */
object TestJson {
    private val mapper = ObjectMapper()

    fun parse(json: String): JsonNode = mapper.readTree(json)

    fun accessToken(json: String): String {
        val token = parse(json).path("accessToken").asString(null)
        return token ?: error("No accessToken in response: $json")
    }

    fun id(json: String): String {
        val value = parse(json).path("id").asString(null)
        return value ?: error("No id in response: $json")
    }

    fun firstContentId(json: String): String {
        val node = parse(json)
        val content = node.path("content")
        if (content.isArray && content.size() > 0) {
            val value = content[0].path("id").asString(null)
            return value ?: error("No id in content[0]: $json")
        }
        if (node.isArray && node.size() > 0) {
            val value = node[0].path("id").asString(null)
            return value ?: error("No id in [0]: $json")
        }
        error("No content id in: $json")
    }
}
