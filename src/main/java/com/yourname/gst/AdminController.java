package com.yourname.gst;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin Controller for shop and token management.
 * Implements endpoints from section 8 of plan.md.
 */
@RestController
@RequestMapping("/admin-api")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private Repos.ShopRepository shopRepository;

    @Autowired
    private Repos.AccessTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Shop management endpoints
    @PostMapping("/shops")
    public ResponseEntity<Models.Shop> createShop(@RequestBody Models.Shop shop) {
        // Validate shop data, set defaults, save to repository
        Models.Shop savedShop = shopRepository.save(shop);
        return ResponseEntity.ok(savedShop);
    }

    @PutMapping("/shops/{id}")
    public ResponseEntity<Models.Shop> updateShop(@PathVariable Long id, @RequestBody Models.Shop shopDetails) {
        // Find shop by id, update fields, save
        Models.Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shop not found"));
        // Update fields from shopDetails
        shop.setCode(shopDetails.getCode());
        shop.setName(shopDetails.getName());
        shop.setConfig(shopDetails.getConfig());
        shop.setActive(shopDetails.isActive());

        Models.Shop updatedShop = shopRepository.save(shop);
        return ResponseEntity.ok(updatedShop);
    }

    @GetMapping("/shops")
    public ResponseEntity<List<Models.Shop>> listShops() {
        // Return all shops
        List<Models.Shop> shops = shopRepository.findAll();
        return ResponseEntity.ok(shops);
    }

    @PostMapping("/shops/{id}/tokens")
    public ResponseEntity<String> generateToken(@PathVariable Long id) {
        // 1. Find shop by id
        // 2. Generate random token: "gst_" + 32 random chars via SecureRandom
        // 3. Hash token with SHA-256
        // 4. Save AccessToken with hash, expiry (24 hours), shop reference
        // 5. Return plain token ONLY ONCE (never store plain token)

        Models.Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shop not found"));

        // Generate token: "gst_" + 32 random chars
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
        accessToken.setLabel("Generated token"); // Default label
        accessToken.setExpiresAt(LocalDateTime.now().plusHours(24)); // 24 hour expiry
        accessToken.setRevoked(false);
        accessToken.setLastUsedAt(null); // Not used yet
        accessToken.setCreatedAt(LocalDateTime.now());
        accessToken.setShop(shop);

        tokenRepository.save(accessToken);

        // Return plain token only once
        return ResponseEntity.ok(token);
    }

    // NEW: Get tokens for a shop (without exposing hash or plain token)
    @GetMapping("/shops/{id}/tokens")
    public ResponseEntity<List<Map<String, Object>>> getShopTokens(@PathVariable Long id) {
        // Verify the shop exists
        Models.Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shop not found"));
        // Get tokens for the shop
        List<Models.AccessToken> tokens = tokenRepository.findByShop(shop);
        // Convert to list of maps with only the allowed fields
        List<Map<String, Object>> result = tokens.stream().map(token -> {
            Map<String, Object> tokenMap = new HashMap<>();
            tokenMap.put("id", token.getId());
            tokenMap.put("label", token.getLabel());
            tokenMap.put("expires_at", token.getExpiresAt());
            tokenMap.put("revoked", token.isRevoked());
            tokenMap.put("last_used_at", token.getLastUsedAt());
            return tokenMap;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/tokens/{id}")
    public ResponseEntity<Void> revokeToken(@PathVariable Long id) {
        // Find token by id and mark as revoked
        Models.AccessToken token = tokenRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        token.setRevoked(true);
        tokenRepository.save(token);

        return ResponseEntity.noContent().build();
    }

    // Helper method to hash tokens
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