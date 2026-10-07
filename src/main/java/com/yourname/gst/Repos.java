package com.yourname.gst;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data Repository interfaces for all entities.
 * Every query on tenant data filters by shop_id for multi-tenancy.
 */
public class Repos {

    public interface ShopRepository extends JpaRepository<Models.Shop, Long> {
        Optional<Models.Shop> findByGstin(String gstin);
        List<Models.Shop> findByActive(boolean active);
    }

    public interface AccessTokenRepository extends JpaRepository<Models.AccessToken, Long> {
        Optional<Models.AccessToken> findByTokenHash(String tokenHash);
        List<Models.AccessToken> findByShop(Models.Shop shop);
        List<Models.AccessToken> findByShopAndRevokedFalse(Models.Shop shop);
        List<Models.AccessToken> findByShopAndExpiresAfter(Models.Shop shop, java.time.LocalDateTime expiresAfter);
    }

    public interface ProductRepository extends JpaRepository<Models.Product, Long> {
        List<Models.Product> findByShop(Models.Shop shop);
        List<Models.Product> findByShopAndActiveTrue(Models.Shop shop);
        Optional<Models.Product> findByShopAndHsnCode(Models.Shop shop, String hsnCode);
    }

    public interface InvoiceRepository extends JpaRepository<Models.Invoice, Long> {
        List<Models.Invoice> findByShop(Models.Shop shop);
        List<Models.Invoice> findByShopAndInvoiceNumber(Models.Shop shop, String invoiceNumber);
        List<Models.Invoice> findByShopAndInvoiceDateBetween(Models.Shop shop, java.time.LocalDateTime start, java.time.LocalDateTime end);
        List<Models.Invoice> findByShopAndInvoiceDateAfter(Models.Shop shop, java.time.LocalDateTime startDate);
        List<Models.Invoice> findByShopAndInvoiceDateBefore(Models.Shop shop, java.time.LocalDateTime endDate);
    }

    public interface InvoiceItemRepository extends JpaRepository<Models.InvoiceItem, Long> {
        List<Models.InvoiceItem> findByInvoice(Models.Invoice invoice);
    }

    public interface InvoiceSeqRepository extends JpaRepository<Models.InvoiceSeq, Long> {
        Optional<Models.InvoiceSeq> findByShop(Models.Shop shop);
        @Query("SELECT i FROM InvoiceSeq i WHERE i.shop.id = :shopId")
        Optional<Models.InvoiceSeq> findByShopId(@Param("shopId") Long shopId);
        @Query("SELECT i FROM InvoiceSeq i WHERE i.shop = :shop")
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<Models.InvoiceSeq> findByShopForUpdate(@Param("shop") Models.Shop shop);
        Optional<Models.InvoiceSeq> findByShopAndFinancialYear(Models.Shop shop, String financialYear);
        @Query("SELECT i FROM InvoiceSeq i WHERE i.shop = :shop AND i.financialYear = :fy")
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<Models.InvoiceSeq> findByShopAndFinancialYearForUpdate(@Param("shop") Models.Shop shop, @Param("fy") String financialYear);
    }

    public interface ScanUsageRepository extends JpaRepository<Models.ScanUsage, Long> {
        List<Models.ScanUsage> findByShop(Models.Shop shop);
        List<Models.ScanUsage> findByShopAndScanDateBetween(Models.Shop shop, java.time.LocalDateTime start, java.time.LocalDateTime end);
    }
}