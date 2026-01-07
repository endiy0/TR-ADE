package org.toyotech.shop;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TradeCommand implements CommandExecutor {
    private final Shop plugin;

    public TradeCommand(Shop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("trade.admin")) return true;
            plugin.cfg.load();
            plugin.debtManager.load();
            sender.sendMessage("§a리로드 완료.");
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            String targetName;
            if (args.length > 1 && sender.hasPermission("trade.admin")) {
                targetName = args[1];
            } else if (sender instanceof Player) {
                targetName = sender.getName();
            } else {
                sender.sendMessage("플레이어를 지정해주세요.");
                return true;
            }
            
            int tp = plugin.debtManager.getTickets(targetName, "tp_tickets");
            // int inv = plugin.debtManager.getTickets(targetName, "invsave_tickets"); // Virtual removed
            
            // Count physical tickets if online
            int inv = 0;
            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null) {
                for (org.bukkit.inventory.ItemStack is : target.getInventory().getContents()) {
                    if (is != null && is.hasItemMeta() && is.getItemMeta().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "invsave_ticket"), org.bukkit.persistence.PersistentDataType.BYTE)) {
                        inv += is.getAmount();
                    }
                }
            }
            
            sender.sendMessage("§b[상태] " + targetName);
            sender.sendMessage("§7TP권: " + tp);
            sender.sendMessage("§7인벤세이브권(소지 중): " + inv + (target == null ? " (오프라인/확인불가)" : ""));
            // Debts? Maybe in future
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            if (!sender.hasPermission("trade.admin")) return true;
            if (args.length >= 3 && args[1].equalsIgnoreCase("time") && args[2].equalsIgnoreCase("add")) {
                if (args.length < 4) {
                    sender.sendMessage("Usage: /trade debug time add <ticks>");
                    return true;
                }
                try {
                    long amount = Long.parseLong(args[3]);
                    plugin.addTick(amount);
                    sender.sendMessage("§a[Debug] 시간을 " + amount + " 틱만큼 앞당겼습니다. 현재: " + plugin.getCurrentTick());
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c잘못된 숫자입니다.");
                }
                return true;
            }
        }

        return false;
    }
}
