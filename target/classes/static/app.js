/**
 * App.js for GST Invoice Processor
 * Handles token extraction from URL, API calls with authentication, and basic UI
 */

let currentProductFields = []; // To store product fields configuration for dynamic rendering
let currentBillFields = []; // To store bill fields configuration for dynamic rendering
let currentShopId = null; // To store the current shop ID (if needed)
let currentShopStateCode = "27"; // Default shop state code, will be updated from config
let lineItemCount = 0; // To track line items in new bill form

document.addEventListener('DOMContentLoaded', function() {
    // Extract token from URL hash
    const token = extractTokenFromHash();

    if (token) {
        // Save token to localStorage
        localStorage.setItem('gst_token', token);

        // Initialize app with token
        initializeApp(token);
    } else {
        // Check if token exists in localStorage
        const storedToken = localStorage.getItem('gst_token');
        if (storedToken) {
            initializeApp(storedToken);
        } else {
            showTokenInstructions();
        }
    }
});

/**
 * Extract token from URL hash
 * @returns {string|null} Token or null if not found
 */
function extractTokenFromHash() {
    const hash = window.location.hash;
    if (hash.startsWith('#token=')) {
        return hash.substring(7); // Remove '#token=' prefix
    }
    return null;
}

/**
 * Initialize app with token
 * @param {string} token The shop token
 */
function initializeApp(token) {
    // Set up authorization header for fetch calls
    const originalFetch = window.fetch;
    window.fetch = function(url, options = {}) {
        const headers = new Headers(options.headers || {});
        headers.set('X-Shop-Token', token);

        return originalFetch(url, {
            ...options,
            headers: headers
        });
    };

    // Load shop configuration and display shop name
    loadShopConfig().then(config => {
        // Extract product fields from config
        if (config.config && config.config.productFields) {
            currentProductFields = config.config.productFields;
        } else {
            currentProductFields = [];
        }

        // Extract bill fields from config
        if (config.config && config.config.billFields) {
            currentBillFields = config.config.billFields;
        } else {
            currentBillFields = [];
        }

        // Extract shop state code from config
        if (config.config && config.config.stateCode) {
            currentShopStateCode = config.config.stateCode.toString();
            // Update the place of supply field in the UI
            const placeOfSupplyInput = document.getElementById('newbill-place-of-supply');
            if (placeOfSupplyInput) {
                placeOfSupplyInput.value = currentShopStateCode;
            }
        } else {
            currentShopStateCode = "27"; // Default
        }

        // Load and render products tab
        loadAndRenderProducts();

        // Render dynamic fields for bill custom fields section
        const billCustomFieldsContainer = document.getElementById('newbill-custom-fields-container');
        if (billCustomFieldsContainer) {
            renderDynamicFieldsForBill(billCustomFieldsContainer);
        }
    }).catch(error => {
        console.error('Error loading shop config:', error);
        showError(error.message);
    });
}

/**
 * Load shop configuration and display shop name
 * @returns {Promise<Object>} Promise resolving to shop config
 */
function loadShopConfig() {
    return fetch('/api/config')
        .then(response => {
            if (response.status === 401) {
                throw new Error('Invalid or expired token. Please obtain a new token from the admin panel.');
            }
            if (!response.ok) {
                throw new Error('Failed to load shop configuration');
            }
            return response.json();
        })
        .then(config => {
            const shopNameDisplay = document.getElementById('shop-name-display');
            if (config.shopName) {
                shopNameDisplay.textContent = `Logged in as: ${config.shopName}`;

                // Show app content
                document.getElementById('app-content').style.display = 'block';
                document.getElementById('token-instructions').style.display = 'none';
            } else {
                throw new Error('Invalid shop configuration');
            }
            return config;
        });
}

/**
 * Load products from API and render the products table
 */
function loadAndRenderProducts() {
    fetch('/api/products')
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to load products');
            }
            return response.json();
        })
        .then(products => {
            renderProductsTable(products);
        })
        .catch(error => {
            console.error('Error loading products:', error);
            showError(error.message);
        });
}

/**
 * Render the products table with the given products array
 * @param {Array} products Array of product objects
 */
