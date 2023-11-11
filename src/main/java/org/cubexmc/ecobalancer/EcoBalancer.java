package org.cubexmc.ecobalancer;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.cubexmc.ecobalancer.commands.CheckAllCommand;
import org.cubexmc.ecobalancer.commands.CheckPlayerCommand;
import org.cubexmc.ecobalancer.commands.HelpCommand;

import java.util.*;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class EcoBalancer extends JavaPlugin {

    private static Economy econ = null;
    private static Permission perms = null;
    private static Chat chat = null;
    private boolean deductBasedOnTime;
    private int inactiveDaysToDeduct;
    private TreeMap<Integer, Double> taxBrackets = new TreeMap<>();
    private int inactiveDaysToClear;
    private Logger fileLogger = Logger.getLogger("EcoBalancerFileLogger");
    private FileHandler fileHandler;

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
        deductBasedOnTime = getConfig().getBoolean("deduct-based-on-time", false);
        inactiveDaysToDeduct = getConfig().getInt("inactive-days-to-deduct", 50);
        List<Map<?, ?>> rawTaxBrackets = getConfig().getMapList("tax-brackets");

        for (Map<?, ?> bracket : rawTaxBrackets) {
            Integer threshold = bracket.get("threshold") == null ? Integer.MAX_VALUE : (Integer) bracket.get("threshold");
            Double rate = (Double) bracket.get("rate");
            taxBrackets.put(threshold, rate);
        }

        inactiveDaysToClear = getConfig().getInt("inactive-days-to-clear", 500);

        try {
            FileHandler fileHandler = new FileHandler(getDataFolder() + File.separator + "EcoBalancer.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileLogger.addHandler(fileHandler);
            fileLogger.setUseParentHandlers(false); // 不将日志转发到父处理器，即不在控制台输出
        } catch (IOException e) {
            getLogger().severe("Could not setup file logger for EcoBalancer");
            e.printStackTrace();
        }

        getCommand("help").setExecutor(new HelpCommand());
        getCommand("checkall").setExecutor(new CheckAllCommand(this));
        getCommand("checkplayer").setExecutor(new CheckPlayerCommand(this));
        getLogger().info("EcoBalancer enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        fileHandler.close();
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
        long daysOffline = (currentTime - lastPlayed) / (1000 * 60 * 60 * 24);
        double balance = econ.hasAccount(player) ? econ.getBalance(player) : 0;
        Double deductionRate = 0.0;

        Map.Entry<Integer, Double> entry = taxBrackets.higherEntry((int) balance);
        if (entry != null) {
            deductionRate = entry.getValue();
        }
        // If no bracket is found (which should not happen because we use Integer.MAX_VALUE for the highest bracket), use a default rate
        if (deductionRate == null) {
            deductionRate = 0.0; // defaultRate should be defined somewhere in your class
        }

        if (deductBasedOnTime) {
            // 计算玩家离线天数
            if (balance < 0) {
                econ.depositPlayer(player, -1 * balance);
                fileLogger.info("[Negative Bal] Depositing " + (-1 * balance) + " to " + player.getName() + " to bring balance to 0.");
            } else {
                if (daysOffline > inactiveDaysToClear) {
                    // 清除超过500天未上线的玩家
                    econ.withdrawPlayer(player, balance);
                    fileLogger.info("[>500] Depositing " + (-1 * balance) + " to " + player.getName() + " to bring balance to 0.");
                } else if (daysOffline > inactiveDaysToDeduct) {
                    // 对于超过50天未上线的玩家，按税率扣除
                    double deduction = balance * deductionRate;
                    econ.withdrawPlayer(player, deduction);
                    fileLogger.info("[<500] Withdrawing " + deduction + " from " + player.getName() + " for inactivity.");
                }
            }
        } else {
            double deduction = balance * deductionRate;
            econ.withdrawPlayer(player, deduction);
            fileLogger.info("Withdrawing " + deduction + " from " + player.getName() + " for inactivity.");
        }
        if (print) {
            getLogger().info("Player: " + player.getName() + ", UUID: " + playerId
                + ", Offline: " + daysOffline + ", Balance: " + balance);
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

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                    checkBalance(currentTime, player, false);
                }
            }
        }, ticksUntilOneAM, oneDayTicks);
    }
}
