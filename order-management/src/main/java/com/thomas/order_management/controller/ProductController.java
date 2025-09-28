package com.thomas.order_management.controller;

import com.thomas.order_management.model.Product;
import com.thomas.order_management.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // Alle Produkte abrufen
    @GetMapping
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    // Nur aktive Produkte abrufen
    @GetMapping("/active")
    public List<Product> getActiveProducts() {
        return productRepository.findByActiveTrue();
    }

    // Verfügbare Produkte (auf Lager und aktiv)
    @GetMapping("/available")
    public List<Product> getAvailableProducts() {
        return productRepository.findAvailableProducts();
    }

    // Product nach ID abrufen
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        Optional<Product> product = productRepository.findById(id);
        return product.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }

    // Produkt erstellen
    @PostMapping
    public Product createProduct(@RequestBody Product product) {
        return productRepository.save(product);
    }

    // Produkt aktualisieren
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id, @RequestBody Product productDetails) {
        Optional<Product> optionalProduct = productRepository.findById(id);
        
        if (optionalProduct.isPresent()) {
            Product product = optionalProduct.get();
            product.setName(productDetails.getName());
            product.setDescription(productDetails.getDescription());
            product.setPrice(productDetails.getPrice());
            product.setStockQuantity(productDetails.getStockQuantity());
            product.setCategory(productDetails.getCategory());
            product.setImageUrl(productDetails.getImageUrl());
            product.setActive(productDetails.getActive());
            
            return ResponseEntity.ok(productRepository.save(product));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Produkt löschen (soft delete - als inaktiv markieren)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        Optional<Product> optionalProduct = productRepository.findById(id);
        
        if (optionalProduct.isPresent()) {
            Product product = optionalProduct.get();
            product.setActive(false);
            productRepository.save(product);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Produkte nach Kategorie
    @GetMapping("/category/{category}")
    public List<Product> getProductsByCategory(@PathVariable String category) {
        return productRepository.findByCategory(category);
    }

    // Produkte suchen
    @GetMapping("/search")
    public List<Product> searchProducts(@RequestParam String query) {
        return productRepository.findByNameContainingIgnoreCase(query);
    }

    // Produkte nach Preisbereich
    @GetMapping("/price-range")
    public List<Product> getProductsByPriceRange(@RequestParam BigDecimal minPrice, @RequestParam BigDecimal maxPrice) {
        return productRepository.findByPriceBetween(minPrice, maxPrice);
    }

    // Produkte mit niedrigem Lagerbestand
    @GetMapping("/low-stock")
    public List<Product> getLowStockProducts() {
        return productRepository.findLowStockProducts();
    }

    // Alle Kategorien
    @GetMapping("/categories")
    public List<String> getAllCategories() {
        return productRepository.findAllCategories();
    }

    // Anzahl aktiver Produkte
    @GetMapping("/count")
    public long getActiveProductCount() {
        return productRepository.countActiveProducts();
    }
}