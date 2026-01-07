package org.toyotech.shop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class EventListener implements Listener {
    private final Shop plugin;

    public EventListener(Shop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        String name = p.getName();
        
        List<String> overdue = plugin.debtManager.getOverduePlayers();
        if (overdue.contains(name)) {
            // Priority 1: Overdue -> Wipe everything
            event.setKeepInventory(false);
            event.getDrops().clear();
            event.setKeepLevel(false);
            event.setDroppedExp(0);
            p.sendMessage("§c[TRADE] 빚이 연체된 상태에서 사망했습니다! 인벤토리가 초기화되고 아이템이 드롭되지 않습니다.");
            return;
        }
        
        // Priority 2: Ticket (Physical Item)
        ItemStack ticket = null;
        for (ItemStack is : p.getInventory().getContents()) {
            if (is != null && is.hasItemMeta() && is.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "invsave_ticket"), PersistentDataType.BYTE)) {
                ticket = is;
                break;
            }
        }

        if (ticket != null) {
            // Consume 1 ticket
            ticket.setAmount(ticket.getAmount() - 1); // This works because we haven't setKeepInventory(true) yet so this modifies the 'current' inventory which is about to be saved
            
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            p.sendMessage("§a[TRADE] 인벤토리가 보존되었습니다 (인벤세이브권 사용됨).");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Just report state
    }
    
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.network.reportPlayerState(event.getPlayer().getName(), false, 0, 0, plugin.getCurrentTick());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!event.hasItem()) return;
        ItemStack item = event.getItem();
        if (item.getItemMeta() == null) return;
        
        if (item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "tp_ticket"), PersistentDataType.BYTE)) {
            event.setCancelled(true);
            openTpGui(event.getPlayer());
        }
    }

    private void openTpGui(Player p) {
        Inventory inv = Bukkit.createInventory(p, 54, "TP권");
        for (Player online : Bukkit.getOnlinePlayers()) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(online);
            meta.setDisplayName("§e" + online.getName());
            skull.setItemMeta(meta);
            inv.addItem(skull);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("TP권")) return;
        event.setCancelled(true);
        
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() != Material.PLAYER_HEAD) return;
        
        Player p = (Player) event.getWhoClicked();
        String targetName = event.getCurrentItem().getItemMeta().getDisplayName().substring(2); // remove color code
        Player target = Bukkit.getPlayerExact(targetName);
        
        if (target != null) {
            // Consume ticket
            ItemStack hand = p.getInventory().getItemInMainHand();
            // Verify it is ticket
            if (hand.hasItemMeta() && hand.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "tp_ticket"), PersistentDataType.BYTE)) {
                hand.setAmount(hand.getAmount() - 1);
                p.teleport(target);
                p.sendMessage("§a" + targetName + "님에게 텔레포트했습니다.");
                p.closeInventory();
            }
        }
    }
}