function renderProductsTable(products) {
    const tbody = document.querySelector('#products-table tbody');
    tbody.innerHTML = ''; // Clear existing rows

    products.forEach(product => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${product.id}</td>
            <td>${product.name || ''}</td>
            <td>${product.description || ''}</td>
            <td>${product.price !== null ? product.price.toFixed(2) : ''}</td>
            <td>${product.hsnCode || ''}</td>
            <td>${product.unit || ''}</td>
            <td>
                <button class="btn btn-sm btn-outline-primary me-1" onclick="editProduct(${product.id})">Edit</button>
                <button class="btn btn-sm btn-outline-danger" onclick="deleteProduct(${product.id})">Delete</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

/**
 * Show token instructions
 */
function showTokenInstructions() {
    document.getElementById('app-content').style.display = 'none';
    document.getElementById('token-instructions').style.display = 'block';
}

/**
 * Show error message
 * @param {string} message Error message to display
 */
function showError(message) {
    const appContent = document.getElementById('app-content');
    appContent.innerHTML = `
        <div class="alert alert-danger">
            ${message}
        </div>
    `;
}

/**
 * Handle search button click
 */
document.addEventListener('click', function(e) {
    if (e.target && e.target.id === 'btn-search-product') {
        const searchTerm = document.getElementById('product-search').value.trim();
        searchProducts(searchTerm);
    }
});

/**
 * Handle enter key in search input
 */
document.addEventListener('keydown', function(e) {
    if (e.target && e.target.id === 'product-search' && e.key === 'Enter') {
        const searchTerm = e.target.value.trim();
        searchProducts(searchTerm);
    }
});

/**
 * Handle add line item button click in new bill form
 */
document.addEventListener('click', function(e) {
    if (e.target && e.target.id === 'btn-add-line-item') {
        addLineItemRow();
    }
});

/**
 * Handle calculate totals button click in new bill form
 */
document.addEventListener('click', function(e) {
    if (e.target && e.target.id === 'btn-calculate-totals') {
        calculateNewBillTotals();
    }
});

/**
 * Handle filter bills button click
 */
document.addEventListener('click', function(e) {
    if (e.target && e.target.id === 'btn-filter-bills') {
        filterBills();
    }
});

/**
 * Handle reset filters button click
 */
document.addEventListener('click', function(e) {
    if (e.target && e.target.id === 'btn-reset-filters') {
        resetBillsFilters();
    }
});

/**
 * Handle enter key in bills search input
 */
document.addEventListener('keydown', function(e) {
    if (e.target && e.target.id === 'bills-search' && e.key === 'Enter') {
        filterBills();
    }
});

/**
 * Search products by search term (searches name and description)
 * @param {string} searchTerm
 */
function searchProducts(searchTerm) {
    if (searchTerm === '') {
        loadAndRenderProducts();
        return;
    }
    fetch(`/api/products?search=${encodeURIComponent(searchTerm)}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to search products');
            }
            return response.json();
        })
        .then(products => {
            renderProductsTable(products);
        })
        .catch(error => {
            console.error('Error searching products:', error);
            showError(error.message);
        });

/**
 * Handle add product button click
 */
document.addEventListener('click', function(e) {
    if (e.target && e.target.id === 'btn-add-product') {
        openAddProductModal();
    }
});

/**
 * Open the add product modal
 */
function openAddProductModal() {
    // Reset form
    document.getElementById('productForm').reset();
    document.getElementById('productModalLabel').textContent = 'Add Product';
    document.getElementById('btn-save-product').dataset.productId = ''; // Indicates add operation

    // Render dynamic fields based on currentProductFields
    renderDynamicFields();

    // Show modal
    const productModal = new bootstrap.Modal(document.getElementById('productModal'));
    productModal.show();
}

/**
 * Open the edit product modal for the given product ID
 * @param {number} productId
 */
function editProduct(productId) {
    // Fetch the product details
    fetch(`/api/products/${productId}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to load product');
            }
            return response.json();
        })
        .then(product => {
            // Fill form with product data
            document.getElementById('product-name').value = product.name || '';
            document.getElementById('product-description').value = product.description || '';
            document.getElementById('product-price').value = product.price !== null ? product.price : '';
            document.getElementById('product-hsn').value = product.hsnCode || '';
            document.getElementById('product-unit').value = product.unit || '';

            // Set the product ID for save operation
            document.getElementById('btn-save-product').dataset.productId = productId;

            // Update modal title
            document.getElementById('productModalLabel').textContent = 'Edit Product';

            // Render dynamic fields and populate with existing custom fields
            renderDynamicFields(product.customFields);

            // Show modal
            const productModal = new bootstrap.Modal(document.getElementById('productModal'));
            productModal.show();
        })
        .catch(error => {
            console.error('Error loading product for edit:', error);
            showError(error.message);
        });
}

