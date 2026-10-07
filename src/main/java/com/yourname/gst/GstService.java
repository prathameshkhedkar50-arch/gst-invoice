package com.yourname.gst;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for GST processing logic.
 * Implements methods from section 8 of plan.md.
 */
@Service
public class GstService {

    @Autowired
    private Repos.InvoiceRepository invoiceRepository;

    @Autowired
    private Repos.InvoiceItemRepository invoiceItemRepository;

    @Autowired
    private Repos.InvoiceSeqRepository invoiceSeqRepository;

    @Autowired
    private Repos.ProductRepository productRepository;

    /**
     * Calculate GST amounts for an invoice item.
     * @param item The invoice item to calculate GST for
     */
    public void calculateGst(Models.InvoiceItem item) {
        // 1. Calculate base amount = quantity * rate
        BigDecimal baseAmount = item.getQuantity().multiply(item.getRate());
        // 2. Calculate discount amount
        BigDecimal discountAmount = baseAmount.multiply(item.getDiscountPercent().divide(new BigDecimal("100")));
        // 3. Taxable amount = base amount - discount amount
        BigDecimal taxableAmount = baseAmount.subtract(discountAmount);
        item.setAmount(taxableAmount);

        // 4. Determine if intra-state or inter-state
        // We need the shop stateCode and customer stateCode.
        // However, the item does not have direct access to shop or invoice.
        // We will assume that the item's gstRate is set appropriately and that the tax type (intra/inter)
        // is determined at the invoice level. For simplicity, we'll assume intra-state for now.
        // TODO: Pass shop and invoice state codes to this method or determine from item's invoice.
        // For now, we'll compute both CGST and SGST (intra-state) as a placeholder.
        // In a real scenario, we would need to know the place of supply.

        // For the purpose of this task, we'll assume intra-state transaction (CGST + SGST)
        BigDecimal gstRateDecimal = item.getGstRate().divide(new BigDecimal("100")); // Convert percentage to decimal
        BigDecimal cgstSgstAmount = taxableAmount.multiply(gstRateDecimal).divide(new BigDecimal("2"));

        item.setCgstAmount(cgstSgstAmount);
        item.setSgstAmount(cgstSgstAmount);
        item.setIgstAmount(BigDecimal.ZERO);
    }

    /**
     * Generate next invoice number for a shop.
     * @param shop The shop to generate invoice number for
     * @return Generated invoice number
     */
    @Transactional
    public String generateInvoiceNumber(Models.Shop shop) {
        // 1. Get invoice prefix from shop config, default to "INV"
        String prefix = "INV";
        if (shop.getConfig() != null) {
            Map<String, Object> config = (Map<String, Object>) shop.getConfig();
            Object prefixObj = config.get("invoicePrefix");
            if (prefixObj != null) {
                prefix = prefixObj.toString();
            }
        }

        // 2. Determine financial year (FY) based on shop's financialYearStart (default April)
        String fyStartMonthStr = "04"; // default to April
        if (shop.getConfig() != null) {
            Map<String, Object> config = (Map<String, Object>) shop.getConfig();
            Object fyStartObj = config.get("financialYearStart");
            if (fyStartObj != null) {
                fyStartMonthStr = fyStartObj.toString();
            }
        }
        int fyStartMonth = Integer.parseInt(fyStartMonthStr);
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        String fy;
        if (month >= fyStartMonth) {
            // Financial year has started
            fy = year + "-" + String.valueOf(year + 1).substring(2);
        } else {
            // Financial year is from previous year
            fy = (year - 1) + "-" + String.valueOf(year).substring(2);
        }

        // 3. Find or create InvoiceSeq for shop and financial year with pessimistic lock
        Models.InvoiceSeq invoiceSeq;
        boolean created = false;
        while (true) {
            try {
                Optional<Models.InvoiceSeq> optSeq = invoiceSeqRepository.findByShopAndFinancialYearForUpdate(shop, fy);
                if (optSeq.isPresent()) {
                    invoiceSeq = optSeq.get();
                } else {
                    invoiceSeq = new Models.InvoiceSeq();
                    invoiceSeq.setShop(shop);
                    invoiceSeq.setPrefix(prefix);
                    invoiceSeq.setFinancialYear(fy);
                    invoiceSeq.setCurrentValue(0L); // start from 0, then we'll increment to 1
                    invoiceSeqRepository.save(invoiceSeq);
                    created = true;
                    // After saving, we need to lock the newly inserted row.
                    // We'll fetch it again with lock.
                    optSeq = invoiceSeqRepository.findByShopAndFinancialYearForUpdate(shop, fy);
                    if (optSeq.isPresent()) {
                        invoiceSeq = optSeq.get();
                    } else {
                        // Should not happen
                        throw new RuntimeException("Failed to lock invoice sequence after creation");
                    }
                }
                // If we successfully retrieved (or created and locked) the sequence, break
                break;
            } catch (DataIntegrityViolationException e) {
                // Duplicate key, another thread created the row first.
                // Retry to fetch and lock the existing row.
                Optional<Models.InvoiceSeq> optSeq = invoiceSeqRepository.findByShopAndFinancialYearForUpdate(shop, fy);
                if (optSeq.isPresent()) {
                    invoiceSeq = optSeq.get();
                    break;
                }
                // If still not present, loop again (should not happen)
            }
        }

        // 4. Increment current value
        Long currentValue = invoiceSeq.getCurrentValue() + 1;
        invoiceSeq.setCurrentValue(currentValue);

        // 5. Save the sequence
        invoiceSeqRepository.save(invoiceSeq);

        // 6. Format invoice number: PREFIX/FY/XXXXXX (padded to 4 digits as per example)
        String paddedValue = String.format("%04d", currentValue);
        return prefix + "/" + fy + "/" + paddedValue;
    }

