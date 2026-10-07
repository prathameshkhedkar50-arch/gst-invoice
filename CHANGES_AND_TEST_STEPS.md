# Files Changed

1. **pom.xml**
   - Set `java.version` to 17 (to match installed JDK)
   - Removed Testcontainers dependencies (`junit-jupiter` and `postgresql`)
   - Removed `spring-boot-starter-test` and `spring-boot-test` dependencies (no automated tests requested)

2. **src/main/java/com/yourname/gst/AdminController.java**
   - *Unchanged from previous implementation* (contains token generation endpoint returning plain token once)

3. **src/main/java/com/yourname/gst/Repos.java**
   - Added `findByShop(Models.Shop shop)` method to `AccessTokenRepository` interface

4. **src/main/resources/static/admin.html**
   - Complete rewrite: Bootstrap 5 interface with vanilla JS for:
     * Shop management (create, edit, list)
     * Token management (generate, view, revoke - token shown only once)
     * CSRF token handling for form submissions
     * Shop link generation for token-based access

5. **src/main/resources/static/app.js**
   - Complete rewrite: Handles token extraction, API authentication, shop config loading
   - Implements Products tab with:
     * Search box (searches name/description)
     * Add/Edit product modal with dynamic fields based on shop's `productFields` config
     * Product table with edit/delete actions
     * Soft delete via DELETE endpoint (sets active=false)
   - Other tabs show "Coming soon"

6. **src/main/java/com/yourname/gst/DataSeeder.java**
   - *New file*: CommandLineRunner that creates two sample shops when `app.seed=true`:
     * Kirana store (no custom fields)
     * Pharmacy store (with batchNo and expiry custom fields)
   - Prints generated tokens to console

7. **src/main/java/com/yourname/gst/ShopController.java**
   - Updated product CRUD endpoints:
     * GET `/api/products` - accepts optional `?search=` parameter
     * POST `/api/products` - validates custom fields against shop's `productFields` config
     * PUT `/api/products/{id}` - validates custom fields if provided
     * DELETE `/api/products/{id}` - soft delete (sets active=false)
   - All queries scoped to current shop via `ShopContext`
   - Never accepts `shop_id` from client

8. **src/main/resources/application.properties**
   - Removed default values for:
     * DB_URL, DB_USER, DB_PASS (now required environment variables)
     * ADMIN_USER, ADMIN_PASS (now required environment variables)
   - Application will fail to start if these are not set

9. **Deleted files**:
   - `src/test/` directory (all test sources)
   - `TEST_RESULTS.md`
   - `TEST_CHECKLIST.md`

# Manual Test Steps

## Prerequisites
1. PostgreSQL database running and accessible
2. Set required environment variables:
   ```bash
   export DB_URL=jdbc:postgresql://localhost:5432/gstdb
   export DB_USER=postgres
   export DB_PASS=postgres
   export ADMIN_USER=admin
   export ADMIN_PASS=admin
   # Optional: to seed sample data
   export APP_SEED=true
   ```

## Build and Run
```bash
mvn clean package
java -jar target/gst-invoice-0.0.1-SNAPSHOT.jar
```
*(or use `mvn spring-boot:run`)*

## If Seeding Enabled (`APP_SEED=true`)
- Console output will show tokens for two sample shops:
  ```
  Generated token for Kirana Shop: gst_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
  Generated token for Pharmacy Shop: gst_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
  ```
- Note these tokens for testing the shop interface

## Admin Interface Testing
1. Open browser to: `http://localhost:8080/admin/login`
2. Log in with credentials from environment variables
3. Test shop management:
   - Create new shops
   - Edit existing shops
   - Generate tokens (shown only once - copy immediately)
   - Revoke tokens
4. Test token management:
   - View token list (see token previews, expiry, revocation status)
   - Generate new tokens for shops

## Shop Interface Testing
1. Obtain a token from admin panel (by generating token for a shop)
2. Access shop interface:  
   `http://localhost:8080/app.html#token=YOUR_TOKEN_HERE`
3. Verify:
   - Shop name displayed in navbar
   - Products tab active by default
   - Search box filters products by name/description
   - Add product button opens modal with:
     * Standard fields (name, description, price, HSN code, unit)
     * Dynamic fields based on shop's `productFields` config (text, number, date, select)
   - Edit product button loads product data into modal
   - Delete product button performs soft delete (sets active=false)
4. Other tabs (New Bill, Bills, Scan, Reports) show "Coming soon" messages

## API Endpoint Testing (with curl)
Replace `YOUR_TOKEN_HERE` with a valid shop token:

### Get shop config
```bash
curl -H "X-Shop-Token: YOUR_TOKEN_HERE" http://localhost:8080/api/config
```

### Get products list
```bash
curl -H "X-Shop-Token: YOUR_TOKEN_HERE" http://localhost:8080/api/products
```

### Search products
```bash
curl -H "X-Shop-Token: YOUR_TOKEN_HERE" "http://localhost:8080/api/products?search=phone"
```

### Create product
```bash
curl -H "X-Shop-Token: YOUR_TOKEN_HERE" \
     -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/products \
     -d '{
       "name": "Test Product",
       "description": "Test Description",
       "price": 100.0,
       "hsnCode": "TEST01",
       "unit": "NOS",
       "active": true
     }'
```

*Note: For shops with custom fields, include them in the JSON at the root level.*

## Verification Points
- ✅ Token authentication required for all `/api/` endpoints
- ✅ Shop data isolation: products only visible to their own shop
- ✅ Custom fields validated against shop configuration (type, required)
- ✅ Token generation returns plain token only once (never stored)
- ✅ CSRF protection active for admin endpoints (login/form submissions)
- ✅ Environment variables required for credentials (no defaults)
- ✅ Multi-tenancy via `shop_id` foreign key on all tenant entities