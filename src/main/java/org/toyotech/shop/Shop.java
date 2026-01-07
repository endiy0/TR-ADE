package org.toyotech.shop;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Shop extends JavaPlugin {
    public ConfigManager cfg;
    public NetworkManager network;
    public DebtManager debtManager;
    private long currentTick = 0;

    @Override
    public void onEnable() {
        cfg = new ConfigManager(this);
        network = new NetworkManager(this);
        debtManager = new DebtManager(this);
        currentTick = debtManager.loadGlobalTick();

        getCommand("trade").setExecutor(new TradeCommand(this));
        getServer().getPluginManager().registerEvents(new EventListener(this), this);

        // Tick Counter
        getServer().getScheduler().runTaskTimer(this, () -> {
            currentTick++;
            // Check overdue occasionally? Or in repay scan
        }, 1L, 1L);

        // Order Poller
        getServer().getScheduler().runTaskTimerAsynchronously(this, 
            new OrderPoller(this), 
            20L, cfg.pollIntervalTicks);
            
        // Repay Scan
        getServer().getScheduler().runTaskTimer(this, 
            () -> {
                debtManager.scanRepayment();
                debtManager.getOverduePlayers(); // Updates status
            }, 
            60L, cfg.repayScanIntervalTicks);
            
        getLogger().info("Trade Plugin Enabled!");
    }

    @Override
    public void onDisable() {
        if (debtManager != null) debtManager.save();
    }
    
    public long getCurrentTick() {
        return currentTick; // This is relative to server start (session).
        // Ideally should be persistent if we want "5 days" to persist across restarts accurately.
        // For MVP, session tick is acceptable if we acknowledge it resets.
        // Or we can save/load it.
    }

    public void addTick(long amount) {
        this.currentTick += amount;
    }
}