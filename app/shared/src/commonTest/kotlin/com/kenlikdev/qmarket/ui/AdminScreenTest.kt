package com.kenlikdev.qmarket.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kenlikdev.qmarket.api.AdminUserDto
import com.kenlikdev.qmarket.api.CategoryDto
import com.kenlikdev.qmarket.api.OrderDto
import com.kenlikdev.qmarket.api.OrderStatusDto
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
                    adminOrders = emptyList(),
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
                    onUpdateOrderStatus = { _, _ -> },
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
                    adminOrders = emptyList(),
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
                    onUpdateOrderStatus = { _, _ -> },
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
                    adminOrders = emptyList(),
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
                    onUpdateOrderStatus = { _, _ -> },
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
                    adminOrders = emptyList(),
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
                    onUpdateOrderStatus = { _, _ -> },
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
                    adminOrders = emptyList(),
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
                    onUpdateOrderStatus = { _, _ -> },
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

    @Test
    fun adminOrderShowsNextStatusActions() =
        runComposeUiTest {
            val order =
                OrderDto(
                    id = "o1",
                    userId = "u1",
                    status = OrderStatusDto.PENDING,
                    totalAmount = "10.00",
                )
            var updated: OrderStatusDto? = null
            setContent {
                AdminScreen(
                    categories = emptyList(),
                    categoryName = "",
                    categorySlug = "",
                    adminOrders = listOf(order),
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
                    onUpdateOrderStatus = { _, s -> updated = s },
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
            onNodeWithTag("adminOrderStatus_CONFIRMED")
                .performScrollTo()
                .performClick()
            assertEquals(OrderStatusDto.CONFIRMED, updated)
        }

    @Test
    fun editCategoryModeShowsSaveAndCancel() =
        runComposeUiTest {
            val category =
                CategoryDto(
                    id = "c1",
                    name = "Electronics",
                    slug = "electronics",
                    active = true,
                )
            var editClicked = false
            setContent {
                AdminScreen(
                    categories = listOf(category),
                    categoryName = "Electronics",
                    categorySlug = "electronics",
                    editingCategoryId = "c1",
                    adminOrders = emptyList(),
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
                    onEditCategory = { editClicked = true },
                    onUpdateCategory = {},
                    onClearCategoryEdit = {},
                    onUpdateOrderStatus = { _, _ -> },
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
            onNodeWithText("Edit category").assertExists()
            onNodeWithTag("adminUpdateCategory").assertIsEnabled()
            onNodeWithTag("adminClearCategoryEdit").assertExists()
            onNodeWithTag("adminEditCategory").assertExists()
        }

    @Test
    fun editCategoryClickInvokesCallback() =
        runComposeUiTest {
            val category =
                CategoryDto(
                    id = "c1",
                    name = "Electronics",
                    slug = "electronics",
                    active = true,
                )
            var editedId: String? = null
            setContent {
                AdminScreen(
                    categories = listOf(category),
                    categoryName = "",
                    categorySlug = "",
                    adminOrders = emptyList(),
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
                    onEditCategory = { editedId = it.id },
                    onUpdateCategory = {},
                    onClearCategoryEdit = {},
                    onUpdateOrderStatus = { _, _ -> },
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
            onNodeWithTag("adminEditCategory").performClick()
            assertEquals("c1", editedId)
        }

    @Test
    fun productCategoryNoneSelectInvokesCallback() =
        runComposeUiTest {
            var selected: String? = "keep"
            setContent {
                AdminScreen(
                    categories =
                        listOf(
                            CategoryDto(id = "c1", name = "Electronics", slug = "electronics"),
                        ),
                    categoryName = "",
                    categorySlug = "",
                    products = emptyList(),
                    editingProductId = null,
                    productName = "Widget",
                    productSlug = "widget",
                    productPrice = "12.50",
                    productStock = "3",
                    productFeatured = false,
                    productCategoryId = "c1",
                    onProductCategoryChange = { selected = it },
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
            onNodeWithTag("adminProductCategoryNone")
                .assertExists()
                .performScrollTo()
                .performClick()
            waitForIdle()
            assertEquals(null, selected)
        }

    @Test
    fun adminUsersListShowsEmailAndSearch() =
        runComposeUiTest {
            setContent {
                AdminScreen(
                    categories = emptyList(),
                    categoryName = "",
                    categorySlug = "",
                    adminOrders = emptyList(),
                    adminUsers =
                        listOf(
                            AdminUserDto(
                                id = "u1",
                                email = "shopper@test.local",
                                firstName = "Sam",
                                lastName = "Shop",
                                enabled = true,
                                roles = listOf("ROLE_USER"),
                            ),
                        ),
                    adminUserQuery = "shop",
                    products = emptyList(),
                    editingProductId = null,
                    productName = "Widget",
                    productSlug = "widget",
                    productPrice = "10",
                    productStock = "1",
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
                    onUpdateOrderStatus = { _, _ -> },
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
            onNodeWithTag("adminUserQuery").assertExists()
            onNodeWithTag("adminUserSearch").assertExists()
            onNodeWithTag("adminUserCard").performScrollTo().assertExists()
            onNodeWithText("shopper@test.local").assertExists()
        }

}
