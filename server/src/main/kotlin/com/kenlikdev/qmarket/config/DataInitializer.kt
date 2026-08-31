package com.kenlikdev.qmarket.config

import com.kenlikdev.qmarket.catalog.domain.Category
import com.kenlikdev.qmarket.catalog.domain.Product
import com.kenlikdev.qmarket.catalog.repository.CategoryRepository
import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.identity.domain.Role
import com.kenlikdev.qmarket.identity.domain.User
import com.kenlikdev.qmarket.identity.repository.RoleRepository
import com.kenlikdev.qmarket.identity.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
@ConditionalOnProperty(name = ["qmarket.seed.enabled"], havingValue = "true", matchIfMissing = false)
class DataInitializer(
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        seedRoles()
        seedAdmin()
        seedCatalog()
    }

    private fun seedRoles() {
        val roles =
            listOf(
                "ROLE_USER" to "Regular customer",
                "ROLE_ADMIN" to "Administrator with full access",
                "ROLE_MANAGER" to "Store manager",
            )
        roles.forEach { (name, description) ->
            if (roleRepository.findByName(name) == null) {
                roleRepository.save(Role(name = name, description = description))
                log.info("Seeded role: {}", name)
            }
        }
    }

    private fun seedAdmin() {
        val adminEmail = "admin@qmarket.local"
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin user already exists: {}", adminEmail)
            return
        }

        val adminRole =
            roleRepository
                .findByName("ROLE_ADMIN")
                ?: throw IllegalStateException("ROLE_ADMIN not found")
        val userRole =
            roleRepository
                .findByName("ROLE_USER")
                ?: throw IllegalStateException("ROLE_USER not found")

        val admin =
            User(
                email = adminEmail,
                passwordHash = passwordEncoder.encode("admin123") ?: error("encode failed"),
                firstName = "Admin",
                lastName = "QMarket",
                enabled = true,
                emailVerified = true,
                roles = mutableSetOf(adminRole, userRole),
            )
        userRepository.save(admin)
        log.info("Seeded admin user: {} (password from seed config, not logged)", adminEmail)
    }

    private fun seedCatalog() {
        if (categoryRepository.count() > 0) {
            log.info("Catalog already seeded, skipping")
            return
        }

        val electronics =
            categoryRepository.save(
                Category(
                    name = "Electronics",
                    slug = "electronics",
                    description = "Gadgets and devices",
                    sortOrder = 1,
                ),
            )
        val clothing =
            categoryRepository.save(
                Category(
                    name = "Clothing",
                    slug = "clothing",
                    description = "Fashion and apparel",
                    sortOrder = 2,
                ),
            )
        val home =
            categoryRepository.save(
                Category(
                    name = "Home & Kitchen",
                    slug = "home-kitchen",
                    description = "Everything for your home",
                    sortOrder = 3,
                ),
            )
        log.info("Seeded {} categories", 3)

        val products =
            listOf(
                Product(
                    name = "Wireless Headphones Pro",
                    slug = "wireless-headphones-pro",
                    shortDescription = "Noise-cancelling over-ear headphones",
                    description = "Premium wireless headphones with active noise cancellation, 30h battery and comfortable fit.",
                    sku = "ELEC-HP-001",
                    price = BigDecimal("149.99"),
                    compareAtPrice = BigDecimal("199.99"),
                    stockQuantity = 45,
                    featured = true,
                    category = electronics,
                ),
                Product(
                    name = "USB-C Fast Charger 65W",
                    slug = "usb-c-fast-charger-65w",
                    shortDescription = "GaN charger for laptop and phone",
                    description = "Compact 65W GaN charger with dual USB-C ports. Compatible with most laptops and phones.",
                    sku = "ELEC-CH-002",
                    price = BigDecimal("39.99"),
                    stockQuantity = 120,
                    featured = true,
                    category = electronics,
                ),
                Product(
                    name = "Smart Watch X3",
                    slug = "smart-watch-x3",
                    shortDescription = "Fitness and health tracking smartwatch",
                    description = "Track heart rate, sleep, SpO2 and workouts. 7-day battery life, AMOLED display.",
                    sku = "ELEC-SW-003",
                    price = BigDecimal("199.00"),
                    compareAtPrice = BigDecimal("249.00"),
                    stockQuantity = 30,
                    featured = true,
                    category = electronics,
                ),
                Product(
                    name = "Classic Cotton T-Shirt",
                    slug = "classic-cotton-tshirt",
                    shortDescription = "100% organic cotton, unisex",
                    description = "Soft organic cotton t-shirt. Available in multiple colors. Regular fit.",
                    sku = "CLTH-TS-001",
                    price = BigDecimal("24.99"),
                    stockQuantity = 200,
                    category = clothing,
                ),
                Product(
                    name = "Denim Jacket",
                    slug = "denim-jacket",
                    shortDescription = "Vintage-style denim jacket",
                    description = "Classic denim jacket with a modern cut. Durable and stylish for everyday wear.",
                    sku = "CLTH-DJ-002",
                    price = BigDecimal("79.99"),
                    compareAtPrice = BigDecimal("99.99"),
                    stockQuantity = 55,
                    featured = true,
                    category = clothing,
                ),
                Product(
                    name = "Ceramic Coffee Mug Set",
                    slug = "ceramic-coffee-mug-set",
                    shortDescription = "Set of 4 minimalist mugs",
                    description = "Elegant ceramic mugs, 350ml each. Microwave and dishwasher safe.",
                    sku = "HOME-MG-001",
                    price = BigDecimal("34.99"),
                    stockQuantity = 80,
                    category = home,
                ),
                Product(
                    name = "Non-stick Frying Pan 28cm",
                    slug = "nonstick-frying-pan-28",
                    shortDescription = "Professional non-stick pan",
                    description = "PFOA-free non-stick coating, induction compatible, oven-safe up to 230°C.",
                    sku = "HOME-FP-002",
                    price = BigDecimal("49.99"),
                    stockQuantity = 40,
                    featured = true,
                    category = home,
                ),
                Product(
                    name = "LED Desk Lamp",
                    slug = "led-desk-lamp",
                    shortDescription = "Adjustable brightness and color temperature",
                    description = "Modern LED desk lamp with touch controls, USB charging port and flexible arm.",
                    sku = "HOME-LM-003",
                    price = BigDecimal("29.99"),
                    stockQuantity = 65,
                    category = home,
                ),
            )

        productRepository.saveAll(products)
        log.info("Seeded {} products", products.size)
    }
}
