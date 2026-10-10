package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.api.CreateCategoryRequestDto
import com.kenlikdev.qmarket.api.CreateProductRequestDto
import com.kenlikdev.qmarket.api.OrderStatusDto
import com.kenlikdev.qmarket.api.ProductDto
import com.kenlikdev.qmarket.api.UpdateCategoryRequestDto
import com.kenlikdev.qmarket.api.UpdateProductRequestDto
import com.kenlikdev.qmarket.ui.AppScreen

/**
 * Admin catalog/orders/users actions for [QMarketAppModel].
 * Kept in a separate file to keep the core model focused on shopper flow.
 */

fun QMarketAppModel.openAdmin() {
    if (!canManageStore) {
        error = "Store administration requires an administrator or manager role"
        return
    }
    runApi {
        categories = api.listAdminCategories()
        adminProducts = api.listAdminProducts(size = 50).content
        adminOrders = api.listAdminOrders().content
        adminUsers =
            if (isAdmin) {
                api.listAdminUsers(q = adminUserQuery.trim().ifBlank { null }).content
            } else {
                emptyList()
            }
        screen = AppScreen.Admin
    }
}

fun QMarketAppModel.searchAdminUsers() {
    if (!isAdmin) {
        adminUsers = emptyList()
        return
    }
    runApi {
        adminUsers =
            api.listAdminUsers(q = adminUserQuery.trim().ifBlank { null }).content
    }
}

fun QMarketAppModel.createCategory() {
    if (!canManageStore) {
        error = "Store management requires an administrator or manager role"
        return
    }
    runApi {
        api.createCategory(
            CreateCategoryRequestDto(
                name = adminCategoryName.trim(),
                slug = adminCategorySlug.trim(),
            ),
        )
        adminCategoryName = ""
        adminCategorySlug = ""
        categories = api.listAdminCategories()
        catalogCategories = runCatchingCancellable { api.listCategories() }.getOrElse { catalogCategories }
        statusMessage = "Category created"
    }
}

fun QMarketAppModel.updateCategory() {
    if (!canManageStore) {
        error = "Store management requires an administrator or manager role"
        return
    }
    val id = editingCategoryId ?: return
    runApi {
        api.updateCategory(
            id,
            UpdateCategoryRequestDto(
                name = adminCategoryName.trim().ifBlank { null },
                slug = adminCategorySlug.trim().ifBlank { null },
            ),
        )
        editingCategoryId = null
        adminCategoryName = ""
        adminCategorySlug = ""
        categories = api.listAdminCategories()
        catalogCategories = runCatchingCancellable { api.listCategories() }.getOrElse { catalogCategories }
        statusMessage = "Category updated"
    }
}

fun QMarketAppModel.deleteCategory(id: String) {
    if (!isAdmin) {
        error = "Only administrators can delete categories"
        return
    }
    runApi {
        api.deleteCategory(id)
        if (editingCategoryId == id) {
            editingCategoryId = null
            adminCategoryName = ""
            adminCategorySlug = ""
        }
        categories = api.listAdminCategories()
        catalogCategories = runCatchingCancellable { api.listCategories() }.getOrElse { catalogCategories }
        statusMessage = "Category deleted"
    }
}

fun QMarketAppModel.saveProduct() {
    if (!canManageStore) {
        error = "Store management requires an administrator or manager role"
        return
    }
    val editingId = editingProductId
    runApi {
        if (editingId == null) {
            api.createProduct(
                CreateProductRequestDto(
                    name = adminProductName.trim(),
                    slug = adminProductSlug.trim(),
                    price = adminProductPrice.trim(),
                    stockQuantity = adminProductStock.trim().toIntOrNull() ?: 0,
                    featured = adminProductFeatured,
                    categoryId = adminProductCategoryId,
                ),
            )
            statusMessage = "Created product"
        } else {
            api.updateProduct(
                editingId,
                UpdateProductRequestDto(
                    name = adminProductName.trim().ifBlank { null },
                    slug = adminProductSlug.trim().ifBlank { null },
                    price = adminProductPrice.trim().ifBlank { null },
                    stockQuantity = adminProductStock.trim().toIntOrNull(),
                    featured = adminProductFeatured,
                    categoryId = adminProductCategoryId,
                    clearCategory = adminProductCategoryId == null,
                ),
            )
            statusMessage = "Updated product"
        }
        editingProductId = null
        adminProductName = ""
        adminProductSlug = ""
        adminProductPrice = "9.99"
        adminProductStock = "10"
        adminProductFeatured = false
        adminProductCategoryId = null
        adminProducts = api.listAdminProducts(size = 50).content
    }
}

fun QMarketAppModel.deleteProduct(product: ProductDto) {
    if (!isAdmin) {
        error = "Only administrators can delete products"
        return
    }
    runApi {
        api.deleteProduct(product.id)
        statusMessage = "Deleted ${product.name}"
        if (editingProductId == product.id) {
            editingProductId = null
        }
        adminProducts = api.listAdminProducts(size = 50).content
    }
}

fun QMarketAppModel.beginEditProduct(product: ProductDto) {
    editingProductId = product.id
    adminProductName = product.name
    adminProductSlug = product.slug
    adminProductPrice = product.price
    adminProductStock = product.stockQuantity.toString()
    adminProductFeatured = product.featured
    adminProductCategoryId = product.categoryId
    error = null
    statusMessage = null
}

fun QMarketAppModel.clearProductEdit() {
    editingProductId = null
    adminProductName = ""
    adminProductSlug = ""
    adminProductPrice = "9.99"
    adminProductStock = "10"
    adminProductFeatured = false
    adminProductCategoryId = null
}

fun QMarketAppModel.beginEditCategory(
    id: String,
    name: String,
    slug: String,
) {
    editingCategoryId = id
    adminCategoryName = name
    adminCategorySlug = slug
    error = null
    statusMessage = null
}

fun QMarketAppModel.clearCategoryEdit() {
    editingCategoryId = null
    adminCategoryName = ""
    adminCategorySlug = ""
}

fun QMarketAppModel.updateAdminOrderStatus(
    orderId: String,
    status: OrderStatusDto,
) {
    if (!canManageStore) {
        error = "Store management requires an administrator or manager role"
        return
    }
    runApi {
        api.updateAdminOrderStatus(orderId, status)
        adminOrders = api.listAdminOrders().content
        statusMessage = "Order → ${status.name}"
    }
}