/**
 * Handle save product button click
 */
document.addEventListener('click', function(e) {
    if (e.target && e.target.id === 'btn-save-product') {
        saveProduct();
    }
});

/**
 * Save product (either add or update)
 */
function saveProduct() {
    const productId = document.getElementById('btn-save-product').dataset.productId;
    const isUpdate = productId !== '';

    // Gather form data
    const productData = {
        name: document.getElementById('product-name').value.trim(),
        description: document.getElementById('product-description').value.trim(),
        price: parseFloat(document.getElementById('product-price').value),
        hsnCode: document.getElementById('product-hsn').value.trim(),
        unit: document.getElementById('product-unit').value.trim(),
        customFields: gatherDynamicFields(),
        active: true // For new products, active is true; for updates, we keep existing active state (but we don't have it in form, so we'll rely on the backend to preserve it)
    };

    // Validate
    if (!productData.name || !productData.hsnCode || !productData.unit || isNaN(productData.price) || productData.price < 0) {
        showError('Please fill in all required fields correctly.');
        return;
    }

    // Prepare the request
    let url = '/api/products';
    let method = 'POST';
    if (isUpdate) {
        url = `/api/products/${productId}`;
        method = 'PUT';
    }

    // Make the API call
    fetch(url, {
        method: method,
        headers: {
            'Content-Type': 'application/json',
            'X-Shop-Token': localStorage.getItem('gst_token')
        },
        body: JSON.stringify(productData)
    })
    .then(response => {
        if (!response.ok) {
            return response.text().then(text => { throw new Error(text || `HTTP error! status: ${response.status}`); });
        }
        return response.json();
    })
    .then(() => {
        // Hide modal
        const productModal = bootstrap.Modal.getInstance(document.getElementById('productModal'));
        productModal.hide();

        // Reload products table
        loadAndRenderProducts();

        // Show success message
        showError(isUpdate ? 'Product updated successfully!' : 'Product added successfully!');
    })
    .catch(error => {
        console.error('Error saving product:', error);
        showError(error.message);
    });
}

/**
 * Handle delete product button click
 */
document.addEventListener('click', function(e) {
    if (e.target && e.target.matches('[onclick^="deleteProduct"]')) {
        // Extract product ID from the onclick attribute
        const onclick = e.target.getAttribute('onclick');
        const match = onclick.match(/deleteProduct\((\d+)\)/);
        if (match) {
            const productId = parseInt(match[1]);
            deleteProduct(productId);
        }
    }
});

/**
 * Delete product (soft delete via active=false)
 * @param {number} productId
 */
function deleteProduct(productId) {
    if (!confirm('Are you sure you want to delete this product? This action cannot be undone.')) {
        return;
    }

    fetch(`/api/products/${productId}`, {
        method: 'DELETE',
        headers: {
            'X-Shop-Token': localStorage.getItem('gst_token')
        }
    })
    .then(response => {
        if (!response.ok) {
            return response.text().then(text => { throw new Error(text || `HTTP error! status: ${response.status}`); });
        }
        // Reload products table
        loadAndRenderProducts();
        showError('Product deleted successfully!');
    })
    .catch(error => {
        console.error('Error deleting product:', error);
        showError(error.message);
    });
}

/**
 * Render dynamic fields based on the shop's productFields configuration
 * @param {Object|null} existingValues Optional map of existing values to populate the fields
 */
