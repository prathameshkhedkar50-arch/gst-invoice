package com.yourname.gst;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Shop Controller for product management.
 * Implements endpoints from section 8 of plan.md.
 */
@RestController
@RequestMapping("/api")
public class ShopController {

    @Autowired
    private Repos.ProductRepository productRepository;

    @Autowired
    private Security.ShopContext shopContext;

    // Product management endpoints
    @GetMapping("/products")
    public ResponseEntity<List<Models.Product>> getProducts(@RequestParam(required = false) String search) {
        Models.Shop shop = shopContext.getShop();
        if (shop == null) {
            return ResponseEntity.status(401).build();
        }
        List<Models.Product> products;
        if (search == null || search.isBlank()) {
            products = productRepository.findByShop(shop);
        } else {
            // Simple search by name or description (case-insensitive)
            products = productRepository.findByShop(shop).stream()
                    .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(search.toLowerCase()))
                            || (p.getDescription() != null && p.getDescription().toLowerCase().contains(search.toLowerCase())))
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(products);
    }

    @PostMapping("/products")
    public ResponseEntity<Models.Product> createProduct(@RequestBody Models.Product product) {
        Models.Shop shop = shopContext.getShop();
        if (shop == null) {
            return ResponseEntity.status(401).build();
        }
        // Validate custom fields against shop's productFields configuration
        if (!validateCustomFields(shop, product.getCustomFields())) {
            return ResponseEntity.badRequest().build();
        }
        product.setShop(shop);
        // Note: active field has a default value in the model, so we don't need to set it here.
        Models.Product savedProduct = productRepository.save(product);
        return ResponseEntity.ok(savedProduct);
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<Models.Product> updateProduct(@PathVariable Long id, @RequestBody Models.Product productDetails) {
        Models.Shop shop = shopContext.getShop();
        if (shop == null) {
            return ResponseEntity.status(401).build();
        }

        Models.Product product = productRepository.findById(id)
                .filter(p -> p.getShop().getId().equals(shop.getId()))
                .orElseThrow(() -> new RuntimeException("Product not found or access denied"));

        // Validate custom fields if provided
        if (productDetails.getCustomFields() != null && !validateCustomFields(shop, productDetails.getCustomFields())) {
            return ResponseEntity.badRequest().build();
        }

        // Update fields
        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        product.setPrice(productDetails.getPrice());
        product.setHsnCode(productDetails.getHsnCode());
        product.setUnit(productDetails.getUnit());
        if (productDetails.getCustomFields() != null) {
            product.setCustomFields(productDetails.getCustomFields());
        }
        // Update active field from the request (client can activate/deactivate product via PUT)
        product.setActive(productDetails.isActive());

        Models.Product updatedProduct = productRepository.save(product);
        return ResponseEntity.ok(updatedProduct);
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        Models.Shop shop = shopContext.getShop();
        if (shop == null) {
            return ResponseEntity.status(401).build();
        }

        Models.Product product = productRepository.findById(id)
                .filter(p -> p.getShop().getId().equals(shop.getId()))
                .orElseThrow(() -> new RuntimeException("Product not found or access denied"));

        // Soft delete: set active to false
        product.setActive(false);
        productRepository.save(product);
        return ResponseEntity.noContent().build();
    }

    // Configuration endpoint
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Models.Shop shop = shopContext.getShop();
        if (shop == null) {
            return ResponseEntity.status(401).build();
        }

        // Return shop name and config as a map
        java.util.Map<String, Object> config = java.util.Map.of(
            "shopName", shop.getName(),
            "config", shop.getConfig()
        );
        return ResponseEntity.ok(config);
    }

    // Ping endpoint for testing
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        Models.Shop shop = shopContext.getShop();
        if (shop == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(shop.getCode());
    }

    /**
     * Validates the given custom fields object against the shop's productFields configuration.
     * @param shop the shop to get configuration from
     * @param customFieldsObject the custom fields to validate (may be null or any object)
     * @return true if valid, false otherwise
     */
    private boolean validateCustomFields(Models.Shop shop, Object customFieldsObject) {
        Map<String, Object> customFields;
        if (customFieldsObject == null) {
            customFields = new java.util.HashMap<>();
        } else {
            // We assume the customFieldsObject is a Map (as stored in the database)
            @SuppressWarnings("unchecked")
            Map<String, Object> temp = (Map<String, Object>) customFieldsObject;
            customFields = temp;
        }

        if (shop.getConfig() == null) {
            // If no config, then no custom fields allowed
            return customFields.isEmpty();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> configMap = (Map<String, Object>) shop.getConfig();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> productFields = (List<Map<String, Object>>) configMap.get("productFields");
        if (productFields == null) {
            // No productFields configured, so no custom fields allowed
            return customFields.isEmpty();
        }

        // Check each provided custom field against the configuration
        for (Map.Entry<String, Object> entry : customFields.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Find the field configuration for this key
            boolean found = false;
            for (Map<String, Object> field : productFields) {
                if (Objects.equals(field.get("key"), key)) {
                    found = true;
                    // Validate required
                    Boolean required = (Boolean) field.get("required");
                    if (required != null && required) {
                        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                            return false; // required field is empty
                        }
                    }
                    // Validate type
                    String type = (String) field.get("type");
                    if (type != null) {
                        switch (type) {
                            case "text":
                                if (!(value instanceof String)) {
                                    return false;
                                }
                                break;
                            case "number":
                                if (!(value instanceof Number)) {
                                    return false;
                                }
                                break;
                            case "date":
                                // We'll accept string that can be parsed as date, but for simplicity, we'll just check if it's a string
                                // In a real app, we would parse it. We'll accept string.
                                if (!(value instanceof String)) {
                                    return false;
                                }
                                break;
                            case "select":
                                if (!(value instanceof String)) {
                                    return false;
                                }
                                // Check if the value is in the options list
                                @SuppressWarnings("unchecked")
                                List<String> options = (List<String>) field.get("options");
                                if (options != null && !options.contains(value)) {
                                    return false;
                                }
                                break;
                            default:
                                // Unknown type, accept as string
                                if (!(value instanceof String)) {
                                    return false;
                                }
                        }
                    }
                    break;
                }
            }
            if (!found) {
                // Field not configured in productFields
                return false;
            }
        }
        // All provided fields are valid
        return true;
    }
}