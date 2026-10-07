package com.yourname.gst;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;

/**
 * JPA Entities for the GST Invoice Processor.
 * All tenant entities include shop_id for multi-tenancy.
 */
public class Models {

    @Entity
    public static class Shop {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String code; // short unique identifier (e.g., "sharma01")
        private String name;

        @JdbcTypeCode(SqlTypes.JSON)
        private Object config; // JSONB column for shop configuration (includes GSTIN, address, etc.)

        private boolean active = true;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Object getConfig() { return config; }
        public void setConfig(Object config) { this.config = config; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    @Entity
    public static class AccessToken {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String tokenHash; // SHA-256 hash of the token
        private String label;
        private LocalDateTime expiresAt;
        private boolean revoked = false;
        private LocalDateTime lastUsedAt;
        private LocalDateTime createdAt;

        @ManyToOne
        @JoinColumn(name = "shop_id", nullable = false)
        private Shop shop;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTokenHash() { return tokenHash; }
        public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        public boolean isRevoked() { return revoked; }
        public void setRevoked(boolean revoked) { this.revoked = revoked; }
        public LocalDateTime getLastUsedAt() { return lastUsedAt; }
        public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public Shop getShop() { return shop; }
        public void setShop(Shop shop) { this.shop = shop; }
    }

    @Entity
    public static class Product {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String name;
        private String description;
        private BigDecimal price;
        private String hsnCode;
        private String unit;
        private BigDecimal gstRate = new BigDecimal("18"); // default 18%

        @JdbcTypeCode(SqlTypes.JSON)
        private Object customFields; // JSONB column for custom fields

        @ManyToOne
        @JoinColumn(name = "shop_id", nullable = false)
        private Shop shop;

        private boolean active = true;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public String getHsnCode() { return hsnCode; }
        public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public BigDecimal getGstRate() {
            return gstRate;
        }

        public void setGstRate(BigDecimal gstRate) {
            this.gstRate = gstRate;
        }

        public Object getCustomFields() { return customFields; }
        public void setCustomFields(Object customFields) { this.customFields = customFields; }
        public Shop getShop() { return shop; }
        public void setShop(Shop shop) { this.shop = shop; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    @Entity
    public static class Invoice {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String invoiceNumber;
        private LocalDateTime invoiceDate;
        private LocalDateTime dueDate;
        private String customerName;
        private String customerGstin;
        private String customerAddress;
        private String customerStateCode;

        @JdbcTypeCode(SqlTypes.JSON)
        private Object customFields; // JSONB column for custom fields

        @ManyToOne
        @JoinColumn(name = "shop_id", nullable = false)
        private Shop shop;
        private BigDecimal taxableAmount = BigDecimal.ZERO;
        private BigDecimal cgstAmount = BigDecimal.ZERO;
        private BigDecimal sgstAmount = BigDecimal.ZERO;
        private BigDecimal igstAmount = BigDecimal.ZERO;
        private BigDecimal roundOffAmount = BigDecimal.ZERO;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getInvoiceNumber() { return invoiceNumber; }
        public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
        public LocalDateTime getInvoiceDate() { return invoiceDate; }
        public void setInvoiceDate(LocalDateTime invoiceDate) { this.invoiceDate = invoiceDate; }
        public LocalDateTime getDueDate() { return dueDate; }
        public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public String getCustomerGstin() { return customerGstin; }
        public void setCustomerGstin(String customerGstin) { this.customerGstin = customerGstin; }
        public String getCustomerAddress() { return customerAddress; }
        public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }
        public String getCustomerStateCode() { return customerStateCode; }
        public void setCustomerStateCode(String customerStateCode) { this.customerStateCode = customerStateCode; }
        public Object getCustomFields() { return customFields; }
        public void setCustomFields(Object customFields) { this.customFields = customFields; }
        public Shop getShop() { return shop; }
        public void setShop(Shop shop) { this.shop = shop; }

        public BigDecimal getTaxableAmount() {
            return taxableAmount;
        }

        public void setTaxableAmount(BigDecimal taxableAmount) {
            this.taxableAmount = taxableAmount;
        }

        public BigDecimal getCgstAmount() {
            return cgstAmount;
        }

        public void setCgstAmount(BigDecimal cgstAmount) {
            this.cgstAmount = cgstAmount;
        }

        public BigDecimal getSgstAmount() {
            return sgstAmount;
        }

        public void setSgstAmount(BigDecimal sgstAmount) {
            this.sgstAmount = sgstAmount;
        }

        public BigDecimal getIgstAmount() {
            return igstAmount;
        }

        public void setIgstAmount(BigDecimal igstAmount) {
            this.igstAmount = igstAmount;
        }

        public BigDecimal getRoundOffAmount() {
            return roundOffAmount;
        }

        public void setRoundOffAmount(BigDecimal roundOffAmount) {
            this.roundOffAmount = roundOffAmount;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }
    }

    @Entity
    public static class InvoiceItem {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String description;
        private String hsnCode;
        private BigDecimal quantity;
        private BigDecimal rate;
        private BigDecimal amount;
        private BigDecimal gstRate;
        private BigDecimal cgstAmount;
        private BigDecimal sgstAmount;
        private BigDecimal igstAmount;
        private BigDecimal discountPercent = BigDecimal.ZERO; // optional line discount percent

        @ManyToOne
        @JoinColumn(name = "invoice_id", nullable = false)
        private Invoice invoice;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getHsnCode() { return hsnCode; }
        public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getRate() { return rate; }
        public void setRate(BigDecimal rate) { this.rate = rate; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public BigDecimal getGstRate() { return gstRate; }
        public void setGstRate(BigDecimal gstRate) { this.gstRate = gstRate; }
        public BigDecimal getCgstAmount() { return cgstAmount; }
        public void setCgstAmount(BigDecimal cgstAmount) { this.cgstAmount = cgstAmount; }
        public BigDecimal getSgstAmount() { return sgstAmount; }
        public void setSgstAmount(BigDecimal sgstAmount) { this.sgstAmount = sgstAmount; }
        public BigDecimal getIgstAmount() { return igstAmount; }
        public void setIgstAmount(BigDecimal igstAmount) { this.igstAmount = igstAmount; }
        public BigDecimal getDiscountPercent() {
            return discountPercent;
        }

        public void setDiscountPercent(BigDecimal discountPercent) {
            this.discountPercent = discountPercent;
        }

        public Invoice getInvoice() { return invoice; }
        public void setInvoice(Invoice invoice) { this.invoice = invoice; }
    }

    @Entity
    @Table(uniqueConstraints = @UniqueConstraint(columnNames = {"shop_id", "financialYear"}))
    public static class InvoiceSeq {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String prefix;
        private Long currentValue;
        private String financialYear;

        @ManyToOne
        @JoinColumn(name = "shop_id", nullable = false)
        private Shop shop;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public Long getCurrentValue() { return currentValue; }
        public void setCurrentValue(Long currentValue) { this.currentValue = currentValue; }
        public String getFinancialYear() { return financialYear; }
        public void setFinancialYear(String financialYear) { this.financialYear = financialYear; }
        public Shop getShop() { return shop; }
        public void setShop(Shop shop) { this.shop = shop; }
    }

    @Entity
    public static class ScanUsage {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne
        @JoinColumn(name = "shop_id", nullable = false)
        private Shop shop;

        private LocalDateTime scanDate;
        private String fileName;
        private String fileType;
        private Long fileSize;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Shop getShop() { return shop; }
        public void setShop(Shop shop) { this.shop = shop; }
        public LocalDateTime getScanDate() { return scanDate; }
        public void setScanDate(LocalDateTime scanDate) { this.scanDate = scanDate; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    }
}