function renderDynamicFields(existingValues = null) {
    const container = document.getElementById('dynamic-fields-container');
    container.innerHTML = ''; // Clear existing fields

    if (!currentProductFields || currentProductFields.length === 0) {
        return; // No dynamic fields to render
    }

    currentProductFields.forEach(field => {
        const fieldDiv = document.createElement('div');
        fieldDiv.className = 'mb-3';

        const label = document.createElement('label');
        label.htmlFor = `dynamic-field-${field.key}`;
        label.className = 'form-label';
        label.textContent = field.label || field.key;
        fieldDiv.appendChild(label);

        let input;
        switch (field.type) {
            case 'text':
                input = document.createElement('input');
                input.type = 'text';
                input.className = 'form-control';
                input.id = `dynamic-field-${field.key}`;
                if (existingValues && existingValues[field.key] !== undefined) {
                    input.value = existingValues[field.key];
                }
                break;
            case 'number':
                input = document.createElement('input');
                input.type = 'number';
                input.className = 'form-control';
                input.id = `dynamic-field-${field.key}`;
                input.step = 'any';
                if (existingValues && existingValues[field.key] !== undefined) {
                    input.value = existingValues[field.key];
                }
                break;
            case 'date':
                input = document.createElement('input');
                input.type = 'date';
                input.className = 'form-control';
                input.id = `dynamic-field-${field.key}`;
                if (existingValues && existingValues[field.key] !== undefined) {
                    input.value = existingValues[field.key];
                }
                break;
            case 'select':
                input = document.createElement('select');
                input.className = 'form-select';
                input.id = `dynamic-field-${field.key}`;
                // Add options
                if (field.options && Array.isArray(field.options)) {
                    field.options.forEach(option => {
                        const optionEl = document.createElement('option');
                        optionEl.value = option;
                        optionEl.textContent = option;
                        input.appendChild(optionEl);
                    });
                }
                if (existingValues && existingValues[field.key] !== undefined) {
                    input.value = existingValues[field.key];
                }
                break;
            default:
                // Default to text
                input = document.createElement('input');
                input.type = 'text';
                input.className = 'form-control';
                input.id = `dynamic-field-${field.key}`;
                if (existingValues && existingValues[field.key] !== undefined) {
                    input.value = existingValues[field.key];
                }
        }

        if (input) {
            fieldDiv.appendChild(input);
            container.appendChild(fieldDiv);
        }
    });
}

/**
 * Gather values from dynamic fields and return as a map
 * @returns {Object} Map of field keys to their values
 */
function gatherDynamicFields() {
    const fieldsMap = {};
    if (!currentProductFields || currentProductFields.length === 0) {
        return fieldsMap;
    }

    currentProductFields.forEach(field => {
        const input = document.getElementById(`dynamic-field-${field.key}`);
        if (input) {
            let value = null;
            switch (field.type) {
                case 'text':
                case 'number':
                case 'date':
                    value = input.value.trim();
                    // For number, convert to number if not empty
                    if (field.type === 'number' && value !== '') {
                        value = parseFloat(value);
                        if (isNaN(value)) {
                            value = null;
                        }
                    }
                    break;
                case 'select':
                    value = input.value;
                    break;
                default:
                    value = input.value.trim();
            }
            // Only add if value is not empty (or if it's a valid number)
            if (value !== '' && value !== null) {
                fieldsMap[field.key] = value;
            }
        }
    });
    return fieldsMap;
}

/**
 * Validate GSTIN format
 * @param {string} gstin The GSTIN to validate
 * @returns {boolean} True if valid, false otherwise
 */
function validateGSTIN(gstin) {
    if (!gstin || gstin.trim() === '') {
        return true; // Empty GSTIN is valid (optional field)
    }
    const gstinPattern = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9Z][A-Z]$/;
    return gstinPattern.test(gstin.trim().toUpperCase());
}

/**
 * Validate custom fields against the bill fields configuration
 * @param {Object} customFields The custom fields to validate
 * @returns {boolean} True if valid, false otherwise
 */
