// Configuration
const API_BASE = '/api/v1/orders';
let isSeeded = false;

// Terminal Log Helper
function addLog(message, type = 'info', worker = 'System') {
    const consoleBody = document.getElementById('consoleLogs');
    if (!consoleBody) return;

    const time = new Date().toLocaleTimeString();
    const entry = document.createElement('div');
    entry.className = 'log-entry';

    let workerClass = '';
    if (worker.includes('Worker')) {
        if (worker.includes('Thread-A') || message.includes('DB') || message.includes('stock')) workerClass = 'db-deduct';
        else if (worker.includes('Thread-B') || message.includes('payment') || message.includes('Payment')) workerClass = 'payment';
        else if (worker.includes('Thread-C') || message.includes('Notification') || message.includes('email')) workerClass = 'notify';
    }

    entry.innerHTML = `
        <span class="log-time">[${time}]</span>
        <span class="log-worker ${workerClass}">${worker}:</span>
        <span class="log-${type}">${message}</span>
    `;

    consoleBody.appendChild(entry);
    consoleBody.scrollTop = consoleBody.scrollHeight;
}

function clearConsole() {
    const consoleBody = document.getElementById('consoleLogs');
    if (consoleBody) {
        consoleBody.innerHTML = '';
        addLog('Console logs cleared.', 'info');
    }
}

// Format Datetime
function formatTime(dateTimeStr) {
    if (!dateTimeStr) return '';
    try {
        const dt = new Date(dateTimeStr);
        return dt.toLocaleTimeString() + ' ' + dt.toLocaleDateString();
    } catch (e) {
        return dateTimeStr;
    }
}

// Fetch Metrics & Catalog state
async function fetchMetrics() {
    try {
        const response = await fetch(`${API_BASE}/metrics`);
        if (!response.ok) throw new Error('Failed to load metrics');
        
        const res = await response.json();
        if (res.success && res.data) {
            const data = res.data;
            
            // Update Stats
            document.getElementById('statActiveThreads').innerText = data.threadPool.activeThreads;
            document.getElementById('statQueueSize').innerText = data.threadPool.queueSize;
            document.getElementById('statSuccess').innerText = data.orders.success;
            document.getElementById('statPending').innerText = data.orders.pending;
            document.getElementById('statFailed').innerText = data.orders.failed;

            // Indicator light status
            const indicator = document.getElementById('indicatorLight');
            if (data.threadPool.activeThreads > 0) {
                indicator.className = 'glowing-indicator busy';
            } else {
                indicator.className = 'glowing-indicator';
            }

            // Update Catalog
            updateCatalog(data.inventory);
        }
    } catch (error) {
        console.error('Error fetching metrics:', error);
    }
}

