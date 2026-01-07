package org.toyotech.shop;

import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final Shop plugin;
    public String backendBaseUrl;
    public String pluginSecret;
    public long pollIntervalTicks;
    public long repayScanIntervalTicks;
    public long creditDaysTicks;
    public boolean adminLog;

    public ConfigManager(Shop plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        load();
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        backendBaseUrl = config.getString("backendBaseUrl", "http://localhost:3000");
        pluginSecret = config.getString("pluginSecret", "secret");
        pollIntervalTicks = config.getLong("pollIntervalTicks", 40);
        repayScanIntervalTicks = config.getLong("repayScanIntervalTicks", 20);
        creditDaysTicks = config.getLong("creditDaysTicks", 120000);
        adminLog = config.getBoolean("adminLog", true);
    }
}