function validateCustomFields(customFields) {
    if (!currentBillFields || currentBillFields.length === 0) {
        // If no bill fields configured, then no custom fields allowed
        return !customFields || Object.keys(customFields).length === 0;
    }

    if (!customFields) {
        // No custom fields provided, that's fine
        return true;
    }

    // Check each provided custom field against the configuration
    for (const [key, value] of Object.entries(customFields)) {
        // Find the field configuration for this key
        let found = false;
        for (const field of currentBillFields) {
            if (field.key === key) {
                found = true;
                // Validate required
                if (field.required && (value === null || value === '')) {
                    return false; // required field is empty
                }
                // Validate type
                if (field.type) {
                    switch (field.type) {
                        case 'text':
                            if (typeof value !== 'string') {
                                return false;
                            }
                            break;
                        case 'number':
                            if (typeof value !== 'number' || isNaN(value)) {
                                return false;
                            }
                            break;
                        case 'date':
                            // For simplicity, we'll accept string that looks like a date
                            if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
                                return false;
                            }
                            break;
                        case 'select':
                            if (typeof value !== 'string') {
                                return false;
                            }
                            // Check if the value is in the options list
                            if (field.options && Array.isArray(field.options) && !field.options.includes(value)) {
                                return false;
                            }
                            break;
                        default:
                            // Unknown type, accept as string
                            if (typeof value !== 'string') {
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
 * Handle new bill form submission
 */
document.addEventListener('submit', function(e) {
    if (e.target && e.target.id === 'newBillForm') {
        e.preventDefault();
        submitNewBill();
    }
});

/**
 * Submit the new bill form data to the API
 */
function submitNewBill() {
    // Validate GSTIN
    const customerGstin = document.getElementById('newbill-customer-gstin').value.trim();
    if (!validateGSTIN(customerGstin)) {
        showErrorInAlert('newbill-alert', 'Invalid GSTIN format');
        return;
    }

    // Validate custom fields against bill fields configuration
    const customFields = gatherDynamicFieldsFromContainer('newbill-custom-fields-container');
    if (!validateCustomFields(customFields)) {
        showErrorInAlert('newbill-alert', 'Invalid custom fields');
        return;
    }

    // Validate required fields
    const customerName = document.getElementById('newbill-customer-name').value.trim();
    if (!customerName) {
        showErrorInAlert('newbill-alert', 'Customer name is required');
        return;
    }

    // Gather line items
    const lineItems = [];
    const rows = document.querySelectorAll('#line-items-table-body tr');
    let isValid = true;

    rows.forEach(row => {
        const index = row.dataset.lineItemIndex;
        const descriptionInput = document.getElementById(`lineitem-${index}-description`);
        const hsnInput = document.getElementById(`lineitem-${index}-hsn`);
        const quantityInput = document.getElementById(`lineitem-${index}-quantity`);
        const rateInput = document.getElementById(`lineitem-${index}-rate`);
        const discountInput = document.getElementById(`lineitem-${index}-discount`);
        const gstRateInput = document.getElementById(`lineitem-${index}-gstrate`);

        const description = descriptionInput.value.trim();
        const hsnCode = hsnInput.value.trim();
        const quantity = parseFloat(quantityInput.value) || 0;
        const rate = parseFloat(rateInput.value) || 0;
        const discountPercent = parseFloat(discountInput.value) || 0;
        let gstRate = parseFloat(gstRateInput.value);

        // Validate required line item fields
        if (!description || !hsnCode || quantity <= 0 || rate < 0) {
            isValid = false;
            return;
        }

        // If GST rate is not provided, use default
        if (isNaN(gstRate)) {
            gstRate = 18; // Default GST rate
            // TODO: Implement product lookup by HSN code to get actual GST rate
        }

        lineItems.push({
            description: description,
            hsnCode: hsnCode,
            quantity: quantity,
            rate: rate,
            discountPercent: discountPercent,
            gstRate: gstRate
        });
    });

    if (!isValid || lineItems.length === 0) {
        showErrorInAlert('newbill-alert', 'Please add at least one valid line item');
        return;
    }


    // Prepare bill data
    const billData = {
        customerName: customerName,
        customerGstin: customerGstin,
        customerAddress: document.getElementById('newbill-customer-address').value.trim(),
        customerStateCode: document.getElementById('newbill-customer-state-code').value.trim() || null,
        customFields: Object.keys(customFields).length > 0 ? customFields : null,
        lineItems: lineItems
    };

    // Show loading
    showLoading('newbill-alert', 'Submitting bill...');

    // Submit to API
    fetch('/api/bills', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Shop-Token': localStorage.getItem('gst_token')
        },
        body: JSON.stringify(billData)
    })
    .then(response => {
        if (!response.ok) {
            return response.text().then(text => { throw new Error(text || `HTTP error! status: ${response.status}`); });
        }
        return response.json();
    })
    .then(result => {
        // Hide loading and show success
        hideLoading('newbill-alert');
        showSuccessInAlert('newbill-alert', 'Bill submitted successfully!');

        // Reset form
        document.getElementById('newBillForm').reset();
        // Reset line items
        const tbody = document.getElementById('line-items-table-body');
        tbody.innerHTML = '';
        lineItemCount = 0;

        // Clear totals
        document.getElementById('newbill-taxable-amount').textContent = '0.00';
        document.getElementById('newbill-cgst-amount').textContent = '0.00';
        document.getElementById('newbill-sgst-amount').textContent = '0.00';
        document.getElementById('newbill-igst-amount').textContent = '0.00';
        document.getElementById('newbill-round-off-amount').textContent = '0.00';
        document.getElementById('newbill-total-amount').textContent = '0.00';
        document.getElementById('newbill-total-amount-words').textContent = '';

        // Remove dynamic fields container content and re-restore if needed
        const customFieldsContainer = document.getElementById('newbill-custom-fields-container');
        customFieldsContainer.innerHTML = '';
        renderDynamicFieldsForBill(customFieldsContainer);
    })
    .catch(error => {
        console.error('Error submitting bill:', error);
        showErrorInAlert('newbill-alert', error.message);
        hideLoading('newbill-alert');
    });
}

/**
 * Gather values from dynamic fields in a specific container and return as a map
 * @param {string} containerId The ID of the container div
 * @returns {Object} Map of field keys to their values
 */
function gatherDynamicFieldsFromContainer(containerId) {
    const fieldsMap = {};
    const container = document.getElementById(containerId);
    if (!container || !currentBillFields || currentBillFields.length === 0) {
        return fieldsMap;
    }

    currentBillFields.forEach(field => {
        const input = document.getElementById(`dynamic-field-${field.key}`);
        if (input) {
            let value = null;
            switch (field.type) {
                case 'text':
                case 'number':
                case 'date':
                    value = input.value.trim();
                    // For number, convert to number if not empty
                    if (field.type === 'number' && value !== '') {
                        value = parseFloat(value);
                        if (isNaN(value)) {
                            value = null;
                        }
                    }
                    break;
                case 'select':
                    value = input.value;
                    break;
                default:
                    value = input.value.trim();
            }
            // Only add if value is not empty (or if it's a valid number)
            if (value !== '' && value !== null) {
                fieldsMap[field.key] = value;
            }
        }
    });
    return fieldsMap;
}

/**
 * Render dynamic fields for bill custom fields section
 * @param {HTMLElement} container The container element to render fields into
 */
function renderDynamicFieldsForBill(container) {
    container.innerHTML = ''; // Clear existing fields

    if (!currentBillFields || currentBillFields.length === 0) {
        return; // No dynamic fields to render
    }

    currentBillFields.forEach(field => {
        const fieldDiv = document.createElement('div');
        fieldDiv.className = 'mb-3';

        const label = document.createElement('label');
        label.htmlFor = `dynamic-field-${field.key}`;
        label.className = 'form-label';
        label.textContent = field.label || field.key;
        fieldDiv.appendChild(label);

        let input;
        switch (field.type) {
            case 'text':
                input = document.createElement('input');
                input.type = 'text';
                input.className = 'form-control';
                input.id = `dynamic-field-${field.key}`;
                break;
            case 'number':
                input = document.createElement('input');
                input.type = 'number';
                input.className = 'form-control';
                input.id = `dynamic-field-${field.key}`;
                input.step = 'any';
                break;
            case 'date':
                input = document.createElement('input');
                input.type = 'date';
                input.className = 'form-control';
                input.id = `dynamic-field-${field.key}`;
                break;
            case 'select':
                input = document.createElement('select');
                input.className = 'form-select';
                input.id = `dynamic-field-${field.key}`;
                // Add options
                if (field.options && Array.isArray(field.options)) {
                    field.options.forEach(option => {
                        const optionEl = document.createElement('option');
                        optionEl.value = option;
                        optionEl.textContent = option;
                        input.appendChild(optionEl);
                    });
                }
                break;
            default:
                // Default to text
                input = document.createElement('input');
                input.type = 'text';
                input.className = 'form-control';
                input.id = `dynamic-field-${field.key}`;
        }

        if (input) {
            fieldDiv.appendChild(input);
            container.appendChild(fieldDiv);
        }
    };

/**
 * Utility function to make authenticated API calls
 */
async function apiCall(endpoint, options = {}) {
 {
    const token = localStorage.getItem('gst_token');
    if (!token) {
        throw new Error('No token available');
    }

    const headers = new Headers(options.headers || {});
    headers.set('X-Shop-Token', token);

    const response = await fetch(endpoint, {
        ...options,
        headers: headers
    });

    if (!response.ok) {
        throw new Error(`API call failed: ${response.status}`);
    }

    return response.json();
}