package com.kenlikdev.qmarket.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kenlikdev.qmarket.api.ProductDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AdminScreenTest {
    private fun sampleProduct() =
        ProductDto(
            id = "p1",
            name = "Seed Widget",
            slug = "seed-widget",
            price = "5.00",
            stockQuantity = 2,
            featured = false,
        )

    private fun baseScreen(
        editingProductId: String? = null,
        productName: String = "Widget",
        productSlug: String = "widget",
        productPrice: String = "12.50",
        productStock: String = "3",
        productFeatured: Boolean = false,
        products: List<ProductDto> = emptyList(),
        onCreate: () -> Unit = {},
        onUpdate: () -> Unit = {},
        onDelete: (ProductDto) -> Unit = {},
        onEdit: (ProductDto) -> Unit = {},
    ) {
        // helper not used as composable
    }

    @Test
    fun createDisabledWhenPriceInvalid() =
        runComposeUiTest {
            setContent {
                AdminScreen(
                    categories = emptyList(),
                    categoryName = "",
                    categorySlug = "",
                    products = emptyList(),
                    editingProductId = null,
                    productName = "Widget",
                    productSlug = "widget",
                    productPrice = "abc",
                    productStock = "10",
                    productFeatured = false,
                    loggedIn = true,
                    userLabel = "admin@qmarket.local",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onCategoryNameChange = {},
                    onCategorySlugChange = {},
                    onCreateCategory = {},
                    onDeleteCategory = {},
                    onNameChange = {},
                    onSlugChange = {},
                    onPriceChange = {},
                    onStockChange = {},
                    onToggleFeatured = {},
                    onCreate = {},
                    onUpdate = {},
                    onDelete = {},
                    onEdit = {},
                    onClearEdit = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("adminCreateProduct").assertIsNotEnabled()
        }

    @Test
    fun createEnabledWhenFormValid() =
        runComposeUiTest {
            setContent {
                AdminScreen(
                    categories = emptyList(),
                    categoryName = "",
                    categorySlug = "",
                    products = emptyList(),
                    editingProductId = null,
                    productName = "Widget",
                    productSlug = "widget",
                    productPrice = "12.50",
                    productStock = "3",
                    productFeatured = true,
                    loggedIn = true,
                    userLabel = "admin@qmarket.local",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onCategoryNameChange = {},
                    onCategorySlugChange = {},
                    onCreateCategory = {},
                    onDeleteCategory = {},
                    onNameChange = {},
                    onSlugChange = {},
                    onPriceChange = {},
                    onStockChange = {},
                    onToggleFeatured = {},
                    onCreate = {},
                    onUpdate = {},
                    onDelete = {},
                    onEdit = {},
                    onClearEdit = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("adminCreateProduct").assertIsEnabled()
        }

    @Test
    fun editModeShowsSaveAndCancel() =
        runComposeUiTest {
            setContent {
                AdminScreen(
                    categories = emptyList(),
                    categoryName = "",
                    categorySlug = "",
                    products = listOf(sampleProduct()),
                    editingProductId = "p1",
                    productName = "Seed Widget",
                    productSlug = "seed-widget",
                    productPrice = "5.00",
                    productStock = "2",
                    productFeatured = false,
                    loggedIn = true,
                    userLabel = "admin@qmarket.local",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onCategoryNameChange = {},
                    onCategorySlugChange = {},
                    onCreateCategory = {},
                    onDeleteCategory = {},
                    onNameChange = {},
                    onSlugChange = {},
                    onPriceChange = {},
                    onStockChange = {},
                    onToggleFeatured = {},
                    onCreate = {},
                    onUpdate = {},
                    onDelete = {},
                    onEdit = {},
                    onClearEdit = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("adminUpdateProduct").assertIsEnabled()
            onNodeWithTag("adminClearEdit").assertExists()
            onNodeWithText("Edit product").assertExists()
        }

    @Test
    fun deleteClickInvokesCallback() =
        runComposeUiTest {
            var deleted: String? = null
            setContent {
                AdminScreen(
                    categories = emptyList(),
                    categoryName = "",
                    categorySlug = "",
                    products = listOf(sampleProduct()),
                    editingProductId = null,
                    productName = "",
                    productSlug = "",
                    productPrice = "9.99",
                    productStock = "10",
                    productFeatured = false,
                    loggedIn = true,
                    userLabel = "admin@qmarket.local",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onCategoryNameChange = {},
                    onCategorySlugChange = {},
                    onCreateCategory = {},
                    onDeleteCategory = {},
                    onNameChange = {},
                    onSlugChange = {},
                    onPriceChange = {},
                    onStockChange = {},
                    onToggleFeatured = {},
                    onCreate = {},
                    onUpdate = {},
                    onDelete = { deleted = it.id },
                    onEdit = {},
                    onClearEdit = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("adminDeleteProduct")
                .performScrollTo()
                .performClick()
            assertEquals("p1", deleted)
        }

    @Test
    fun slugifyProductNameBasic() {
        assertEquals("hello-world", slugifyProductName("Hello World"))
        assertEquals("a-b", slugifyProductName("  A   B  "))
        assertEquals("product", slugifyProductName("!!!"))
    }

    @Test
    fun createCategoryDisabledWhenEmpty() =
        runComposeUiTest {
            setContent {
                AdminScreen(
                    categories = emptyList(),
                    categoryName = "",
                    categorySlug = "",
                    products = emptyList(),
                    editingProductId = null,
                    productName = "",
                    productSlug = "",
                    productPrice = "9.99",
                    productStock = "10",
                    productFeatured = false,
                    loggedIn = true,
                    userLabel = "admin@qmarket.local",
                    cartCount = 0,
                    error = null,
                    statusMessage = null,
                    loading = false,
                    onBackToCatalog = {},
                    onCategoryNameChange = {},
                    onCategorySlugChange = {},
                    onCreateCategory = {},
                    onDeleteCategory = {},
                    onNameChange = {},
                    onSlugChange = {},
                    onPriceChange = {},
                    onStockChange = {},
                    onToggleFeatured = {},
                    onCreate = {},
                    onUpdate = {},
                    onDelete = {},
                    onEdit = {},
                    onClearEdit = {},
                    onCart = {},
                    onOrders = {},
                    onAddresses = {},
                    onProfile = {},
                    onLogout = {},
                )
            }
            onNodeWithTag("adminCreateCategory").assertIsNotEnabled()
        }
}
