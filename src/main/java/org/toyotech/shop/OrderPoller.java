package org.toyotech.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;

public class OrderPoller implements Runnable {
    private final Shop plugin;
    private final Set<String> processing = new HashSet<>();

    public OrderPoller(Shop plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.network.getPendingOrders().thenAccept(orders -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (orders == null) return;
                for (JsonElement el : orders) {
                    processOrder(el.getAsJsonObject());
                }
            });
        }).exceptionally(e -> {
            plugin.getLogger().warning("Failed to poll orders: " + e.getMessage());
            return null;
        });
    }

    private void processOrder(JsonObject order) {
        String id = order.get("id").getAsString();
        if (processing.contains(id)) return;
        processing.add(id);

        plugin.network.updateOrderStatus(id, "PROCESSING", null).thenRun(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    String playerName = order.get("player_name").getAsString();
                    Player player = Bukkit.getPlayerExact(playerName);

                    if (player == null) {
                        plugin.network.updateOrderStatus(id, "FAILED", "Player offline");
                        processing.remove(id);
                        return;
                    }

                    String method = order.get("payment_method").getAsString();
                    String productId = order.get("product_id").getAsString();
                    String costMatName = order.get("currency_material").getAsString();
                    int costAmt = order.get("currency_amount").getAsInt();

                    if ("IMMEDIATE".equals(method)) {
                        handleImmediate(id, player, productId, costMatName, costAmt);
                    } else if ("CREDIT".equals(method)) {
                        handleCredit(id, player, productId, costMatName, costAmt);
                    } else {
                        plugin.network.updateOrderStatus(id, "FAILED", "Unknown payment method");
                        processing.remove(id);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Error processing order " + id + ": " + e.getMessage());
                    e.printStackTrace();
                    plugin.network.updateOrderStatus(id, "FAILED", "Internal Plugin Error");
                    processing.remove(id);
                }
            });
        });
    }

    private void handleImmediate(String id, Player player, String productId, String costMatName, int costAmt) {
        Material costMat = Material.matchMaterial(costMatName);
        if (costMat == null) {
            fail(id, "Invalid currency material: " + costMatName);
            return;
        }

        if (countItems(player, costMat) < costAmt) {
            fail(id, "자금 부족");
            return;
        }

        // Deduct
        player.getInventory().removeItem(new ItemStack(costMat, costAmt));
        
        // Deliver
        deliverProduct(player, productId);
        
        success(id, productId + " 구매 완료");
    }

    private void handleCredit(String id, Player player, String productId, String costMatName, int costAmt) {
        // Deliver first
        deliverProduct(player, productId);
        
        // Create Debt
        long due = plugin.getCurrentTick() + plugin.cfg.creditDaysTicks;
        plugin.debtManager.addDebt(player.getName(), costMatName, costAmt, due);
        
        success(id, "외상 구매 완료. 2일(40분) 내 상환 필요.");
    }
    
    private void deliverProduct(Player player, String productId) {
        switch (productId) {
            case "TP_TICKET":
                giveTpTicket(player);
                // plugin.debtManager.addTickets(player.getName(), "tp_tickets", 1); // Removed: Physical item only
                break;
            case "INVSAVE_TICKET":
                giveInvSaveTicket(player);
                player.sendMessage("§a[TRADE] 인벤세이브권을 받았습니다!");
                break;
            case "MENDING_BOOK":
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
                meta.addStoredEnchant(Enchantment.MENDING, 1, true);
                book.setItemMeta(meta);
                player.getInventory().addItem(book);
                break;
            default:
                Material mat = Material.matchMaterial(productId);
                if (mat != null) {
                    player.getInventory().addItem(new ItemStack(mat));
                } else {
                    plugin.getLogger().warning("Unknown product: " + productId);
                }
        }
    }

    private void giveTpTicket(Player p) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bTP권");
        meta.setLore(java.util.List.of("§7우클릭하여 플레이어에게 텔레포트", "§c사용 시 소모됨"));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "tp_ticket"), PersistentDataType.BYTE, (byte)1);
        item.setItemMeta(meta);
        p.getInventory().addItem(item);
    }

    private void giveInvSaveTicket(Player p) {
        ItemStack item = new ItemStack(Material.BOOK); // Different material to distinguish visibly
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6인벤세이브권");
        meta.setLore(java.util.List.of("§7사망 시 인벤토리 보존", "§c사용 시 소모됨"));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "invsave_ticket"), PersistentDataType.BYTE, (byte)1);
        item.setItemMeta(meta);
        p.getInventory().addItem(item);
    }

    private int countItems(Player p, Material m) {
        int count = 0;
        for (ItemStack is : p.getInventory().getContents()) {
            if (is != null && is.getType() == m) count += is.getAmount();
        }
        return count;
    }

    private void fail(String id, String msg) {
        plugin.network.updateOrderStatus(id, "FAILED", msg);
        processing.remove(id);
    }

    private void success(String id, String msg) {
        plugin.network.updateOrderStatus(id, "SUCCEEDED", msg);
        processing.remove(id);
    }
}
