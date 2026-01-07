const express = require('express');
const sqlite3 = require('sqlite3');
const { open } = require('sqlite');
const cors = require('cors');
const path = require('path');
const fs = require('fs');

const app = express();
const PORT = process.env.PORT || 3000;
const BASE_URL = process.env.BASE_URL || `http://localhost:${PORT}`;
const ADMIN_KEY = process.env.ADMIN_KEY || 'admin'; // Default for dev
const PLUGIN_SECRET = process.env.PLUGIN_SECRET || 'secret'; // Default for dev
const DB_PATH = process.env.DB_PATH || path.join(__dirname, 'data', 'trade.sqlite');

// Ensure data directory exists
const dataDir = path.dirname(DB_PATH);
if (!fs.existsSync(dataDir)){
    fs.mkdirSync(dataDir, { recursive: true });
}

// Middleware
app.use(express.json());
if (process.env.NODE_ENV !== 'production') {
    app.use(cors());
}

// Serve Frontend
const frontDist = path.join(__dirname, 'front', 'dist');
if (fs.existsSync(frontDist)) {
    app.use(express.static(frontDist));
}

// Utils
function encodeBase64Url(str) {
    return Buffer.from(str, 'utf-8').toString('base64')
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/, '');
}

function decodeBase64Url(b64url) {
    try {
        let str = b64url.replace(/-/g, '+').replace(/_/g, '/');
        while (str.length % 4) str += '=';
        return Buffer.from(str, 'base64').toString('utf-8');
    } catch (e) {
        return null;
    }
}

// Database
let db;
async function initDb() {
    db = await open({
        filename: DB_PATH,
        driver: sqlite3.Database
    });

    await db.exec(`
        CREATE TABLE IF NOT EXISTS shop_prices (
            product_id TEXT PRIMARY KEY,
            currency_material TEXT NOT NULL,
            currency_amount INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS orders (
            id TEXT PRIMARY KEY,
            player_name TEXT NOT NULL,
            product_id TEXT NOT NULL,
            payment_method TEXT NOT NULL,
            status TEXT NOT NULL,
            message TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS debts (
            id TEXT PRIMARY KEY,
            player_name TEXT NOT NULL,
            currency_material TEXT NOT NULL,
            remaining_amount INTEGER NOT NULL,
            due_tick BIGINT NOT NULL,
            status TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS player_state (
            player_name TEXT PRIMARY KEY,
            online INTEGER NOT NULL,
            last_seen_at INTEGER NOT NULL,
            tp_tickets INTEGER NOT NULL DEFAULT 0,
            invsave_tickets INTEGER NOT NULL DEFAULT 0,
            last_tick BIGINT NOT NULL DEFAULT 0
        );
    `);

    // Seed Prices
    const count = await db.get('SELECT count(*) as c FROM shop_prices');
    if (count.c === 0) {
        const now = Date.now();
        const initialPrices = [
            { id: 'GOLD_INGOT', mat: 'COBBLESTONE', amt: 64 },
            { id: 'IRON_INGOT', mat: 'GOLD_INGOT', amt: 16 },
            { id: 'DIAMOND', mat: 'IRON_INGOT', amt: 16 },
            { id: 'TP_TICKET', mat: 'DIAMOND', amt: 1 },
            { id: 'INVSAVE_TICKET', mat: 'DIAMOND', amt: 1 },
            { id: 'SHULKER_BOX', mat: 'DIAMOND', amt: 30 },
            { id: 'VILLAGER_SPAWN_EGG', mat: 'EMERALD', amt: 10 },
            { id: 'ENDER_PEARL', mat: 'DIAMOND', amt: 3 },
            { id: 'MENDING_BOOK', mat: 'DIAMOND', amt: 5 }
        ];
        for (const p of initialPrices) {
            await db.run(
                'INSERT INTO shop_prices VALUES (?, ?, ?, ?)',
                [p.id, p.mat, p.amt, now]
            );
        }
        console.log('Seeded initial prices');
    }
}

// Routes

// 1. Create Shop URL
app.get('/create/shop/:playerId', (req, res) => {
    const { playerId } = req.params;
    const encoded = encodeBase64Url(playerId);
    const url = `${BASE_URL}/shop/${encoded}`;
    res.json({ playerName: playerId, encoded, url });
});

// 2. API: Products
app.get('/api/products', async (req, res) => {
    const products = await db.all('SELECT * FROM shop_prices');
    res.json(products);
});

// 3. API: Create Order
app.post('/api/orders', async (req, res) => {
    const { playerName, productId, paymentMethod } = req.body;
    if (!playerName || !productId || !paymentMethod) return res.status(400).send('Missing fields');

    const id = crypto.randomUUID();
    const now = Date.now();
    await db.run(
        'INSERT INTO orders (id, player_name, product_id, payment_method, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
        [id, playerName, productId, paymentMethod, 'PENDING', now, now]
    );
    res.json({ orderId: id });
});

// 4. API: Get Order
app.get('/api/orders/:id', async (req, res) => {
    const order = await db.get('SELECT * FROM orders WHERE id = ?', req.params.id);
    if (!order) return res.status(404).send('Not found');
    res.json(order);
});

