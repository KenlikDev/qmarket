package com.kenlikdev.qmarket.identity.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_addresses")
class Address(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),
    @Column(length = 100)
    var label: String? = null,
    @Column(name = "recipient_name", nullable = false, length = 200)
    var recipientName: String = "",
    @Column(length = 30)
    var phone: String? = null,
    @Column(nullable = false, length = 100)
    var country: String = "RU",
    @Column(length = 100)
    var region: String? = null,
    @Column(nullable = false, length = 100)
    var city: String = "",
    @Column(name = "street_line1", nullable = false, length = 255)
    var streetLine1: String = "",
    @Column(name = "street_line2", length = 255)
    var streetLine2: String? = null,
    @Column(name = "postal_code", length = 20)
    var postalCode: String? = null,
    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    fun formatSingleLine(): String {
        val parts =
            listOfNotNull(
                recipientName.takeIf { it.isNotBlank() },
                streetLine1.takeIf { it.isNotBlank() },
                streetLine2?.takeIf { it.isNotBlank() },
                listOfNotNull(postalCode, city).joinToString(" ").takeIf { it.isNotBlank() },
                region?.takeIf { it.isNotBlank() },
                country.takeIf { it.isNotBlank() },
            )
        return parts.joinToString(", ")
    }
}
