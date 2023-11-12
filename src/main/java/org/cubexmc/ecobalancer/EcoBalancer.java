package org.cubexmc.ecobalancer;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.cubexmc.ecobalancer.commands.CheckAllCommand;
import org.cubexmc.ecobalancer.commands.CheckPlayerCommand;
import org.cubexmc.ecobalancer.commands.UtilCommand;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    private String scheduleType;
    private List<Integer> scheduleDaysOfWeek;
    private List<Integer> scheduleDatesOfMonth;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();  // 保存默认配置
        loadConfiguration();  // 加载配置

        // Check for an existing log file and compress it if found
        File logDir = new File(getDataFolder() + File.separator + "logs");
        if (!logDir.exists()) {
            logDir.mkdirs(); // This will create the directory if it does not exist
        }
        File lockFile = new File(getDataFolder() + File.separator + "logs" + File.separator + "latest.log.lck");
        if (lockFile.exists()) {
            lockFile.delete(); // This will delete the lock file if it exists
        }
        File existingLogFile = new File(getDataFolder() + File.separator + "logs" + File.separator + "latest.log");
        if (existingLogFile.exists()) {
            compressExistingLogFile(existingLogFile);
        }

        try {
            fileHandler = new FileHandler(getDataFolder() + File.separator + "logs" + File.separator + "latest.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileLogger.addHandler(fileHandler);
            fileLogger.setUseParentHandlers(false); // 不将日志转发到父处理器，即不在控制台输出
        } catch (IOException e) {
            getLogger().severe("Could not setup file logger for EcoBalancer");
            e.printStackTrace();
        }

        getCommand("ecobal").setExecutor(new UtilCommand(this));
        getCommand("checkall").setExecutor(new CheckAllCommand(this));
        getCommand("checkplayer").setExecutor(new CheckPlayerCommand(this));
        getLogger().info("EcoBalancer enabled!");
    }

    public void loadConfiguration() {
        // Cancel all scheduled tasks
        Bukkit.getScheduler().cancelTasks(this);

        // Load the new scheduling configuration
        scheduleType = getConfig().getString("schedule.type", "daily");
        scheduleDaysOfWeek = getConfig().getIntegerList("schedule.days-of-week");
        scheduleDatesOfMonth = getConfig().getIntegerList("schedule.dates-of-month");
        String checkTime = getConfig().getString("check-time", "01:00");  // 读取配置
        int hourOfDay = Integer.parseInt(checkTime.split(":")[0]);
        int minute = Integer.parseInt(checkTime.split(":")[1]);
        // Determine which scheduling method to use based on the type
        switch (scheduleType.toLowerCase()) {
            case "weekly":
                scheduleWeeklyChecks();
                break;
            case "monthly":
                scheduleMonthlyChecks();
                break;
            default:
                scheduleDailyChecks(hourOfDay, minute);
                break;
        }
        // deduction setting
        deductBasedOnTime = getConfig().getBoolean("deduct-based-on-time", false);
        inactiveDaysToDeduct = getConfig().getInt("inactive-days-to-deduct", 50);
        inactiveDaysToClear = getConfig().getInt("inactive-days-to-clear", 500);
        List<Map<?, ?>> rawTaxBrackets = getConfig().getMapList("tax-brackets");

        for (Map<?, ?> bracket : rawTaxBrackets) {
            Integer threshold = bracket.get("threshold") == null ? Integer.MAX_VALUE : (Integer) bracket.get("threshold");
            Double rate = (Double) bracket.get("rate");
            taxBrackets.put(threshold, rate);
        }
    }

    @Override
    public void onDisable() {
        // Ensure all pending logs are flushed and the handler is closed
        if (fileHandler != null) {
            fileHandler.flush();
            fileLogger.removeHandler(fileHandler);
            fileHandler.close();
        }

        // Now attempt to compress the log file
        File logFile = new File(getDataFolder() + File.separator + "logs" + File.separator + "latest.log");
        if (logFile.exists()) {
            compressExistingLogFile(logFile);
        }

        getLogger().info("EcoBalancer disabled.");
    }

    // Method to compress the existing log file
    private void compressExistingLogFile(File logFile) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = dateFormat.format(new Date(logFile.lastModified()));
        File compressedFile = new File(logFile.getParent(), timestamp + ".gz");

        try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(compressedFile))) {
            Files.copy(logFile.toPath(), gzos);
        } catch (IOException e) {
            getLogger().severe("Could not compress the log file: " + e.getMessage());
        }
        logFile.delete(); // 删除原始日志文件
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

    public void checkBalance(CommandSender sender, long currentTime, OfflinePlayer player, boolean log) {
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
                if (sender != null) {
                    sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " 的余额置为" + String.format("%.2f", balance) + "。");
                    sender.sendMessage(ChatColor.GREEN + "已将余额置为" + String.format("%.2f", econ.getBalance(player)) + "。");
                }
                if (log)
                    fileLogger.info("[负额度] 已将 " + player.getName() + " 的余额置为" + String.format("%.2f", econ.getBalance(player)) + "。");
            } else {
                if (daysOffline > inactiveDaysToClear) {
                    // 清除超过inactiveDaysToClear天未上线的玩家
                    econ.withdrawPlayer(player, balance);
                    if (sender != null) {
                        sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " 已经离线" + daysOffline + "天，极不活跃。");
                        sender.sendMessage(ChatColor.GREEN + "已将余额置为" + String.format("%.2f", econ.getBalance(player)) + "。");
                    }
                    if (log)
                        fileLogger.info("[及不活跃] 已将 " + player.getName() + " 的余额置为" + String.format("%.2f", econ.getBalance(player)) + "。");
                } else if (daysOffline > inactiveDaysToDeduct) {
                    // 对于超过50天未上线的玩家，按税率扣除
                    double deduction = balance * deductionRate;
                    econ.withdrawPlayer(player, deduction);
                    if (sender != null) {
                        sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " 已经离线" + daysOffline + "天，较不活跃。");
                        sender.sendMessage(ChatColor.GREEN + "已从余额中扣除 " + ChatColor.YELLOW + String.format("%.2f", deduction) + ChatColor.GREEN + "。");
                    }
                    if (log)
                        fileLogger.info("[较不活跃] 已从 " + player.getName() + " 的余额中扣除 " + String.format("%.2f", deduction) + "。");
                } else {
                    if (sender != null) {
                        sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " 仅离线" + daysOffline + "天，较活跃。");
                    }
                }
            }
        } else {
            double deduction = balance * deductionRate;
            econ.withdrawPlayer(player, deduction);
            if (sender != null)
                sender.sendMessage(ChatColor.GREEN + "已从 " + ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " 的余额中扣除 " + ChatColor.YELLOW + String.format("%.2f", deduction) + ChatColor.GREEN + "。");
            if (log)
                fileLogger.info("已从 " + player.getName() + " 的余额中扣除 " + String.format("%.2f", deduction) + "。");
        }
    }

    private long calculateInitialDailyDelay(int hourOfDay, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar nextCheck = (Calendar) now.clone();
        nextCheck.set(Calendar.HOUR_OF_DAY, hourOfDay);
        nextCheck.set(Calendar.MINUTE, minute);
        nextCheck.set(Calendar.SECOND, 0);
        nextCheck.set(Calendar.MILLISECOND, 0);

        // If the next check time is before the current time, add one day
        if (nextCheck.before(now)) {
            nextCheck.add(Calendar.DAY_OF_MONTH, 1);
        }

        return (nextCheck.getTimeInMillis() - now.getTimeInMillis()) / 50; // Ticks until the next check
    }

    private long calculateInitialWeeklyDelay() {
        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.DAY_OF_WEEK);
        int daysUntilNextCheck = scheduleDaysOfWeek.stream()
                .sorted()
                .filter(dayOfWeek -> dayOfWeek >= today)
                .findFirst()
                .orElse(scheduleDaysOfWeek.get(0)) - today;

        // If the next check date is in the next week
        if (daysUntilNextCheck < 0) {
            daysUntilNextCheck += 7; // days left in the current week
        }

        Calendar nextCheck = (Calendar) now.clone();
        nextCheck.add(Calendar.DAY_OF_WEEK, daysUntilNextCheck);
        nextCheck.set(Calendar.HOUR_OF_DAY, 0);
        nextCheck.set(Calendar.MINUTE, 0);
        nextCheck.set(Calendar.SECOND, 0);
        nextCheck.set(Calendar.MILLISECOND, 0);

        return (nextCheck.getTimeInMillis() - now.getTimeInMillis()) / 50; // Ticks until the next check
    }

    private long calculateInitialMonthlyDelay() {
        Calendar now = Calendar.getInstance();
        int dayOfMonth = now.get(Calendar.DAY_OF_MONTH);
        int daysUntilNextCheck = scheduleDatesOfMonth.stream()
                .filter(date -> date >= dayOfMonth)
                .findFirst()
                .orElse(scheduleDatesOfMonth.get(0)) - dayOfMonth;

        // If the next check date is in the next month
        if (daysUntilNextCheck < 0) {
            daysUntilNextCheck += now.getActualMaximum(Calendar.DAY_OF_MONTH); // days left in the current month
        }

        Calendar nextCheck = (Calendar) now.clone();
        nextCheck.add(Calendar.DAY_OF_MONTH, daysUntilNextCheck);
        nextCheck.set(Calendar.HOUR_OF_DAY, 0);
        nextCheck.set(Calendar.MINUTE, 0);
        nextCheck.set(Calendar.SECOND, 0);
        nextCheck.set(Calendar.MILLISECOND, 0);

        return (nextCheck.getTimeInMillis() - now.getTimeInMillis()) / 50; // Ticks until the next check
    }

    private void scheduleDailyChecks(int hourOfDay, int minute) {
        // Calculate initial delay
        long initialDelay = calculateInitialDailyDelay(hourOfDay, minute);
        long ticksPerDay = 20L * 60 * 60 * 24;

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            // Your daily check code here
            checkAll(null); // Replace null with the actual sender, if available
        }, initialDelay, ticksPerDay); // Run task every day
    }
    private void scheduleWeeklyChecks() {
        long ticksPerWeek = 20L * 60 * 60 * 24 * 7;
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            Calendar now = Calendar.getInstance();
            int today = now.get(Calendar.DAY_OF_WEEK);
            if (scheduleDaysOfWeek.contains(today)) {
                checkAll(null); // Replace null with the actual sender, if available
            }
        }, calculateInitialWeeklyDelay(), ticksPerWeek); // Adjust delay for weekly
    }

    private void scheduleMonthlyChecks() {
        long ticksPerMonth = 20L * 60 * 60 * 24 * 30; // Rough approximation
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            Calendar now = Calendar.getInstance();
            int today = now.get(Calendar.DATE);
            if (scheduleDatesOfMonth.contains(today)) {
                checkAll(null); // Replace null with the actual sender, if available
            }
        }, calculateInitialMonthlyDelay(), ticksPerMonth); // Adjust for monthly
    }

    public void checkAll(CommandSender sender) {
        final long currentTime = System.currentTimeMillis();
        final OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        final int batchSize = 100; // Number of players to process at once
        final int delay = 10; // Delay in ticks between batches (20 ticks = 1 second)

        class BatchRunnable implements Runnable {
            private int index = 0;

            @Override
            public void run() {
                int start = index;
                int end = Math.min(index + batchSize, players.length);
                for (int i = index; i < end; i++) {
                    OfflinePlayer player = players[i];
                    checkBalance(null, currentTime, player, false);
                }
                index += batchSize;
                if (sender != null) {
                    // Send a message to the sender after each batch
                    Bukkit.getScheduler().runTask(EcoBalancer.this, () -> sender.sendMessage(ChatColor.GRAY + "处理了" + (end - start) + "个玩家，共处理：" + end));
                } else {
                    getLogger().info("处理了" + (end - start) + "个玩家，共处理：" + end);
                }
                if (index < players.length) {
                    // Schedule next batch
                    Bukkit.getScheduler().runTaskLaterAsynchronously(EcoBalancer.this, this, delay);
                } else {
                    // All players have been processed, notify the sender
                    // Send a message to the sender after each batch
                    Bukkit.getScheduler().runTask(EcoBalancer.this, () -> {
                        if (sender != null) {
                            sender.sendMessage(ChatColor.GREEN + "所有玩家都已被洗劫。");
                        } else {
                            getLogger().info("所有玩家都已被洗劫。");
                            fileLogger.info("处理了" + players.length + "个玩家，共处理：" + end);
                        }
                    });
                }
            }
        }

        // Start the first batch
        Bukkit.getScheduler().runTaskAsynchronously(this, new BatchRunnable());
    }
}
