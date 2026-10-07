package com.yourname.gst;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service for PDF invoice generation.
 * Implements methods from section 8 of plan.md.
 */
@Service
public class PdfService {

    @Autowired
    private Repos.InvoiceRepository invoiceRepository;

    @Autowired
    private Repos.InvoiceItemRepository invoiceItemRepository;

    /**
     * Generate PDF invoice from invoice data.
     * @param invoice The invoice to generate PDF for
     * @param shop The shop associated with the invoice
     * @return PDF content as byte array
     */
    public byte[] generateInvoicePdf(Models.Invoice invoice, Models.Shop shop) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            // Set up fonts
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

            // Add shop details
            document.add(new Paragraph(shop.getName() == null ? "" : shop.getName(), titleFont));
            document.add(new Paragraph(getShopAddress(shop), normalFont));
            String shopGstin = "";
            if (shop.getConfig() != null && shop.getConfig() instanceof Map) {
                Map<String, Object> config = (Map<String, Object>) shop.getConfig();
                Object gstinObj = config.get("gstin");
                if (gstinObj != null) {
                    shopGstin = gstinObj.toString();
                }
            }
            document.add(new Paragraph("GSTIN: " + shopGstin, normalFont));
            document.add(new Paragraph(" ")); // Empty line

            // Add invoice details
            document.add(new Paragraph("Invoice Number: " + invoice.getInvoiceNumber(), headingFont));
            document.add(new Paragraph("Invoice Date: " + formatDate(invoice.getInvoiceDate()), normalFont));
            document.add(new Paragraph("Due Date: " + formatDate(invoice.getDueDate()), normalFont));
            document.add(new Paragraph(" ")); // Empty line

            // Add customer details
            document.add(new Paragraph("Bill To:", headingFont));
            document.add(new Paragraph(invoice.getCustomerName() == null ? "" : invoice.getCustomerName(), normalFont));
            document.add(new Paragraph(invoice.getCustomerAddress() == null ? "" : invoice.getCustomerAddress(), normalFont));
            document.add(new Paragraph("GSTIN: " + (invoice.getCustomerGstin() == null ? "" : invoice.getCustomerGstin()), normalFont));
            document.add(new Paragraph("State Code: " + (invoice.getCustomerStateCode() == null ? "" : invoice.getCustomerStateCode()), normalFont));

            // Add custom fields block
            addCustomFieldsBlock(document, invoice, normalFont);

            document.add(new Paragraph(" ")); // Empty line