// Update Catalog GUI
function updateCatalog(inventory) {
    const productContainer = document.getElementById('productContainer');
    const selectSku = document.getElementById('simSku');
    
    if (!productContainer) return;
    
    if (!inventory || inventory.length === 0) {
        isSeeded = false;
        productContainer.innerHTML = `
            <div style="text-align: center; padding: 2rem; color: var(--text-secondary);">
                <p>Database is empty. Please seed the catalog to begin simulation.</p>
                <button class="btn btn-primary" style="margin-top: 1rem;" onclick="seedDatabase()">Seed Database Now</button>
            </div>
        `;
        selectSku.innerHTML = '<option value="">-- Seed database first --</option>';
        return;
    }

    isSeeded = true;
    
    // Save selection value
    const previousSelection = selectSku.value;
    selectSku.innerHTML = '';
    
    let html = '';
    inventory.forEach(item => {
        // Add option to select
        const option = document.createElement('option');
        option.value = item.sku;
        option.text = `${item.name} (${item.sku})`;
        selectSku.appendChild(option);

        // Progress bar calculations (assume initial seed cap of 200 for iPhone, scale others)
        let maxScale = 200;
        if (item.sku === 'SKU-MACBOOK') maxScale = 50;
        else if (item.sku === 'SKU-IPHONE') maxScale = 100;
        else if (item.sku === 'SKU-AIRPODS') maxScale = 200;
        else if (item.sku === 'SKU-WATCH') maxScale = 150;
        else if (item.sku === 'SKU-IPAD') maxScale = 80;

        const cachePercent = Math.max(0, Math.min(100, (item.cacheStock / maxScale) * 100));
        const dbPercent = Math.max(0, Math.min(100, (item.dbStock / maxScale) * 100));

        html += `
            <div class="product-row">
                <div class="product-meta">
                    <div>
                        <span class="product-name">${item.name}</span>
                        <span class="product-sku">${item.sku}</span>
                    </div>
                    <div>
                        <span class="version-badge">Version ${item.version}</span>
                        <span class="product-price">$${item.price.toFixed(2)}</span>
                    </div>
                </div>
                <div class="stock-indicator-container">
                    <div class="stock-meter">
                        <div class="stock-meter-label">
                            <span>In-Memory Cache</span>
                            <span style="font-weight:600; color:var(--accent-secondary);">${item.cacheStock}</span>
                        </div>
                        <div class="stock-meter-bar-outer">
                            <div class="stock-meter-bar-inner" style="width: ${cachePercent}%;"></div>
                        </div>
                    </div>
                    <div class="stock-meter">
                        <div class="stock-meter-label">
                            <span>DB Physical Stock</span>
                            <span style="font-weight:600; color:var(--color-success);">${item.dbStock}</span>
                        </div>
                        <div class="stock-meter-bar-outer">
                            <div class="stock-meter-bar-inner db-bar" style="width: ${dbPercent}%;"></div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    });

    productContainer.innerHTML = html;
    
    // Restore selection if still valid
    if (previousSelection && [...selectSku.options].some(opt => opt.value === previousSelection)) {
        selectSku.value = previousSelection;
    }
}

// Fetch Recent Orders Table
async function fetchRecentOrders() {
    try {
        const response = await fetch(`${API_BASE}/recent`);
        if (!response.ok) throw new Error('Failed to load recent orders');
        
        const res = await response.json();
        if (res.success && res.data) {
            const orders = res.data;
            const tbody = document.getElementById('ordersTableBody');
            if (!tbody) return;
            
            if (orders.length === 0) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="6" style="text-align: center; color: var(--text-secondary);">No orders recorded. Run a simulation to populate orders.</td>
                    </tr>
                `;
                return;
            }

            tbody.innerHTML = orders.map(order => {
                let badgeClass = 'pending';
                if (order.status === 'SUCCESS') badgeClass = 'success';
                else if (order.status === 'FAILED') badgeClass = 'failed';

                return `
                    <tr>
                        <td style="font-weight: 500;">#${order.id}</td>
                        <td class="tracking-id">${order.orderTrackingId}</td>
                        <td style="font-family: var(--font-mono); font-size: 0.85rem;">${order.customerId}</td>
                        <td style="font-weight: 600; color: var(--text-primary);">$${order.totalAmount.toFixed(2)}</td>
                        <td><span class="status-badge ${badgeClass}">${order.status}</span></td>
                        <td style="font-size: 0.8rem; color: var(--text-secondary);">${formatTime(order.createdAt)}</td>
                    </tr>
                `;
            }).join('');
        }
    } catch (error) {
        console.error('Error fetching recent orders:', error);
    }
}

// Seed Database Action
async function seedDatabase() {
    addLog('Sending database seeding request...', 'info');
    try {
        const response = await fetch(`${API_BASE}/admin/seed`, { method: 'POST' });
        const res = await response.json();
        if (res.success) {
            addLog('Database catalog successfully seeded with test items! In-memory cache loaded.', 'success');
            fetchMetrics();
            fetchRecentOrders();
        } else {
            addLog(`Seeding failed: ${res.message}`, 'error');
        }
    } catch (e) {
        addLog(`Error connecting to server to seed: ${e.message}`, 'error');
    }
}

