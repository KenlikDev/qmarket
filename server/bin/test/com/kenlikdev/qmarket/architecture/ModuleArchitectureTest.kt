package com.kenlikdev.qmarket.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * Module boundary rules for the modular monolith.
 *
 * Catalog exports: [com.kenlikdev.qmarket.catalog.api.ProductCatalog]
 * Cart/Order may use catalog.api only — not catalog domain/JPA.
 */
@AnalyzeClasses(
    packages = ["com.kenlikdev.qmarket"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class ModuleArchitectureTest {
    @ArchTest
    val cartDoesNotDependOnCatalogInternals: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..cart..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..catalog.domain..",
                "..catalog.repository..",
                "..catalog.service..",
                "..catalog.adapter..",
                "..catalog.web..",
            ).because("cart must use catalog.api (ProductCatalog) only")

    @ArchTest
    val orderDoesNotDependOnCatalogInternals: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..order..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..catalog.domain..",
                "..catalog.repository..",
                "..catalog.service..",
                "..catalog.adapter..",
                "..catalog.web..",
            ).because("order must use catalog.api (ProductCatalog) only")

    @ArchTest
    val webDoesNotDependOnRepositories: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..")
            .because("controllers must not access repositories directly")

    @ArchTest
    val catalogApiIsPure: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..catalog.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..catalog.domain..",
                "..catalog.repository..",
                "jakarta.persistence..",
                "org.springframework..",
            ).because("catalog.api must stay a pure module contract")
}
