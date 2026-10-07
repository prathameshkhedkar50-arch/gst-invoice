package com.yourname.gst;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Bill Controller for billing operations.
 * Implements endpoints from section 8 of plan.md.
 */
@RestController
@RequestMapping("/api/bills")
public class BillController {

    @Autowired
    private Security.ShopContext shopContext;

    @Autowired
    private GstService gstService;

    @Autowired
    private Repos.InvoiceRepository invoiceRepository;

    // GSTIN pattern: 2 digits, 5 uppercase letters, 4 digits, 1 uppercase letter, 1 digit, 1 uppercase letter
    private static final Pattern GSTIN_PATTERN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9Z][A-Z]$");

    /**
     * Create a new bill (invoice).
     * Expects a JSON object with bill details and line items.
     * The shop is identified by the X-Shop-Token header (validated by filter).
     * @param billData Map containing bill details and line items
     * @return Created invoice
     */
    @PostMapping
    public ResponseEntity<Models.Invoice> createBill(@RequestHeader("X-Shop-Token") String token, @RequestBody Map<String, Object> billData) {
        // Get the shop from the ShopContext (set by TokenFilter)
        Models.Shop shop = shopContext.getShop();
        if (shop == null) {
            return ResponseEntity.status(401).build();
        }

        // Validate customerGstin if present
        Object customerGstinObj = billData.get("customerGstin");
        if (customerGstinObj != null) {
            String customerGstin = customerGstinObj.toString();
            if (!GSTIN_PATTERN.matcher(customerGstin).matches()) {
                return ResponseEntity.badRequest().build();
            }
        }

        // Validate custom fields against billFields configuration
        Object customObj = billData.get("custom");
        Map<String, Object> customFields = (customObj instanceof Map) ? (Map<String, Object>) customObj : null;
        if (!validateCustomFields(shop, customFields)) {
            return ResponseEntity.badRequest().build();
        }

        // Create invoice from the provided data (which should contain customer details and line items)
        Models.Invoice invoice = gstService.createInvoiceFromScan(shop, billData);
        return ResponseEntity.ok(invoice);
    }

    /**
     * Validate custom fields against the shop's billFields configuration.
     * @param shop the shop to get configuration from
     * @param customFields the custom fields to validate (may be null)
     * @return true if valid, false otherwise
     */
    private boolean validateCustomFields(Models.Shop shop, Map<String, Object> customFields) {
        if (shop.getConfig() == null) {
            // If no config, then no custom fields allowed
            return customFields == null || customFields.isEmpty();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> configMap = (Map<String, Object>) shop.getConfig();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> billFields = (List<Map<String, Object>>) configMap.get("billFields");
        if (billFields == null) {
            // No billFields configured, so no custom fields allowed
            return customFields == null || customFields.isEmpty();
        }

        if (customFields == null) {
            // No custom fields provided, that's fine
            return true;
        }

        // Check each provided custom field against the configuration
        for (Map.Entry<String, Object> entry : customFields.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Find the field configuration for this key
            boolean found = false;
            for (Map<String, Object> field : billFields) {
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
                // Field not configured in billFields
                return false;
            }
        }
        // All provided fields are valid
        return true;
    }

    /**
     * Get a bill by its ID.
     * @param billId The ID of the bill to retrieve
     * @return The bill if found and belongs to the current shop
     */
    @GetMapping("/{id}")
    public ResponseEntity<Models.Invoice> getBill(@PathVariable Long id) {
        Models.Shop shop = shopContext.getShop();
        if (shop == null) {
            return ResponseEntity.status(401).build();
        }

        // Find the invoice by ID and check that it belongs to the current shop
        return invoiceRepository.findById(id)
                .filter(invoice -> invoice.getShop().getId().equals(shop.getId()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Get the PDF of a bill by its ID.
     * @param billId The ID of the bill to retrieve PDF for
     * @return The PDF bytes
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getBillPdf(@PathVariable Long id) {
        Models.Shop shop = shopContext.getShop();
        if (shop == null) {
            return ResponseEntity.status(401).build();
        }

        // Find the invoice by ID and check that it belongs to the current shop
        Models.Invoice invoice = invoiceRepository.findById(id)
                .filter(inv -> inv.getShop().getId().equals(shop.getId()))
                .orElse(null);
        if (invoice == null) {
            return ResponseEntity.notFound().build();
        }

        // Generate PDF using PdfService (to be implemented)
        // For now, we'll return a placeholder
        byte[] pdfBytes = "PDF not implemented yet".getBytes();
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .body(pdfBytes);
    }

    /**
     * Get a list of bills for the current shop with optional filtering.
     * Supports filtering by date range (from, to) and search (customer name or invoice number).
     * Results are ordered by invoice date descending (newest first).
     * @param from Start date for filtering (inclusive, format: yyyy-MM-dd)
     * @param to End date for filtering (inclusive, format: yyyy-MM-dd)
     * @param search Search term for customer name or invoice number
     * @return List of invoices
     */
    @GetMapping
    public ResponseEntity<List<Models.Invoice>> getBills(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String search) {
        Models.Shop shop = shopContext.getShop();
        if (shop == null) {
            return ResponseEntity.status(401).build();
        }

        LocalDateTime startDate = null;
        LocalDateTime endDate = null;

        // Parse date parameters if provided
        if (from != null && !from.isEmpty()) {
            try {
                startDate = LocalDate.parse(from).atStartOfDay();
            } catch (Exception e) {
                return ResponseEntity.badRequest().build();
            }
        }
        if (to != null && !to.isEmpty()) {
            try {
                endDate = LocalDate.parse(to).atTime(23, 59, 59);
            } catch (Exception e) {
                return ResponseEntity.badRequest().build();
            }
        }

        List<Models.Invoice> invoices;
        if (startDate != null && endDate != null) {
            // Get invoices within date range
            invoices = invoiceRepository.findByShopAndInvoiceDateBetween(shop, startDate, endDate);
        } else if (startDate != null) {
            // Get invoices from start date onwards
            invoices = invoiceRepository.findByShopAndInvoiceDateAfter(shop, startDate);
        } else if (endDate != null) {
            // Get invoices up to end date
            invoices = invoiceRepository.findByShopAndInvoiceDateBefore(shop, endDate);
        } else {
            // Get all invoices for the shop
            invoices = invoiceRepository.findByShop(shop);
        }

        // Apply search filter if provided
        if (search != null && !search.isEmpty()) {
            String lowerSearch = search.toLowerCase();
            invoices = invoices.stream()
                    .filter(invoice ->
                        (invoice.getCustomerName() != null &&
                         invoice.getCustomerName().toLowerCase().contains(lowerSearch)) ||
                        (invoice.getInvoiceNumber() != null &&
                         invoice.getInvoiceNumber().toLowerCase().contains(lowerSearch)))
                    .collect(Collectors.toList());
        }

        // Sort by invoice date descending (newest first)
        invoices.sort(Comparator.comparing(Models.Invoice::getInvoiceDate).reversed());

        return ResponseEntity.ok(invoices);
    }
}