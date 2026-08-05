package com.kenlikdev.qmarket.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DtoSerializationTest {
    private val json = QMarketJson.format

    @Test
    fun loginRequestRoundTrip() {
        val dto = LoginRequestDto(email = "a@b.c", password = "secret")
        val encoded = json.encodeToString(LoginRequestDto.serializer(), dto)
        val decoded = json.decodeFromString(LoginRequestDto.serializer(), encoded)
        assertEquals(dto, decoded)
    }

    @Test
    fun authResponseParsesServerLikePayload() {
        val payload =
            """
            {
              "accessToken": "aaa",
              "refreshToken": "bbb",
              "tokenType": "Bearer",
              "expiresIn": 3600,
              "user": {
                "id": "11111111-1111-1111-1111-111111111111",
                "email": "admin@qmarket.local",
                "firstName": "Admin",
                "lastName": null,
                "roles": ["ROLE_ADMIN"]
              }
            }
            """.trimIndent()
        val dto = json.decodeFromString(AuthResponseDto.serializer(), payload)
        assertEquals("aaa", dto.accessToken)
        assertEquals("admin@qmarket.local", dto.user.email)
        assertTrue(dto.user.roles.contains("ROLE_ADMIN"))
    }

    @Test
    fun productPageParses() {
        val payload =
            """
            {
              "content": [
                {
                  "id": "p1",
                  "name": "Phone",
                  "slug": "phone",
                  "price": "499.00",
                  "stockQuantity": 5,
                  "active": true,
                  "featured": false
                }
              ],
              "page": 0,
              "size": 20,
              "totalElements": 1,
              "totalPages": 1
            }
            """.trimIndent()
        val page = json.decodeFromString(PageDto.serializer(ProductDto.serializer()), payload)
        assertEquals(1, page.content.size)
        assertEquals("499.00", page.content[0].price)
    }

    @Test
    fun springDataPageParsesWithoutPageField() {
        val payload =
            """
            {
              "content": [],
              "totalElements": 0,
              "totalPages": 0,
              "size": 20,
              "number": 0
            }
            """.trimIndent()
        val page = json.decodeFromString(PageDto.serializer(ProductDto.serializer()), payload)
        assertEquals(0, page.pageIndex())
        assertEquals(0, page.totalElements)
    }

    @Test
    fun orderStatusEnum() {
        val payload =
            """
            {
              "id": "o1",
              "userId": "u1",
              "status": "PENDING",
              "totalAmount": "10.00",
              "items": []
            }
            """.trimIndent()
        val order = json.decodeFromString(OrderDto.serializer(), payload)
        assertEquals(OrderStatusDto.PENDING, order.status)
    }
}
