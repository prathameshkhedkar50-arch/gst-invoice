package com.yourname.gst;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CommandLineRunner to seed sample data when app.seed=true is set.
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private Repos.ShopRepository shopRepository;

    @Autowired
    private Repos.AccessTokenRepository tokenRepository;

    @Autowired
    private Repos.ProductRepository productRepository;

    @Autowired
    private Environment environment;

    @Override
    public void run(String... args) throws Exception {
        // Check if seeding is enabled
        if (!environment.getProperty("app.seed", Boolean.class, false)) {
            return;
        }

        System.out.println("Seeding sample data...");

        // Create two sample shops
        Models.Shop kiranaShop = new Models.Shop();
        kiranaShop.setCode("KIRANA001");
        kiranaShop.setName("Kirana Store");
        kiranaShop.setActive(true);
        // Config for kirana store (no custom fields)
        java.util.Map<String, Object> kiranaConfig = new java.util.HashMap<>();
        kiranaConfig.put("shopName", "Kirana Store");
        kiranaConfig.put("gstin", "27AAAAA1111A1Z1");
        kiranaConfig.put("stateCode", "27");
        kiranaConfig.put("address", "123 Main Street, City");
        kiranaConfig.put("city", "City");
        kiranaConfig.put("state", "Maharashtra");
        kiranaConfig.put("pincode", "400001");
        kiranaConfig.put("phone", "9876543210");
        kiranaConfig.put("invoicePrefix", "KIR");
        kiranaConfig.put("financialYearStart", "04");
        kiranaConfig.put("defaultGstRate", 18);
        kiranaConfig.put("billFields", new java.util.ArrayList<>());
        kiranaConfig.put("productFields", new java.util.ArrayList<>());
        kiranaConfig.put("scanFields", new java.util.ArrayList<>());
        java.util.Map<String, Object> kiranaLimits = java.util.Map.of("scansPerDay", 50);
        kiranaConfig.put("limits", kiranaLimits);
        kiranaShop.setConfig(kiranaConfig);

        Models.Shop pharmacyShop = new Models.Shop();
        pharmacyShop.setCode("PHARMA001");
        pharmacyShop.setName("Pharmacy Store");
        pharmacyShop.setActive(true);
        // Config for pharmacy store with custom fields: batchNo and expiry
        java.util.Map<String, Object> pharmaConfig = new java.util.HashMap<>();
        pharmaConfig.put("shopName", "Pharmacy Store");
        pharmaConfig.put("gstin", "27BBBBB2222B2Z2");
        pharmaConfig.put("stateCode", "27");
        pharmaConfig.put("address", "456 Health Avenue, City");
        pharmaConfig.put("city", "City");
        pharmaConfig.put("state", "Maharashtra");
        pharmaConfig.put("pincode", "400002");
        pharmaConfig.put("phone", "9876543211");
        pharmaConfig.put("invoicePrefix", "PHM");
        pharmaConfig.put("financialYearStart", "04");
        pharmaConfig.put("defaultGstRate", 12);
        pharmaConfig.put("billFields", new java.util.ArrayList<>());
        // Product fields
        java.util.List<Map<String, Object>> pharmaProductFields = new java.util.ArrayList<>();
        java.util.Map<String, Object> batchNoField = new java.util.HashMap<>();
        batchNoField.put("key", "batchNo");
        batchNoField.put("label", "Batch No");
        batchNoField.put("type", "text");
        batchNoField.put("required", false);
        pharmaProductFields.add(batchNoField);
        java.util.Map<String, Object> expiryField = new java.util.HashMap<>();
        expiryField.put("key", "expiry");
        expiryField.put("label", "Expiry Date");
        expiryField.put("type", "date");
        expiryField.put("required", true);
        pharmaProductFields.add(expiryField);
        pharmaConfig.put("productFields", pharmaProductFields);
        // Scan fields
        pharmaConfig.put("scanFields", java.util.List.of("supplierName", "supplierGstin", "invoiceNo", "invoiceDate", "taxableAmount", "cgst", "sgst", "igst", "total"));
        // Limits
        java.util.Map<String, Object> pharmaLimits = java.util.Map.of("scansPerDay", 30);
        pharmaConfig.put("limits", pharmaLimits);
        pharmacyShop.setConfig(pharmaConfig);

        // Save shops
        Models.Shop savedKirana = shopRepository.save(kiranaShop);
        Models.Shop savedPharmacy = shopRepository.save(pharmacyShop);

        System.out.println("Created Kirana Shop with ID: " + savedKirana.getId());
        System.out.println("Created Pharmacy Shop with ID: " + savedPharmacy.getId());

        // Create sample products for Kirana shop
        List<Models.Product> kiranaProducts = new ArrayList<>();
        Models.Product rice = new Models.Product();
        rice.setName("Rice");
        rice.setDescription("Basmati Rice");
        rice.setPrice(new BigDecimal("60.0"));
        rice.setHsnCode("1006");
        rice.setUnit("Kg");
        rice.setActive(true);
        rice.setShop(savedKirana);
        kiranaProducts.add(rice);

        Models.Product wheat = new Models.Product();
        wheat.setName("Wheat");
        wheat.setDescription("Whole Wheat");
        wheat.setPrice(new BigDecimal("40.0"));
        wheat.setHsnCode("1001");
        wheat.setUnit("Kg");
        wheat.setActive(true);
        wheat.setShop(savedKirana);
        kiranaProducts.add(wheat);

        Models.Product oil = new Models.Product();
        oil.setName("Cooking Oil");
        oil.setDescription("Refined Sunflower Oil");
        oil.setPrice(new BigDecimal("120.0"));
        oil.setHsnCode("1512");
        oil.setUnit("L");
        oil.setActive(true);
        oil.setShop(savedKirana);
        kiranaProducts.add(oil);

        // Save products for Kirana shop
        for (Models.Product product : kiranaProducts) {
            productRepository.save(product);
        }

        // Create sample products for Pharmacy shop
        List<Models.Product> pharmaProducts = new ArrayList<>();
        Models.Product paracetamol = new Models.Product();
        paracetamol.setName("Paracetamol");
        paracetamol.setDescription("500mg Tablet");
        paracetamol.setPrice(new BigDecimal("25.0"));
        paracetamol.setHsnCode("3004");
        paracetamol.setUnit("Strip");
        paracetamol.setActive(true);
        paracetamol.setShop(savedPharmacy);
        // Add custom fields for this product
        Map<String, Object> paracetamolCustomFields = new HashMap<>();
        paracetamolCustomFields.put("batchNo", "BATCH001");
        paracetamolCustomFields.put("expiry", "2025-12-31");
        paracetamol.setCustomFields(paracetamolCustomFields);
        pharmaProducts.add(paracetamol);

        Models.Product amoxicillin = new Models.Product();
        amoxicillin.setName("Amoxicillin");
        amoxicillin.setDescription("250mg Capsule");
        amoxicillin.setPrice(new BigDecimal("80.0"));
        amoxicillin.setHsnCode("3004");
        amoxicillin.setUnit("Strip");
        amoxicillin.setActive(true);
        amoxicillin.setShop(savedPharmacy);
        // Add custom fields for this product
        Map<String, Object> amoxicillinCustomFields = new HashMap<>();
        amoxicillinCustomFields.put("batchNo", "BATCH002");
        amoxicillinCustomFields.put("expiry", "2026-06-30");
        amoxicillin.setCustomFields(amoxicillinCustomFields);
        pharmaProducts.add(amoxicillin);

        // Save products for Pharmacy shop
        for (Models.Product product : pharmaProducts) {
            productRepository.save(product);
        }

        // Generate tokens for each shop
        String kiranaToken = generateTokenForShop(savedKirana);
        String pharmacyToken = generateTokenForShop(savedPharmacy);

        System.out.println("Generated token for Kirana Shop: " + kiranaToken);
        System.out.println("Generated token for Pharmacy Shop: " + pharmacyToken);

        System.out.println("Seeding completed.");
    }

    /**
     * Generate a token for the given shop and save it.
     * @param shop the shop for which to generate the token
     * @return the plain token (only shown once)
     */
    private String generateTokenForShop(Models.Shop shop) {
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        String randomPart = Base64.getEncoder().encodeToString(randomBytes);
        String token = "gst_" + randomPart;

        // Hash the token for storage
        String tokenHash = hashToken(token);

        // Create and save access token
        Models.AccessToken accessToken = new Models.AccessToken();
        accessToken.setTokenHash(tokenHash);
        accessToken.setLabel("Seeded token");
        accessToken.setExpiresAt(LocalDateTime.now().plusHours(24)); // 24 hour expiry
        accessToken.setRevoked(false);
        accessToken.setLastUsedAt(null);
        accessToken.setCreatedAt(LocalDateTime.now());
        accessToken.setShop(shop);

        tokenRepository.save(accessToken);

        return token;
    }

    /**
     * Hash a token using SHA-256.
     * @param token the plain token
     * @return the base64-encoded SHA-256 hash
     */
    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes());
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Token hashing failed", e);
        }
    }
}