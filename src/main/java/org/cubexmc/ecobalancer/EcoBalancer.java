package org.cubexmc.ecobalancer;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.cubexmc.ecobalancer.commands.CheckAllCommand;
import org.cubexmc.ecobalancer.commands.CheckPlayerCommand;

import java.util.UUID;
import java.util.Calendar;

public final class EcoBalancer extends JavaPlugin {

    private static Economy econ = null;
    private static Permission perms = null;
    private static Chat chat = null;
    private int inactiveDaysToDeduct;
    private double deductionPercentage;
    private int inactiveDaysToClear;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();  // 保存默认配置
        // check time
        String checkTime = getConfig().getString("check-time", "01:00");  // 读取配置
        int hourOfDay = Integer.parseInt(checkTime.split(":")[0]);
        int minute = Integer.parseInt(checkTime.split(":")[1]);
        scheduleCheckAll(hourOfDay, minute);
        // deduction setting
        inactiveDaysToDeduct = getConfig().getInt("inactive-days-to-deduct", 50);
        deductionPercentage = getConfig().getDouble("deduction-percentage", 1);
        inactiveDaysToClear = getConfig().getInt("inactive-days-to-clear", 500);

        getCommand("checkall").setExecutor(new CheckAllCommand(this));
        getCommand("checkplayer").setExecutor(new CheckPlayerCommand(this));
        getLogger().info("EcoBalancer enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("EcoBalancer disabled.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("EcoBalancer disabled [plugin=null]");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().info("EcoBalancer disabled [rsp=null]");
            return false;
        }
        econ = rsp.getProvider();
        getLogger().info(""+(econ != null));
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    public void checkBalance(long currentTime, OfflinePlayer player, boolean print) {
        UUID playerId = player.getUniqueId();
        long lastPlayed = player.getLastPlayed();
        double balance = econ.hasAccount(player) ? econ.getBalance(player) : 0;

        // 计算玩家离线天数
        long daysOffline = (currentTime - lastPlayed) / (1000 * 60 * 60 * 24);
        if (balance < 0) {
            econ.depositPlayer(player, -1*balance);
        } else {
            if (daysOffline > inactiveDaysToClear) {
                // 清除超过500天未上线的玩家
                econ.withdrawPlayer(player, balance);
            } else if (daysOffline > inactiveDaysToDeduct) {
                // 对于超过50天未上线的玩家，扣除其1%的余额
                double deduction = balance * deductionPercentage;
                econ.withdrawPlayer(player, deduction);
            }
        }
        if (print) {
            getLogger().info("Player: " + player.getName() + ", UUID: " + playerId
                + ", Last Played: " + lastPlayed + ", Balance: " + balance
                + ", Days Offline: " + daysOffline);
        }
    }

    private void scheduleCheckAll(int hour, int minute) {
        // 每天调用一次 CheckBalance()
        long oneDayTicks = 20L * 60 * 60 * 24; // 一天的ticks
        Calendar now = Calendar.getInstance();
        Calendar oneAM = Calendar.getInstance();

        // 设置为明天的1AM
        oneAM.set(Calendar.HOUR_OF_DAY, hour);
        oneAM.set(Calendar.MINUTE, minute);
        oneAM.set(Calendar.SECOND, 0);
        oneAM.set(Calendar.MILLISECOND, 0);
        oneAM.add(Calendar.DAY_OF_MONTH, 1);

        // 如果当前时间已经超过1AM，则再加一天
        if (now.after(oneAM)) {
            oneAM.add(Calendar.DAY_OF_MONTH, 1);
        }

        long millisUntilOneAM = oneAM.getTimeInMillis() - now.getTimeInMillis();
        long ticksUntilOneAM = millisUntilOneAM / 50; // 每个tick代表50毫秒
        // 注意：你需要编写代码来正确计算这个值

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                int cnt = 0;
                boolean print = false;
                long currentTime = System.currentTimeMillis();
                for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                    if (cnt < 10) {
                        print = true;
                    }
                    checkBalance(currentTime, player, print);
                }
            }
        }, ticksUntilOneAM, oneDayTicks);
    }
}
