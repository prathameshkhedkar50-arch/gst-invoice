# GST Invoice Processor: Project Plan

One shared Spring Boot app that serves multiple shops. Each shop gets its own config (name, GSTIN, fields, products), accesses the app with a **token (no login)**, and can **create GST bills** and **scan supplier invoices**. Only the **admin logs in** and generates tokens.

---

## 1. Goals

- Demo-ready in a few weeks, with a lean codebase.
- Onboarding a new shop = admin fills a form (or imports a JSON), generates a token, shares a link. No code change, no redeploy.
- Fields are dynamic: each shop can define extra fields on bills and products.
- Real GST logic: CGST/SGST vs IGST, HSN, rounding, validation.

## 2. Tech Stack

| Layer | Choice |
|---|---|
| Language / Framework | Java 21, Spring Boot 3.x (Web, Data JPA, Security, Validation) |
| Database | PostgreSQL (JSONB for dynamic fields) |
| UI | Static HTML + Bootstrap 5 + vanilla JS (served from Spring Boot, no separate frontend build) |
| Invoice scan | Vision LLM API (image/PDF in, JSON out). Optional fallback: Tess4J OCR |
| PDF output | OpenPDF (or Thymeleaf template to PDF) |
| Config | `application.properties` only (no YAML, no extra config files) |
| Build | Maven |

## 3. Multi-Tenancy Approach

**Shared database, shared schema, `shop_id` column on every tenant table.** Simplest to run and demo.

- Every request to `/api/**` resolves the shop from the token.
- A `ShopContext` (ThreadLocal) holds the current shop for the request.
- Every repository query filters by `shop_id`. Never trust a `shop_id` sent from the client.

## 4. Access Model (Token, No Login)

### Admin
- Single admin, credentials in `application.properties` (password stored as a BCrypt hash).
- Spring Security form login protects `/admin/**` and `/admin-api/**` only.
- Admin can: create/edit/disable shops, edit shop config, generate/revoke tokens, view usage.

### Shop users
- Token is sent as header `X-Shop-Token: <token>`.
- The shop opens a link like `https://yourapp/app.html#token=XXXX`. The page saves it in `localStorage` and attaches it to every call.
- Token format: `gst_<random 32 chars>` (generated with `SecureRandom`).
- **Store only the SHA-256 hash in DB.** Show the plain token once at generation time.
- Token fields: `shop_id`, `label`, `expires_at` (nullable), `revoked`, `last_used_at`.
- A shop can have multiple tokens (for example owner and staff) and each can be revoked separately.

### Request flow
```
Request -> TokenFilter (reads X-Shop-Token)
        -> hash it -> find active token -> load shop
        -> set ShopContext -> controller -> repository (filtered by shop_id)
        -> clear ShopContext
```
Invalid, revoked or expired token returns `401`.

## 5. Dynamic Fields (Shop Config)

Each shop has a `config` JSON (JSONB column) that the admin edits from a form:

```json
{
  "shopName": "Sharma General Store",
  "gstin": "27ABCDE1234F1Z5",
  "stateCode": "27",
  "address": "Shivajinagar, Pune",
  "phone": "9876543210",
  "invoicePrefix": "SGS",
  "financialYearStart": "04",
  "defaultGstRate": 18,
  "billFields": [
    { "key": "vehicleNo", "label": "Vehicle No", "type": "text", "required": false },
    { "key": "paymentMode", "label": "Payment Mode", "type": "select", "options": ["Cash", "UPI", "Card"] }
  ],
  "productFields": [
    { "key": "batchNo", "label": "Batch No", "type": "text" },
    { "key": "expiry", "label": "Expiry", "type": "date" }
  ],
  "scanFields": ["supplierName", "supplierGstin", "invoiceNo", "invoiceDate", "taxableAmount", "cgst", "sgst", "igst", "total"],
  "limits": { "scansPerDay": 50 }
}
```

- **Fixed core fields** (always present): invoice no, date, customer/supplier, GSTIN, items, tax, total.
- **Custom fields** are stored in a `custom` JSONB column on bills and products.
- The UI reads the config on load (`GET /api/config`) and renders extra inputs automatically.
- Supported field types in v1: `text`, `number`, `date`, `select`.
- The same config also builds the extraction prompt for scans, so the AI only extracts what that shop cares about.