            // Add items table
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);
            table.setSpacingAfter(10f);

            // Define column widths
            float[] columnWidths = {1f, 3f, 1f, 1f, 1f, 1f, 1f, 1f};
            table.setWidths(columnWidths);

            // Add table header
            addTableHeader(table);

            // Add items
            List<Models.InvoiceItem> items = invoiceItemRepository.findByInvoice(invoice);
            if (items != null) {
                int index = 1;
                for (Models.InvoiceItem item : items) {
                    addItemRow(table, item, normalFont, index);
                    index++;
                }
            }

            document.add(table);

            // Add totals
            document.add(new Paragraph(" "));
            addTotals(document, invoice, normalFont);

            // Add grand total in words
            addGrandTotalInWords(document, invoice, normalFont);

            document.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    /**
     * Get PDF invoice as byte array by invoice ID.
     * @param invoiceId The ID of the invoice
     * @param shop The shop requesting the PDF
     * @return PDF content as byte array
     */
    public byte[] getInvoicePdfById(Long invoiceId, Models.Shop shop) {
        Models.Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(inv -> inv.getShop().getId().equals(shop.getId()))
                .orElse(null);
        if (invoice == null) {
            return new byte[0];
        }
        return generateInvoicePdf(invoice, shop);
    }

    // Helper methods

    private String getShopAddress(Models.Shop shop) {
        if (shop.getConfig() == null) {
            return "";
        }
        Map<String, Object> config = (Map<String, Object>) shop.getConfig();
        StringBuilder address = new StringBuilder();
        Object addressObj = config.get("address");
        if (addressObj != null) {
            address.append(addressObj.toString());
        }
        Object cityObj = config.get("city");
        if (cityObj != null) {
            address.append(", ").append(cityObj.toString());
        }
        Object stateObj = config.get("state");
        if (stateObj != null) {
            address.append(", ").append(stateObj.toString());
        }
        Object pincodeObj = config.get("pincode");
        if (pincodeObj != null) {
            address.append(" - ").append(pincodeObj.toString());
        }
        return address.toString();
    }

    private String formatDate(LocalDateTime date) {
        if (date == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        return date.format(formatter);
    }

    private void addTableHeader(PdfPTable table) {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        table.addCell(new PdfPCell(new Phrase("S.No", headerFont)));
        table.addCell(new PdfPCell(new Phrase("Description", headerFont)));
        table.addCell(new PdfPCell(new Phrase("HSN", headerFont)));
        table.addCell(new PdfPCell(new Phrase("Qty", headerFont)));
        table.addCell(new PdfPCell(new Phrase("Rate", headerFont)));
        table.addCell(new PdfPCell(new Phrase("Discount %", headerFont)));
        table.addCell(new PdfPCell(new Phrase("Tax Amount", headerFont)));
        table.addCell(new PdfPCell(new Phrase("Amount", headerFont)));
    }

    private void addItemRow(PdfPTable table, Models.InvoiceItem item, Font font, int index) {
        table.addCell(new PdfPCell(new Phrase(String.valueOf(index), font)));
        table.addCell(new PdfPCell(new Phrase(item.getDescription() == null ? "" : item.getDescription(), font)));
        table.addCell(new PdfPCell(new Phrase(item.getHsnCode() == null ? "" : item.getHsnCode(), font)));
        table.addCell(new PdfPCell(new Phrase(item.getQuantity() == null ? "" : item.getQuantity().toString(), font)));
        table.addCell(new PdfPCell(new Phrase(item.getRate() == null ? "" : item.getRate().toString(), font)));
        table.addCell(new PdfPCell(new Phrase(item.getDiscountPercent() == null ? "" : item.getDiscountPercent().toString(), font)));
        BigDecimal taxAmount = item.getCgstAmount().add(item.getSgstAmount()).add(item.getIgstAmount());
        table.addCell(new PdfPCell(new Phrase(taxAmount == null ? "" : taxAmount.toString(), font)));
        table.addCell(new PdfPCell(new Phrase(item.getAmount() == null ? "" : item.getAmount().toString(), font)));
    }

    private void addTotals(Document document, Models.Invoice invoice, Font font) throws DocumentException {
        // We'll add a table for totals aligned to the right
        PdfPTable totalsTable = new PdfPTable(2);
        totalsTable.setWidthPercentage(30);
        totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalsTable.setSpacingBefore(10f);

        totalsTable.addCell(new PdfPCell(new Phrase("Taxable Amount:", font)));
        totalsTable.addCell(new PdfPCell(new Phrase(invoice.getTaxableAmount() == null ? "" : invoice.getTaxableAmount().toString(), font)));
        totalsTable.addCell(new PdfPCell(new Phrase("CGST:", font)));
        totalsCell(totalsTable, invoice.getCgstAmount(), font);
        totalsTable.addCell(new PdfPCell(new Phrase("SGST:", font)));
        totalsCell(totalsTable, invoice.getSgstAmount(), font);
        totalsTable.addCell(new PdfPCell(new Phrase("IGST:", font)));
        totalsCell(totalsTable, invoice.getIgstAmount(), font);
        totalsTable.addCell(new PdfPCell(new Phrase("Round Off:", font)));
        totalsCell(totalsTable, invoice.getRoundOffAmount(), font);
        totalsTable.addCell(new PdfPCell(new Phrase("Total Amount:", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10))));
        totalsCell(totalsTable, invoice.getTotalAmount(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));

        document.add(totalsTable);
    }

    private void totalsCell(PdfPTable table, BigDecimal amount, Font font) {
        table.addCell(new PdfPCell(new Phrase(amount == null ? "" : amount.toString(), font)));
    }

    /**
     * Add custom fields block to the PDF.
     * @param document The PDF document
     * @param invoice The invoice containing custom fields
     * @param font The font to use for text
     */
    private void addCustomFieldsBlock(Document document, Models.Invoice invoice, Font font) {
        Object customFieldsObj = invoice.getCustomFields();
        if (customFieldsObj != null && customFieldsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> customFields = (Map<String, Object>) customFieldsObj;
            if (!customFields.isEmpty()) {
                document.add(new Paragraph("Custom Fields:", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
                for (Map.Entry<String, Object> entry : customFields.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    String valueStr = value != null ? value.toString() : "";
                    document.add(new Paragraph(key + ": " + valueStr, font));
                }
                document.add(new Paragraph(" ")); // Empty line after custom fields
            }
        }
    }

    /**
     * Add grand total in words (Indian format) to the PDF.
     * @param document The PDF document
     * @param invoice The invoice containing the total amount
     * @param font The font to use for text
     */
    private void addGrandTotalInWords(Document document, Models.Invoice invoice, Font font) {
        BigDecimal totalAmount = invoice.getTotalAmount();
        if (totalAmount != null) {
            String totalInWords = convertToIndianWords(totalAmount);
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Amount in Words: " + totalInWords + " Only", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
            document.add(new Paragraph(" ")); // Empty line
        }
    }

    /**
     * Convert a number to Indian words format (lakhs, crores).
     * @param number The number to convert
     * @return The number in words using Indian numbering system
     */
    private String convertToIndianWords(BigDecimal number) {
        // Extract the decimal part (paise)
        long amount = number.longValue();
        int decimalPart = number.multiply(new BigDecimal(100)).remainder(new BigDecimal(100)).intValue();

        if (amount == 0) {
            return "Zero";
        }

        String words = "";

        // Handle crores
        if (amount >= 10000000) {
            long crores = amount / 10000000;
            words += convertThreeDigitNumber((int) crores) + " Crore ";
            amount %= 10000000;
        }

        // Handle lakhs
        if (amount >= 100000) {
            long lakhs = amount / 100000;
            words += convertThreeDigitNumber((int) lakhs) + " Lakh ";
            amount %= 100000;
        }

        // Handle thousands
        if (amount >= 1000) {
            long thousands = amount / 1000;
            words += convertThreeDigitNumber((int) thousands) + " Thousand ";
            amount %= 1000;
        }

        // Handle hundreds
        if (amount >= 100) {
            long hundreds = amount / 100;
            words += convertThreeDigitNumber((int) hundreds) + " Hundred ";
            amount %= 100;
        }

        // Handle tens and units
        if (amount > 0) {
            if (!words.isEmpty()) {
                words += "and ";
            }
            words += convertThreeDigitNumber((int) amount);
        }

        // Add paise if any
        if (decimalPart > 0) {
            if (!words.isEmpty()) {
                words += " and ";
            }
            words += convertThreeDigitNumber(decimalPart) + " Paise";
        }

        return words.isEmpty() ? "Zero" : words;
    }

    /**
     * Convert a three-digit number to words.
     * @param number The three-digit number (0-999)
     * @return The number in words
     */
    private String convertThreeDigitNumber(int number) {
        String[] units = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
                         "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

        if (number == 0) {
            return "";
        } else if (number < 20) {
            return units[number];
        } else if (number < 100) {
            return tens[number / 10] + (number % 10 != 0 ? " " + units[number % 10] : "");
        } else {
            return units[number / 100] + " Hundred" + (number % 100 != 0 ? " and " + convertThreeDigitNumber(number % 100) : "");
        }
    }
}