package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.support.TestJson
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Admin users API against Testcontainers PostgreSQL (jdbc:tc in application-test.yml).
 * Requires Docker for the embedded Postgres container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUsersIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        adminToken =
            TestJson.accessToken(
                mockMvc
                    .perform(
                        post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"email":"admin@qmarket.local","password":"admin123"}"""),
                    ).andExpect(status().isOk)
                    .andReturn()
                    .response
                    .contentAsString,
            )

        // Idempotent: first run 201, later runs may 409
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "shopper-admin-users-it@test.local",
                      "password": "password123",
                      "firstName": "Shop",
                      "lastName": "Per"
                    }
                    """.trimIndent(),
                ),
        )

        userToken =
            TestJson.accessToken(
                mockMvc
                    .perform(
                        post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """{"email":"shopper-admin-users-it@test.local","password":"password123"}""",
                            ),
                    ).andExpect(status().isOk)
                    .andReturn()
                    .response
                    .contentAsString,
            )
    }

    @Test
    fun `list users rejects anonymous`() {
        // Class-level @PreAuthorize → AccessDenied for anonymous → 403 (not 401 entry-point)
        mockMvc
            .perform(get("/api/v1/admin/users"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `list users forbidden for ROLE_USER`() {
        mockMvc
            .perform(
                get("/api/v1/admin/users")
                    .header("Authorization", "Bearer $userToken"),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `admin can list users including seeded admin`() {
        mockMvc
            .perform(
                get("/api/v1/admin/users")
                    .header("Authorization", "Bearer $adminToken")
                    .param("size", "50"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.content[?(@.email == 'admin@qmarket.local')]").exists())
            .andExpect(jsonPath("$.content[?(@.email == 'shopper-admin-users-it@test.local')]").exists())
    }

    @Test
    fun `admin can search users by email fragment`() {
        mockMvc
            .perform(
                get("/api/v1/admin/users")
                    .header("Authorization", "Bearer $adminToken")
                    .param("q", "shopper-admin-users-it"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.content[0].email").value("shopper-admin-users-it@test.local"))
            .andExpect(jsonPath("$.content[0].firstName").value("Shop"))
    }

    @Test
    fun `admin can get user by id`() {
        val listJson =
            mockMvc
                .perform(
                    get("/api/v1/admin/users")
                        .header("Authorization", "Bearer $adminToken")
                        .param("q", "shopper-admin-users-it"),
                ).andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString

        val userId = TestJson.firstContentId(listJson)

        mockMvc
            .perform(
                get("/api/v1/admin/users/$userId")
                    .header("Authorization", "Bearer $adminToken"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.email").value("shopper-admin-users-it@test.local"))
            .andExpect(jsonPath("$.roles").isArray)
    }

    @Test
    fun `get unknown user returns 404`() {
        mockMvc
            .perform(
                get("/api/v1/admin/users/00000000-0000-0000-0000-000000000099")
                    .header("Authorization", "Bearer $adminToken"),
            ).andExpect(status().isNotFound)
    }
}