// Reset Environment Action
async function resetSimulation() {
    addLog('Sending environment reset request...', 'info');
    try {
        const response = await fetch(`${API_BASE}/admin/reset`, { method: 'POST' });
        const res = await response.json();
        if (res.success) {
            addLog('Simulation environment reset complete. Stock refilled, transaction history wiped.', 'success');
            fetchMetrics();
            fetchRecentOrders();
        } else {
            addLog(`Reset failed: ${res.message}`, 'error');
        }
    } catch (e) {
        addLog(`Error connecting to server to reset: ${e.message}`, 'error');
    }
}

// Poll order status for terminal updates
function pollOrderStatus(trackingId, index) {
    let attempts = 0;
    const maxAttempts = 15; // 15 seconds max
    
    const interval = setInterval(async () => {
        attempts++;
        try {
            const response = await fetch(`${API_BASE}/status/${trackingId}`);
            if (response.ok) {
                const res = await response.json();
                if (res.success && res.data) {
                    const status = res.data.status;
                    if (status !== 'PENDING') {
                        clearInterval(interval);
                        const type = (status === 'SUCCESS') ? 'success' : 'error';
                        addLog(`Order #${res.data.orderId} (Tracking ID: ${trackingId.substring(0, 8)}...) resolved to ${status}!`, type, `Worker-Fulfillment`);
                        fetchMetrics();
                        fetchRecentOrders();
                    }
                }
            }
        } catch (e) {
            console.error('Error polling status:', e);
        }
        
        if (attempts >= maxAttempts) {
            clearInterval(interval);
            addLog(`Stopped polling order tracking ID: ${trackingId.substring(0, 8)}... (Timeout)`, 'warn', 'Worker-Monitor');
        }
    }, 1000);
}

// Run Load Simulation
async function runSimulation(event) {
    event.preventDefault();
    if (!isSeeded) {
        addLog('Cannot run simulation: Database is not seeded yet.', 'warn');
        return;
    }

    const sku = document.getElementById('simSku').value;
    const qty = parseInt(document.getElementById('simQty').value);
    const concurrency = parseInt(document.getElementById('simConcurrency').value);
    const scenario = document.getElementById('simScenario').value;

    addLog(`Launching parallel traffic load: firing ${concurrency} concurrent order requests for SKU: ${sku}, Quantity: ${qty}...`, 'info', 'LoadSimulator');

    // Fire requests concurrently using Promise.all
    const requests = [];
    for (let i = 1; i <= concurrency; i++) {
        let customerId = `CUST-${Math.floor(1000 + Math.random() * 9000)}`;
        if (scenario === 'payment_fail' && (i % 3 === 0 || i === 1)) {
            // Seed failures in the queue
            customerId = 'FAIL_PAYMENT';
        }

        const requestBody = {
            customerId: customerId,
            items: [{
                sku: sku,
                quantity: qty
            }]
        };

        const reqPromise = fetch(`${API_BASE}/checkout`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestBody)
        })
        .then(async response => {
            const data = await response.json();
            if (response.ok) {
                const trackingId = data.data.orderTrackingId;
                addLog(`Request ${i}: Checkout accepted! Tracking ID: ${trackingId.substring(0, 8)}... status: PENDING`, 'pending', 'OrderWorker-API');
                pollOrderStatus(trackingId, i);
            } else {
                addLog(`Request ${i} Rejected: ${data.message}`, 'error', 'OrderWorker-API');
            }
        })
        .catch(error => {
            addLog(`Request ${i} failed at network: ${error.message}`, 'error', 'OrderWorker-API');
        });

        requests.push(reqPromise);
    }

    // Refresh immediately to show thread pool loading
    setTimeout(fetchMetrics, 100);
    setTimeout(fetchMetrics, 500);

    await Promise.all(requests);
    
    // Final UI refresh
    fetchMetrics();
    fetchRecentOrders();
}

// Initial Warmup
window.addEventListener('DOMContentLoaded', () => {
    fetchMetrics();
    fetchRecentOrders();
    
    // Auto-refresh stats and inventory every 2 seconds
    setInterval(() => {
        fetchMetrics();
        fetchRecentOrders();
    }, 2000);
});