    /**
     * Create an invoice from scanned data.
     * @param shop The shop creating the invoice
     * @param scannedData Data extracted from scanned invoice
     * @return Created invoice
     */
    @Transactional
    public Models.Invoice createInvoiceFromScan(Models.Shop shop, java.util.Map<String, Object> scannedData) {
        // 1. Create Invoice entity
        Models.Invoice invoice = new Models.Invoice();
        invoice.setShop(shop);

        // 2. Set invoice number, dates
        String invoiceNumber = generateInvoiceNumber(shop);
        invoice.setInvoiceNumber(invoiceNumber);
        LocalDateTime now = LocalDateTime.now();
        invoice.setInvoiceDate(now);
        // Set due date to 30 days from invoice date (or configurable)
        invoice.setDueDate(now.plusDays(30));

        // 3. Set customer details from scannedData
        invoice.setCustomerName((String) scannedData.get("customerName"));
        invoice.setCustomerGstin((String) scannedData.get("customerGstin"));
        invoice.setCustomerAddress((String) scannedData.get("customerAddress"));
        // Set customer state code (required for GST calculation)
        String customerStateCode = (String) scannedData.get("customerStateCode");
        invoice.setCustomerStateCode(customerStateCode);

        // Set custom fields from scannedData
        Object customObj = scannedData.get("custom");
        if (customObj != null) {
            invoice.setCustomFields(customObj);
        }

        // 4. Save invoice to get ID
        invoice = invoiceRepository.save(invoice);

        // 5. Create InvoiceItem entities from line items in scannedData
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> lineItems = (List<java.util.Map<String, Object>>) scannedData.get("lineItems");
        if (lineItems != null) {
            for (java.util.Map<String, Object> itemData : lineItems) {
                Models.InvoiceItem item = new Models.InvoiceItem();
                item.setDescription((String) itemData.get("description"));
                item.setHsnCode((String) itemData.get("hsnCode"));
                item.setQuantity(new BigDecimal(itemData.get("quantity").toString()));
                item.setRate(new BigDecimal(itemData.get("rate").toString()));
                // Set discount percent (default 0)
                Object discountObj = itemData.get("discountPercent");
                item.setDiscountPercent(discountObj != null ? new BigDecimal(discountObj.toString()) : BigDecimal.ZERO);
                // Set gstRate from product (optional, could be overridden)
                // We'll try to find the product by HSN code or description, but for now, we'll use a default from shop config
                Object gstRateObj = itemData.get("gstRate");
                if (gstRateObj != null) {
                    item.setGstRate(new BigDecimal(gstRateObj.toString()));
                } else {
                    // Default gst rate from shop config
                    Map<String, Object> config = (Map<String, Object>) shop.getConfig();
                    Object defaultGstRateObj = config.get("defaultGstRate");
                    item.setGstRate(defaultGstRateObj != null ? new BigDecimal(defaultGstRateObj.toString()) : new BigDecimal("18"));
                }
                item.setInvoice(invoice);

                // 6. Calculate GST for each item
                calculateGst(item);

                // 7. Save invoice item
                invoiceItemRepository.save(item);
            }
        }

        // 8. Recalculate invoice totals
        List<Models.InvoiceItem> items = invoiceItemRepository.findByInvoice(invoice);
        BigDecimal taxableAmount = BigDecimal.ZERO;
        BigDecimal cgstAmount = BigDecimal.ZERO;
        BigDecimal sgstAmount = BigDecimal.ZERO;
        BigDecimal igstAmount = BigDecimal.ZERO;

        for (Models.InvoiceItem item : items) {
            taxableAmount = taxableAmount.add(item.getAmount());
            cgstAmount = cgstAmount.add(item.getCgstAmount());
            sgstAmount = sgstAmount.add(item.getSgstAmount());
            igstAmount = igstAmount.add(item.getIgstAmount());
        }

        invoice.setTaxableAmount(taxableAmount);
        invoice.setCgstAmount(cgstAmount);
        invoice.setSgstAmount(sgstAmount);
        invoice.setIgstAmount(igstAmount);

        // 9. Calculate round off amount (round to nearest rupee)
        BigDecimal unroundedTotal = taxableAmount.add(cgstAmount).add(sgstAmount).add(igstAmount);
        BigDecimal roundedTotal = unroundedTotal.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundOffAmount = roundedTotal.subtract(unroundedTotal);
        invoice.setRoundOffAmount(roundOffAmount);

        // 10. Total amount
        invoice.setTotalAmount(roundedTotal);

        // 11. Save updated invoice
        invoiceRepository.save(invoice);

        return invoice;
    }

    /**
     * Get GST summary for a shop.
     * @param shop The shop to get summary for
     * @param startDate Start date for summary
     * @param endDate End date for summary
     * @return GST summary data
     */
    public java.util.Map<String, Object> getGstSummary(Models.Shop shop, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
        // 1. Query invoices for shop in date range
        List<Models.Invoice> invoices = invoiceRepository.findByShopAndInvoiceDateBetween(shop, startDate, endDate);

        // 2. Calculate totals
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;
        BigDecimal totalIgst = BigDecimal.ZERO;

        for (Models.Invoice invoice : invoices) {
            totalSales = totalSales.add(invoice.getTaxableAmount());
            totalCgst = totalCgst.add(invoice.getCgstAmount());
            totalSgst = totalSgst.add(invoice.getSgstAmount());
            totalIgst = totalIgst.add(invoice.getIgstAmount());
        }

        // 3. Return summary map
        return java.util.Map.of(
            "totalSales", totalSales,
            "totalCgst", totalCgst,
            "totalSgst", totalSgst,
            "totalIgst", totalIgst
        );
    }
}