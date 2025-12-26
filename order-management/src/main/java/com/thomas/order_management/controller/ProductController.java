// src/main/java/com/thomas/order_management/controller/ProductController.java
package com.thomas.order_management.controller;

import com.thomas.order_management.controller.assembler.ProductModelAssembler;
import com.thomas.order_management.dto.ProductDto;
import com.thomas.order_management.mapper.ProductMapper;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductModelAssembler assembler;

    @GetMapping
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<ProductDto>>> getAllProducts() {
        List<EntityModel<ProductDto>> products = productRepository.findAll().stream()
                .map(productMapper::toDto)
                .map(productDto -> assembler.toModel(Objects.requireNonNull(productDto, "productDto")))
                .toList();

        return ResponseEntity.ok(CollectionModel.of(products,
                linkTo(methodOn(ProductController.class).getAllProducts()).withSelfRel()));
    }

    @GetMapping("/active")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<ProductDto>>> getActiveProducts() {
        List<EntityModel<ProductDto>> products = productRepository.findByActiveTrue().stream()
                .map(productMapper::toDto)
                .map(productDto -> assembler.toModel(Objects.requireNonNull(productDto, "productDto")))
                .toList();

        return ResponseEntity.ok(CollectionModel.of(products,
                linkTo(methodOn(ProductController.class).getActiveProducts()).withSelfRel()));
    }

    @GetMapping("/available")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<ProductDto>>> getAvailableProducts() {
        List<EntityModel<ProductDto>> products = productRepository.findAvailableProducts().stream()
                .map(productMapper::toDto)
                .map(productDto -> assembler.toModel(Objects.requireNonNull(productDto, "productDto")))
                .toList();

        return ResponseEntity.ok(CollectionModel.of(products,
                linkTo(methodOn(ProductController.class).getAvailableProducts()).withSelfRel()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<ProductDto>> getProductById(@PathVariable Long id) {
        return productRepository.findById(Objects.requireNonNull(id, "id"))
                .map(productMapper::toDto)
                .map(productDto -> assembler.toModel(Objects.requireNonNull(productDto, "productDto")))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @SuppressWarnings("null")
    public ResponseEntity<EntityModel<ProductDto>> createProduct(@RequestBody Product product) {
        Product savedProduct = productRepository.save(product);
        ProductDto productDto = Objects.requireNonNull(productMapper.toDto(savedProduct), "productDto");
        EntityModel<ProductDto> model = assembler.toModel(productDto);

        return ResponseEntity
                .created(model.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(model);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityModel<ProductDto>> updateProduct(@PathVariable Long id, @RequestBody Product productDetails) {
        return productRepository.findById(Objects.requireNonNull(id, "id"))
                .map(product -> {
                    product.setName(productDetails.getName());
                    product.setDescription(productDetails.getDescription());
                    product.setPrice(productDetails.getPrice());
                    product.setStockQuantity(productDetails.getStockQuantity());
                    product.setCategory(productDetails.getCategory());
                    product.setImageUrl(productDetails.getImageUrl());
                    product.setActive(productDetails.getActive());
                    
                    Product savedProduct = productRepository.save(product);
                    ProductDto productDto = Objects.requireNonNull(productMapper.toDto(savedProduct), "productDto");
                    return assembler.toModel(productDto);
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        return productRepository.findById(Objects.requireNonNull(id, "id"))
                .map(product -> {
                    product.setActive(false);
                    productRepository.save(product);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{category}")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<ProductDto>>> getProductsByCategory(@PathVariable String category) {
        List<EntityModel<ProductDto>> products = productRepository.findByCategory(category).stream()
                .map(productMapper::toDto)
                .map(productDto -> assembler.toModel(Objects.requireNonNull(productDto, "productDto")))
                .toList();

        return ResponseEntity.ok(CollectionModel.of(products));
    }

    @GetMapping("/search")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<ProductDto>>> searchProducts(@RequestParam String query) {
        List<EntityModel<ProductDto>> products = productRepository.findByNameContainingIgnoreCase(query).stream()
                .map(productMapper::toDto)
                .map(productDto -> assembler.toModel(Objects.requireNonNull(productDto, "productDto")))
                .toList();

        return ResponseEntity.ok(CollectionModel.of(products));
    }

    @GetMapping("/price-range")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<ProductDto>>> getProductsByPriceRange(
            @RequestParam BigDecimal minPrice, 
            @RequestParam BigDecimal maxPrice) {
        List<EntityModel<ProductDto>> products = productRepository.findByPriceBetween(minPrice, maxPrice).stream()
                .map(productMapper::toDto)
                .map(productDto -> assembler.toModel(Objects.requireNonNull(productDto, "productDto")))
                .toList();

        return ResponseEntity.ok(CollectionModel.of(products));
    }

    @GetMapping("/low-stock")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<ProductDto>>> getLowStockProducts() {
        List<EntityModel<ProductDto>> products = productRepository.findLowStockProducts().stream()
                .map(productMapper::toDto)
                .map(productDto -> assembler.toModel(Objects.requireNonNull(productDto, "productDto")))
                .toList();

        return ResponseEntity.ok(CollectionModel.of(products));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getAllCategories() {
        return ResponseEntity.ok(productRepository.findAllCategories());
    }

    @GetMapping("/count/active")
    public long getActiveProductCount() {
        return productRepository.countActiveProducts();
    }

    @GetMapping("/filter")
    @SuppressWarnings("null")
    public ResponseEntity<CollectionModel<EntityModel<ProductDto>>> filterProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock) {

        List<Product> products = productRepository.findAll();

        if (search != null && !search.trim().isEmpty()) {
            products = products.stream()
                    .filter(p -> p.getName().toLowerCase().contains(search.toLowerCase()) || 
                                 p.getDescription().toLowerCase().contains(search.toLowerCase()))
                    .toList();
        }

        if (category != null && !category.trim().isEmpty()) {
            products = products.stream()
                    .filter(p -> category.equals(p.getCategory()))
                    .toList();
        }

        if (active != null) {
            products = products.stream()
                    .filter(p -> active.equals(p.getActive()))
                    .toList();
        }

        if (minPrice != null) {
            products = products.stream()
                    .filter(p -> p.getPrice().compareTo(minPrice) >= 0)
                    .toList();
        }

        if (maxPrice != null) {
            products = products.stream()
                    .filter(p -> p.getPrice().compareTo(maxPrice) <= 0)
                    .toList();
        }

        if (inStock != null && inStock) {
            products = products.stream()
                    .filter(p -> p.getStockQuantity() > 0)
                    .toList();
        }

        List<EntityModel<ProductDto>> resultModels = products.stream()
                .map(productMapper::toDto)
            .map(productDto -> assembler.toModel(Objects.requireNonNull(productDto, "productDto")))
                .toList();

        return ResponseEntity.ok(CollectionModel.of(resultModels));
    }
}