// API: Get Player Debts
app.get('/api/debts/:playerName', async (req, res) => {
    const { playerName } = req.params;
    // Only return OPEN or OVERDUE debts
    const debts = await db.all(
        "SELECT * FROM debts WHERE player_name = ? AND (status = 'OPEN' OR status = 'OVERDUE')", 
        playerName
    );
    res.json(debts);
});

// 5. Admin API
app.use('/api/admin', (req, res, next) => {
    const auth = req.headers.authorization; // Expect "Bearer <key>" or just check header
    // Simple check
    if (req.headers['x-admin-key'] === ADMIN_KEY || req.query.key === ADMIN_KEY) return next();
    return res.status(401).send('Unauthorized');
});

app.get('/api/admin/prices', async (req, res) => {
    const prices = await db.all('SELECT * FROM shop_prices');
    res.json(prices);
});

app.put('/api/admin/prices', async (req, res) => {
    const updates = req.body; // Array of objects
    if (!Array.isArray(updates)) return res.status(400).send('Expected array');
    const now = Date.now();
    
    for (const u of updates) {
        if (u.currencyAmount <= 0) continue;
        await db.run(
            'UPDATE shop_prices SET currency_material = ?, currency_amount = ?, updated_at = ? WHERE product_id = ?',
            [u.currencyMaterial, u.currencyAmount, now, u.productId]
        );
    }
    res.json({ success: true });
});

// 6. Plugin API
app.use('/api/plugin', (req, res, next) => {
    if (req.headers['x-trade-secret'] !== PLUGIN_SECRET) return res.status(401).send('Unauthorized Plugin');
    next();
});

app.get('/api/plugin/orders/pending', async (req, res) => {
    // Include price snapshot if needed, but plugin can query/know prices. 
    // Ideally we join, but keeping it simple.
    // Let's JOIN to give plugin the current price so it doesn't need to ask again.
    const orders = await db.all(`
        SELECT o.*, p.currency_material, p.currency_amount 
        FROM orders o 
        LEFT JOIN shop_prices p ON o.product_id = p.product_id
        WHERE o.status = 'PENDING' LIMIT ?`, 
        req.query.limit || 50
    );
    res.json(orders);
});

app.post('/api/plugin/orders/:id/processing', async (req, res) => {
    await db.run("UPDATE orders SET status = 'PROCESSING', updated_at = ? WHERE id = ?", [Date.now(), req.params.id]);
    res.json({ success: true });
});

app.post('/api/plugin/orders/:id/succeeded', async (req, res) => {
    const { message } = req.body;
    await db.run("UPDATE orders SET status = 'SUCCEEDED', message = ?, updated_at = ? WHERE id = ?", [message, Date.now(), req.params.id]);
    res.json({ success: true });
});

app.post('/api/plugin/orders/:id/failed', async (req, res) => {
    const { message } = req.body;
    await db.run("UPDATE orders SET status = 'FAILED', message = ?, updated_at = ? WHERE id = ?", [message, Date.now(), req.params.id]);
    res.json({ success: true });
});

app.post('/api/plugin/debts/upsert', async (req, res) => {
    const { id, playerName, currencyMaterial, remainingAmount, dueTick, status } = req.body;
    // If id exists update, else insert
    // Wait, plugin generates debt ID or server? 
    // Plugin manages debts. Let's assume plugin sends an ID (UUID).
    const now = Date.now();
    const existing = await db.get('SELECT id FROM debts WHERE id = ?', id);
    if (existing) {
        await db.run(
            'UPDATE debts SET remaining_amount = ?, due_tick = ?, status = ?, updated_at = ? WHERE id = ?',
            [remainingAmount, dueTick, status, now, id]
        );
    } else {
        await db.run(
            'INSERT INTO debts VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
            [id, playerName, currencyMaterial, remainingAmount, dueTick, status, now, now]
        );
    }
    res.json({ success: true });
});

app.post('/api/plugin/player_state', async (req, res) => {
    const { playerName, online, tpTickets, invsaveTickets, lastTick } = req.body;
    const now = Date.now();
    await db.run(`
        INSERT INTO player_state (player_name, online, last_seen_at, tp_tickets, invsave_tickets, last_tick)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(player_name) DO UPDATE SET
        online=excluded.online, last_seen_at=excluded.last_seen_at, tp_tickets=excluded.tp_tickets,
        invsave_tickets=excluded.invsave_tickets, last_tick=excluded.last_tick
    `, [playerName, online, now, tpTickets, invsaveTickets, lastTick]);
    res.json({ success: true });
});

// React Routing Fallback
app.get('*', (req, res) => {
    if (fs.existsSync(path.join(frontDist, 'index.html'))) {
        res.sendFile(path.join(frontDist, 'index.html'));
    } else {
        res.status(404).send('Frontend not built. Please run npm run build in Web/front.');
    }
});

// Start
initDb().then(() => {
    app.listen(PORT, () => {
        console.log(`Server running on port ${PORT}`);
    });
});