## 6. Features

### A. Billing (sales invoices)
1. Product master per shop (name, HSN, unit, rate, GST %, custom fields).
2. Create bill: pick customer details, add line items, auto-calculate totals.
3. GST logic:
   - Shop state == customer state: **CGST + SGST** (half each).
   - Different state: **IGST**.
   - Per-item rounding rules plus a final round-off line.
4. Invoice number: `PREFIX/FY/0001`, sequence per shop per financial year (use a locked counter row to avoid duplicates).
5. Download bill as PDF with shop name, GSTIN, address, and the custom fields.

### B. Supplier invoice scan (purchase invoices)
1. Upload image or PDF.
2. Backend sends the file plus a prompt (built from the shop's `scanFields`) to the vision LLM. Response is forced to JSON.
3. **Validation layer** (this is what makes it more than a wrapper):
   - GSTIN format regex and checksum check
   - Taxable + taxes == total (tolerance of Rs. 1)
   - CGST == SGST, or IGST only
   - Date parsing and duplicate detection (same supplier GSTIN + invoice no)
4. Result is saved with status `NEEDS_REVIEW` or `OK`. Fields that failed validation are highlighted.
5. User edits and confirms in the UI, then the status becomes `CONFIRMED`.
6. Per-shop daily scan limit (from config) to control API cost.

### C. Reports
- Monthly GST summary: sales tax collected vs purchase tax paid (input credit view).
- Export CSV of sales and purchases for a date range.

### D. Admin panel
- Shops list, create/edit shop (config JSON editor with form helpers).
- Generate / revoke token, copy shop link.
- Usage counters: bills created, scans used, last active.

## 7. Database Schema (PostgreSQL)

```
shops          (id, code, name, config JSONB, active, created_at)
access_tokens  (id, shop_id, token_hash, label, expires_at, revoked, last_used_at, created_at)
products       (id, shop_id, name, hsn, unit, rate, gst_rate, custom JSONB, active)
invoices       (id, shop_id, type [SALE|PURCHASE], invoice_no, invoice_date,
                party_name, party_gstin, party_state_code,
                taxable_amount, cgst, sgst, igst, round_off, total,
                status [DRAFT|NEEDS_REVIEW|CONFIRMED], source [MANUAL|SCAN],
                custom JSONB, scan_raw JSONB, created_at)
invoice_items  (id, invoice_id, product_id NULL, description, hsn, qty, rate,
                gst_rate, taxable, cgst, sgst, igst, total)
invoice_seq    (shop_id, fy, last_no)       -- PK (shop_id, fy)
scan_usage     (shop_id, day, count)        -- PK (shop_id, day)
```

Indexes: `(shop_id, invoice_date)`, `(shop_id, party_gstin, invoice_no)`, `token_hash` (unique).

## 8. API Endpoints

**Admin (session login)**
| Method | Path | Purpose |
|---|---|---|
| POST | `/admin-api/shops` | Create shop with config |
| PUT | `/admin-api/shops/{id}` | Update shop and config |
| GET | `/admin-api/shops` | List shops |
| POST | `/admin-api/shops/{id}/tokens` | Generate token (returns plain token once) |
| DELETE | `/admin-api/tokens/{id}` | Revoke token |

**Shop (X-Shop-Token)**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/config` | Shop config for dynamic UI |
| GET/POST/PUT | `/api/products` | Product master |
| POST | `/api/bills` | Create sales bill |
| GET | `/api/bills`, `/api/bills/{id}` | List / view |
| GET | `/api/bills/{id}/pdf` | Download PDF |
| POST | `/api/scan` | Upload supplier invoice, get extracted JSON |
| PUT | `/api/purchases/{id}` | Correct and confirm scanned data |
| GET | `/api/reports/gst-summary?month=YYYY-MM` | Monthly summary |
| GET | `/api/reports/export?from=&to=&type=` | CSV export |

## 9. Lean Folder Structure

Single package, few classes, grouped by layer rather than deep nesting.

```
gst-invoice/
├── pom.xml
├── plan.md
└── src/main/
    ├── java/com/yourname/gst/
    │   ├── GstApplication.java
    │   ├── Models.java            // JPA entities (Shop, Token, Product, Invoice, Item)
    │   ├── Repos.java             // all Spring Data repositories
    │   ├── Security.java          // admin SecurityConfig + TokenFilter + ShopContext
    │   ├── AdminController.java   // shops + tokens
    │   ├── ShopController.java    // config, products, bills, reports
    │   ├── ScanController.java    // upload + extraction endpoint
    │   ├── GstService.java        // tax calc, numbering, validation
    │   ├── ScanService.java       // LLM call, prompt builder, validators
    │   └── PdfService.java        // bill PDF
    └── resources/
        ├── application.properties
        └── static/
            ├── admin.html         // admin panel (Bootstrap)
            ├── app.html           // shop app (Bootstrap)
            └── app.js             // shared JS: token handling, dynamic forms
```

If it grows, split `Models.java` and `Repos.java` later. Start small.

## 10. application.properties (everything configurable here)

```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/gstdb
spring.datasource.username=postgres
spring.datasource.password=changeme
spring.jpa.hibernate.ddl-auto=update

# Admin (BCrypt hash, never plain text)
app.admin.username=admin
app.admin.password-hash={bcrypt}$2a$10$....

# Tokens
app.token.prefix=gst_
app.token.default-expiry-days=0   # 0 = never expires

# Scan / AI
app.ai.api-key=${AI_API_KEY}
app.ai.model=your-vision-model
app.ai.max-file-mb=5
app.scan.default-daily-limit=50

# Uploads and PDF
spring.servlet.multipart.max-file-size=5MB
app.upload.dir=./uploads
```

Secrets (DB password, API key) come from environment variables in production.

## 11. Build Phases

| Phase | Work | Time |
|---|---|---|
| 1 | Project setup, PostgreSQL, entities, admin login, shop CRUD, token generation + TokenFilter | 3-4 days |
| 2 | Shop config, dynamic form rendering, product master | 3-4 days |
| 3 | Bill creation, GST calc (CGST/SGST/IGST), invoice numbering, PDF | 4-5 days |
| 4 | Scan: upload, prompt builder, LLM call, validators, review screen | 4-5 days |
| 5 | Reports, CSV export, scan limits, usage counters | 2-3 days |
| 6 | Tests, Dockerfile, deploy, demo data for 2-3 sample shops | 2-3 days |

Roughly 3 to 4 weeks part-time.

## 12. Security Checklist

- Tokens hashed in DB, shown once, revocable, optional expiry.
- All queries scoped by `shop_id` from `ShopContext`, with a test that proves shop A cannot read shop B's data.
- Upload checks: file type, size limit, random stored file names.
- Rate limit by token (daily scan cap, basic request throttle).
- Admin endpoints separate from shop endpoints and not reachable with a shop token.
- HTTPS in production; do not log tokens or API keys.

## 13. Testing

- Unit: GST calculations (intra/inter-state, rounding), GSTIN validator, invoice sequence.
- Integration: token filter (valid, revoked, expired), tenant isolation, bill creation.
- Scan: run 10 to 15 sample invoices, record field-level accuracy, and keep the number for your resume.

## 14. Demo Plan

1. Admin logs in, creates two shops with different config (a kirana store, and a pharmacy with batch/expiry fields).
2. Generate tokens and open each shop link: the same app shows different fields.
3. Create a bill in each shop and download the PDF.
4. Upload a supplier invoice, show extracted data, a validation warning, then confirm.
5. Show the monthly GST summary.
6. Revoke a token live and show the access is cut off.

## 15. Resume Bullets (fill in your real numbers)

- Built a multi-tenant GST billing and invoice-processing platform with Spring Boot and PostgreSQL, onboarding new shops through configuration only, with no code changes.
- Designed a token-based access model (SHA-256 hashed, revocable, per-shop scoped) with tenant isolation enforced at the repository layer.
- Implemented AI-based supplier invoice extraction with a validation layer (GSTIN checksum, tax reconciliation, duplicate detection), reaching X% field-level accuracy on N sample invoices.
- Modeled dynamic per-shop fields using PostgreSQL JSONB and a config-driven UI.

## 16. Later Ideas (not in v1)

- Offline-friendly PWA for shop counters
- WhatsApp bill sharing
- GSTR-1 ready export format
- Per-shop branding (logo, colors)
- Audit log of edits
- Move from `ddl-auto=update` to Flyway migrations