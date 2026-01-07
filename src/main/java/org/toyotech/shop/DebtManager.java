package org.toyotech.shop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DebtManager {
    private final Shop plugin;
    private File debtsFile;
    private FileConfiguration debtsConfig;
    private File playersFile;
    private FileConfiguration playersConfig;

    public DebtManager(Shop plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        debtsFile = new File(plugin.getDataFolder(), "debts.yml");
        if (!debtsFile.exists()) {
            debtsFile.getParentFile().mkdirs();
            try { debtsFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        debtsConfig = YamlConfiguration.loadConfiguration(debtsFile);

        playersFile = new File(plugin.getDataFolder(), "players.yml");
        if (!playersFile.exists()) {
            try { playersFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);
    }

    public void save() {
        try {
            debtsConfig.set("globalTick", plugin.getCurrentTick());
            debtsConfig.save(debtsFile);
            playersConfig.save(playersFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public long loadGlobalTick() {
        return debtsConfig.getLong("globalTick", 0);
    }

    // Ticket Management
    public int getTickets(String playerName, String type) {
        return playersConfig.getInt(playerName + "." + type, 0);
    }

    public void addTickets(String playerName, String type, int amount) {
        int current = getTickets(playerName, type);
        playersConfig.set(playerName + "." + type, current + amount);
        save();
        reportState(playerName);
    }

    public boolean useTicket(String playerName, String type) {
        int current = getTickets(playerName, type);
        if (current > 0) {
            playersConfig.set(playerName + "." + type, current - 1);
            save();
            reportState(playerName);
            return true;
        }
        return false;
    }

    // Debt Management
    public void addDebt(String playerName, String material, int amount, long dueTick) {
        String id = UUID.randomUUID().toString();
        String path = "debts." + id;
        debtsConfig.set(path + ".player", playerName);
        debtsConfig.set(path + ".material", material);
        debtsConfig.set(path + ".remaining", amount);
        debtsConfig.set(path + ".dueTick", dueTick);
        debtsConfig.set(path + ".status", "OPEN");
        save();
        
        plugin.network.upsertDebt(id, playerName, material, amount, dueTick, "OPEN");
    }

    public List<String> getOverduePlayers() {
        List<String> list = new ArrayList<>();
        if (debtsConfig.getConfigurationSection("debts") == null) return list;
        
        long currentTick = plugin.getCurrentTick();
        
        for (String id : debtsConfig.getConfigurationSection("debts").getKeys(false)) {
            String path = "debts." + id;
            String status = debtsConfig.getString(path + ".status");
            long due = debtsConfig.getLong(path + ".dueTick");
            int remaining = debtsConfig.getInt(path + ".remaining");
            String player = debtsConfig.getString(path + ".player");
            
            if ("OPEN".equals(status) && remaining > 0 && currentTick > due) {
                debtsConfig.set(path + ".status", "OVERDUE");
                plugin.network.upsertDebt(id, player, debtsConfig.getString(path + ".material"), remaining, due, "OVERDUE");
                save();
            }
            
            if ("OVERDUE".equals(debtsConfig.getString(path + ".status"))) {
                list.add(player);
            }
        }
        return list;
    }

    public void scanRepayment() {
        if (debtsConfig.getConfigurationSection("debts") == null) return;
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            String name = p.getName();
            // Find debts for this player
            // Ideally sort by created/due, but here iteration order (insertion) is roughly FIFO in YML if keys are UUIDs? Not guaranteed.
            // Let's iterate all debts (inefficient for large scale but fine for MVP)
            
            for (String id : debtsConfig.getConfigurationSection("debts").getKeys(false)) {
                String path = "debts." + id;
                if (!name.equals(debtsConfig.getString(path + ".player"))) continue;
                String status = debtsConfig.getString(path + ".status");
                if ("PAID".equals(status)) continue;
                
                String matName = debtsConfig.getString(path + ".material");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) continue;
                
                int remaining = debtsConfig.getInt(path + ".remaining");
                if (remaining <= 0) continue;

                // Check inventory
                int amountInInv = 0;
                for (ItemStack is : p.getInventory().getContents()) {
                    if (is != null && is.getType() == mat) {
                        amountInInv += is.getAmount();
                    }
                }
                
                if (amountInInv > 0) {
                    int toTake = Math.min(amountInInv, remaining);
                    p.getInventory().removeItem(new ItemStack(mat, toTake));
                    p.sendMessage("§e[TRADE] 빚 상환: " + toTake + "개의 " + matName + "을(를) 가져갔습니다.");
                    
                    remaining -= toTake;
                    debtsConfig.set(path + ".remaining", remaining);
                    
                    if (remaining == 0) {
                        debtsConfig.set(path + ".status", "PAID");
                        p.sendMessage("§a[TRADE] 빚 " + id.substring(0,8) + " 상환 완료!");
                    }
                    save();
                    
                    plugin.network.upsertDebt(id, name, matName, remaining, debtsConfig.getLong(path + ".dueTick"), debtsConfig.getString(path + ".status"));
                    
                    // If we took items, we might need to stop if we want to prioritize one debt per tick or continue?
                    // Let's continue if we still have items? 
                    // Simpler: Just handle one debt per scan or loop until inv empty. 
                    // Let's just break here to be safe and simple (one debt processed per tick per player)
                    break;
                }
            }
            
            reportState(name);
        }
    }
    
    private void reportState(String name) {
        plugin.network.reportPlayerState(
            name, 
            Bukkit.getPlayerExact(name) != null, 
            getTickets(name, "tp_tickets"), 
            getTickets(name, "invsave_tickets"), 
            plugin.getCurrentTick()
        );
    }
}
