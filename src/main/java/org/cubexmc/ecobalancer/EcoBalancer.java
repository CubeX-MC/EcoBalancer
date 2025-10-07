package org.cubexmc.ecobalancer;

import net.md_5.bungee.api.chat.*;
import net.milkbowl.vault.economy.Economy;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.cubexmc.ecobalancer.commands.*;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static java.lang.Math.min;

import java.util.zip.GZIPOutputStream;

import org.cubexmc.ecobalancer.listeners.AdminLoginListener;
import org.cubexmc.ecobalancer.metrics.Metrics;
import org.cubexmc.ecobalancer.utils.SchedulerUtils;
import org.cubexmc.ecobalancer.utils.DatabaseUtils;

@SuppressWarnings("deprecation")
public final class EcoBalancer extends JavaPlugin {
    private static Economy econ = null;
    private boolean deductBasedOnTime;
    private int inactiveDaysToDeduct;
    private TreeMap<Integer, Double> taxBrackets = new TreeMap<>();
    private int inactiveDaysToClear;
    private FileHandler fileHandler;
    private Logger fileLogger = Logger.getLogger("EcoBalancerFileLogger");
    private int recordRetentionDays;
    private String scheduleType;
    private List<Integer> scheduleDaysOfWeek;
    private List<Integer> scheduleDatesOfMonth;
    private String checkTime;
    private FileConfiguration langConfig;
    private boolean taxAccount;
    private String taxAccountName;
    private String messagePrefix;

    private void initFileLogger(boolean rotateExisting) {
        // Create logs dir
        File logDir = new File(getDataFolder() + File.separator + "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        // Rotate previous log only on full startup to avoid churn on reload
        if (rotateExisting) {
            File lockFile = new File(getDataFolder() + File.separator + "logs" + File.separator + "latest.log.lck");
            if (lockFile.exists()) {
                lockFile.delete();
            }
            File existingLogFile = new File(getDataFolder() + File.separator + "logs" + File.separator + "latest.log");
            if (existingLogFile.exists()) {
                compressExistingLogFile(existingLogFile);
            }
        }
        try {
            fileHandler = new FileHandler(getDataFolder() + File.separator + "logs" + File.separator + "latest.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileLogger.addHandler(fileHandler);
            fileLogger.setUseParentHandlers(false);
        } catch (IOException e) {
            getLogger().severe("Could not create the log file handler for EcoBalancer.");
            e.printStackTrace();
        }
    }

    private void updateFileLoggerFromConfig() {
        boolean enable = getConfig().getBoolean("file-logging", true);
        if (enable) {
            if (fileHandler == null) {
                initFileLogger(false);
            }
        } else {
            if (fileHandler != null) {
                try { fileLogger.removeHandler(fileHandler); } catch (Throwable ignored) {}
                try { fileHandler.close(); } catch (Throwable ignored) {}
                fileHandler = null;
            }
        }
    }

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();  // 保存默认配置
        loadConfiguration();  // 加载配置

        // 检查db，如果不存在则创建
        File dataFolder = getDataFolder();
        File databaseFile = new File(dataFolder, "records.db");

        // 如果数据库文件不存在,创建它
        if (!databaseFile.exists()) {
            try {
                databaseFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("无法创建数据库文件: " + e.getMessage());
            }
        }

        // 初始化数据库（建表、索引、PRAGMA）
        DatabaseUtils.initializeTables(this, getLogger());

        long initialDelay = calculateDelayForDaily(Calendar.getInstance(), 0, 0); // 在每天的午夜12点运行
        long cleanupPeriod = 24 * 60 * 60 * 20; // 24小时(以tick为单位)
        SchedulerUtils.runTaskTimer(this, this::cleanupRecords, initialDelay, cleanupPeriod);

        // Optional file logger based on config
        if (getConfig().getBoolean("file-logging", true)) {
            initFileLogger(true);
        }

        // metrics
        int pluginId = 20269; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);
        // Optional: Add custom charts
        metrics.addCustomChart(new Metrics.SimplePie("chart_id", () -> "My value"));

        // 创建虚拟账户
        if (taxAccount && !econ.hasAccount(taxAccountName)) {
            econ.createPlayerAccount(taxAccountName);
        }

        getServer().getPluginManager().registerEvents(new AdminLoginListener(this), this);
        // Register executor and decoupled tab completer
        UtilCommand util = new UtilCommand(this);
        if (getCommand("ecobal") != null) {
            getCommand("ecobal").setExecutor(util);
            getCommand("ecobal").setTabCompleter(new org.cubexmc.ecobalancer.commands.EcoTabCompleter(this, util));
        } else {
            getLogger().severe("Command 'ecobal' not found in plugin.yml. Tab completer not registered.") ;
        }
        displayAsciiArt();
        getLogger().info("EcoBalancer enabled!");
        
        // 告知用户Folia支持状态
        if (checkFoliaSupport()) {
            getLogger().info("Folia support is enabled!");
        } else {
            getLogger().info("Running on standard Bukkit/Spigot server");
        }
    }

    private void displayAsciiArt() {
        String[] asciiArt = {
                "▓█████  ▄████▄   ▒█████   ▄▄▄▄    ▄▄▄       ██▓    ",
                "▓█   ▀ ▒██▀ ▀█  ▒██▒  ██▒▓█████▄ ▒████▄    ▓██▒    ",
                "▒███   ▒▓█    ▄ ▒██░  ██▒▒██▒ ▄██▒██  ▀█▄  ▒██░    ",
                "▒▓█  ▄ ▒▓▓▄ ▄██▒▒██   ██░▒██░█▀  ░██▄▄▄▄██ ▒██░    ",
                "░▒████▒▒ ▓███▀ ░░ ████▓▒░░▓█  ▀█▓ ▓█   ▓██▒░██████▒",
                "░░ ▒░ ░░ ░▒ ▒  ░░ ▒░▒░▒░ ░▒▓███▀▒ ▒▒   ▓▒█░░ ▒░▓  ░",
                " ░ ░  ░  ░  ▒     ░ ▒ Version: " + getDescription().getVersion(),
                "   ░   ░        ░ ░ ░ Author: " + getDescription().getAuthors().get(0),
                "   ░  ░░ ░          ░ Website: " + getDescription().getWebsite(),
                "                      Powered by CubeX"
        };

        // ANSI 转义序列for colors
        final String ANSI_RESET = "\u001B[0m";
        final String ANSI_YELLOW = "\u001B[33m";
    // Colors available if needed in future
        final String ANSI_RED = "\u001B[31m";
        final String ANSI_WHITE = "\u001B[37m";

        // 在控制台输出彩色的 ASCII 艺术字符
        getLogger().info("");
        for (int i = 0; i < asciiArt.length; i++) {
            String line = asciiArt[i];
            if (i < 6) {
                line = ANSI_YELLOW + line + ANSI_RESET;
            } else if (i < 9) {
                // green since 21 characters
                line = ANSI_YELLOW + line.substring(0, 21) + ANSI_RESET + line.substring(21) + ANSI_RESET;
            } else if (i == 9) {
                line = line.replace("Cube", ANSI_RED + "Cube" + ANSI_WHITE).replace("X", ANSI_WHITE + "X" + ANSI_RESET);
            }
            getLogger().info(line);
        }
        getLogger().info("");
    }

    public boolean useTaxAccount() {
        return taxAccount;
    }

    public String getTaxAccountName() {
        return taxAccountName;
    }

    public String getTaxAccountBalance() {
        return String.format("%.2f", econ.getBalance(taxAccountName));
    }

    public void loadConfiguration() {
        // Cancel all scheduled tasks
        SchedulerUtils.cancelAllTasks(this);
        // load language config
        loadLangFile();
        messagePrefix = langConfig.getString("prefix", "&7[&6EcoBalancer&7]&r");
        // Update file logger state on reload
        updateFileLoggerFromConfig();
        recordRetentionDays = getConfig().getInt("record-retention-days", 30);
        // Load the new scheduling configuration
        scheduleType = getConfig().getString("check-schedule.type", "daily");
        scheduleDaysOfWeek = getConfig().getIntegerList("check-schedule.days-of-week");
        scheduleDatesOfMonth = getConfig().getIntegerList("check-schedule.dates-of-month");
        checkTime = getConfig().getString("check-time", "01:00");  // 读取配置
        // Determine which scheduling method to use based on the type
        scheduleCheck(calculateNextDelay());
        // deduction setting
        deductBasedOnTime = getConfig().getBoolean("deduct-based-on-time", false);
        inactiveDaysToDeduct = getConfig().getInt("inactive-days-to-deduct", 50);
        inactiveDaysToClear = getConfig().getInt("inactive-days-to-clear", 500);
        List<Map<?, ?>> rawTaxBrackets = getConfig().getMapList("tax-brackets");
        taxAccount = getConfig().getBoolean("tax-account", false);
        taxAccountName = taxAccount ? getConfig().getString("tax-account-name", "tax") : null;

        // Rebuild tax brackets from config (supports absolute or percentile-based thresholds)
        taxBrackets.clear();
        boolean usePercentileThresholds = getConfig().getBoolean("percentile-thresholds", false);
        if (!usePercentileThresholds) {
            // Standard: thresholds are absolute balances
            for (Map<?, ?> bracket : rawTaxBrackets) {
                Object thObj = bracket.get("threshold");
                int threshold = (thObj == null) ? Integer.MAX_VALUE : ((Number) thObj).intValue();
                Double rate = ((Number) bracket.get("rate")).doubleValue();
                taxBrackets.put(threshold, rate);
            }
        } else {
            // Percentile mode: thresholds represent 0-100 percentiles of current balance distribution
            List<Double> balances = collectAllBalances();
            if (balances.isEmpty()) {
                getLogger().warning("percentile-thresholds enabled but no balances found; falling back to absolute thresholds.");
                for (Map<?, ?> bracket : rawTaxBrackets) {
                    Object thObj = bracket.get("threshold");
                    int threshold = (thObj == null) ? Integer.MAX_VALUE : ((Number) thObj).intValue();
                    Double rate = ((Number) bracket.get("rate")).doubleValue();
                    taxBrackets.put(threshold, rate);
                }
            } else {
                // Sort once for percentile computation
                Collections.sort(balances);
                for (Map<?, ?> bracket : rawTaxBrackets) {
                    Object thObj = bracket.get("threshold");
                    int thresholdAbs;
                    if (thObj == null) {
                        thresholdAbs = Integer.MAX_VALUE;
                    } else {
                        double p = ((Number) thObj).doubleValue();
                        // Clamp percentile to [0,100]
                        if (p < 0) p = 0; if (p > 100) p = 100;
                        double value = getPercentileValue(balances, p);
                        // Use ceil as an exclusive upper bound in int domain
                        if (value >= Integer.MAX_VALUE) {
                            thresholdAbs = Integer.MAX_VALUE;
                        } else if (value <= Integer.MIN_VALUE) {
                            thresholdAbs = Integer.MIN_VALUE + 1; // keep ordering sane
                        } else {
                            thresholdAbs = (int) Math.ceil(value);
                        }
                    }
                    Double rate = ((Number) bracket.get("rate")).doubleValue();
                    taxBrackets.put(thresholdAbs, rate);
                }

                // Log computed absolute thresholds for visibility
                try {
                    StringBuilder sb = new StringBuilder("Computed absolute thresholds from percentiles: ");
                    for (Map.Entry<Integer, Double> e : taxBrackets.entrySet()) {
                        sb.append("[").append(e.getKey() == Integer.MAX_VALUE ? "MAX" : e.getKey()).append(": ")
                          .append(e.getValue()).append("] ");
                    }
                    getLogger().info(sb.toString());
                } catch (Throwable ignored) {}
            }
        }
    }

    private void loadLangFile() {
        // Load the language file based on config
        String lang = getConfig().getString("language", "en_US");
        getLogger().info("Loading language file: " + lang);
        File langFile = new File(getDataFolder(), "lang" + File.separator + lang + ".yml");
        if (!langFile.exists()) {
            saveResource("lang" + File.separator + lang + ".yml", false);
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    public String getFormattedMessage(String path, Map<String, String> placeholders) {
        if (placeholders == null) {
            placeholders = new HashMap<>();
        }
        placeholders.put("prefix", messagePrefix);
        String message = langConfig.getString(path, "Message not found!");
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public TextComponent getFormattedMessage(String path, Map<String, String> placeholders, String[] clickablePlaceholders, TextComponent[] clickableComponents) {
        if (placeholders == null) {
            placeholders = new HashMap<>();
        }
        placeholders.put("prefix", messagePrefix);

        String messageTemplate = langConfig.getString(path, "Message not found!");

        // 初始化一个基础的TextComponent用于最终消息
        TextComponent finalMessage = new TextComponent("");

        // 替换除clickablePlaceholders外的所有占位符
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (!Arrays.asList(clickablePlaceholders).contains(entry.getKey())) {
                messageTemplate = messageTemplate.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }

        // 分割消息模板
        String[] messageParts = messageTemplate.split("%", -1);

        for (int i = 0; i < messageParts.length; i++) {
            String part = messageParts[i];

            // 检查这部分是否匹配任何可点击的占位符
            int placeholderIndex = -1;
            for (int j = 0; j < clickablePlaceholders.length; j++) {
                if (part.startsWith(clickablePlaceholders[j])) {
                    placeholderIndex = j;
                    break;
                }
            }

            if (placeholderIndex != -1) {
                // 如果这部分以一个可点击的占位符开始,添加相应的可点击组件
                finalMessage.addExtra(clickableComponents[placeholderIndex]);

                // 如果占位符后还有文本,作为普通文本添加
                String remainingText = part.substring(clickablePlaceholders[placeholderIndex].length());
                if (!remainingText.isEmpty()) {
                    finalMessage.addExtra(new TextComponent(ChatColor.translateAlternateColorCodes('&', remainingText)));
                }
            } else {
                // 如果这部分不是可点击的占位符,作为普通文本添加
                if (!part.isEmpty()) {
                    finalMessage.addExtra(new TextComponent(ChatColor.translateAlternateColorCodes('&', part)));
                }
            }
        }

        return finalMessage;
    }

    /**
     * 获取语言配置
     * @return 语言配置
     */
    public FileConfiguration getLangConfig() {
        return langConfig;
    }
    
    /**
     * 获取消息前缀
     * @return 消息前缀
     */
    public String getMessagePrefix() {
        return messagePrefix;
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
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HHmm");
        String timestamp = dateFormat.format(new Date(logFile.lastModified()));
        File renamedLogFile = new File(logFile.getParent(), timestamp + ".log");
        // Rename the file to include the timestamp
        if (!logFile.renameTo(renamedLogFile)) {
            getLogger().severe("Could not rename the log file.");
            return;
        }
        // Compress the renamed log file into a .gz file
        File compressedFile = new File(renamedLogFile.getParent(), renamedLogFile.getName() + ".gz");
        try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(compressedFile))) {
            Files.copy(renamedLogFile.toPath(), gzos);
        } catch (IOException e) {
            getLogger().severe("Could not compress the log file: " + e.getMessage());
        }
        // Delete the original (now renamed) log file after it's compressed
        if (!renamedLogFile.delete()) {
            getLogger().severe("Could not delete the original log file after compression.");
        }
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

    public void checkBalance(CommandSender sender, long currentTime, OfflinePlayer player, boolean log, boolean isCheckAll, int operationId) {
        long lastPlayed = player.getLastPlayed();
        long daysOffline = (currentTime - lastPlayed) / (1000 * 60 * 60 * 24);
        double balance = econ.hasAccount(player) ? econ.getBalance(player) : 0;
        Double deductionRate = 0.0;

        double oldBalance = balance;

        if (taxAccount && player.getName().equals(taxAccountName)) return;

        Map.Entry<Integer, Double> entry = taxBrackets.higherEntry((int) balance);
        if (entry != null) {
            deductionRate = entry.getValue();
        }
        // If no bracket is found (which should not happen because we use Integer.MAX_VALUE for the highest bracket), use a default rate
        if (deductionRate == null) {
            deductionRate = 0.0; // defaultRate should be defined somewhere in your class
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("balance", String.format("%.2f", balance));
        placeholders.put("days_offline", String.valueOf(daysOffline));

        // fix all negative balance
        if (balance < 0.0) {
            econ.depositPlayer(player, -1 * balance);
            placeholders.put("new_balance", String.format("%.2f", econ.getBalance(player)));
            sendMessage(sender, "messages.negative_balance", placeholders, log);
        } else if (balance > 0.0) {
            if (deductBasedOnTime) {
                // 计算玩家离线天数
                if (daysOffline > inactiveDaysToClear) {
                    // 清除超过inactiveDaysToClear天未上线的玩家
                    econ.withdrawPlayer(player, balance);
                    if (taxAccount) econ.depositPlayer(taxAccountName, balance); //
                    placeholders.put("new_balance", String.format("%.2f", econ.getBalance(player)));
                    sendMessage(sender, "messages.offline_extreme", placeholders, log);
                } else if (daysOffline > inactiveDaysToDeduct) {
                    // 对于超过50天未上线的玩家，按税率扣除
                    double deduction = min(balance, balance * deductionRate); // in case deductionRate is greater than 1
                    placeholders.put("deduction", String.format("%.2f", deduction));
                    econ.withdrawPlayer(player, deduction);
                    if (taxAccount) econ.depositPlayer(taxAccountName, deduction);
                    sendMessage(sender, "messages.offline_moderate", placeholders, log);
                } else {
                    sendMessage(sender, "messages.offline_active", placeholders, false);
                }
            } else {
                double deduction = min(balance, balance * deductionRate); // in case deductionRate is greater than 1
                placeholders.put("deduction", String.format("%.2f", deduction));
                econ.withdrawPlayer(player, deduction);
                if (taxAccount) econ.depositPlayer(taxAccountName, deduction);
                sendMessage(sender, "messages.deduction_made", placeholders, log);
            }
        } else {
            sendMessage(sender, "messages.zero_balance", placeholders, log);
        }

        double newBalance = econ.getBalance(player);
        double deduction = oldBalance - newBalance;
        saveRecord(player, oldBalance, newBalance, deduction, isCheckAll, operationId);
    }

    private void sendMessage(CommandSender sender, String path, Map<String, String> placeholders, boolean isLog) {
        String message = getFormattedMessage(path, placeholders);
        if (sender != null) for (String str : message.split("\n")) sender.sendMessage(str);
        if (isLog && getConfig().getBoolean("file-logging", true) && fileHandler != null) {
            for (String str : message.split("\n")) fileLogger.info(str);
        }
    }

    private long calculateNextDelay() {
        Calendar now = Calendar.getInstance();

        // 选择最近的一个执行时间
        switch (scheduleType) {
            case "daily":
                return calculateDelayForDaily(now);
            case "weekly":
                return calculateDelayForWeekly(now);
            case "monthly":
                return calculateDelayForMonthly(now);
            default:
                return calculateDelayForDaily(now);
        }
    }

    private long calculateDelayForDaily(Calendar now) {
        int hourOfDay = Integer.parseInt(checkTime.split(":")[0]);
        int minute = Integer.parseInt(checkTime.split(":")[1]);
        return calculateDelayForDaily(now, hourOfDay, minute);
    }
    private long calculateDelayForDaily(Calendar now, int hours, int minutes) {

        Calendar nextCheck = (Calendar) now.clone();
        nextCheck.set(Calendar.HOUR_OF_DAY, hours);
        nextCheck.set(Calendar.MINUTE, minutes);
        nextCheck.set(Calendar.SECOND, 0);
        nextCheck.set(Calendar.MILLISECOND, 0);

        // 如果下一个检查时间在现在之前，添加一天
        if (nextCheck.before(now)) {
            nextCheck.add(Calendar.DAY_OF_MONTH, 1);
        }

        return (nextCheck.getTimeInMillis() - now.getTimeInMillis()) / 50; // 返回ticks
    }

    private long calculateDelayForWeekly(Calendar now) {
        int today = now.get(Calendar.DAY_OF_WEEK);
        if (scheduleDaysOfWeek.contains(today)) {
            // 如果今天是执行日，检查当前时间是否已过计划执行时间
            long delayForToday = calculateDelayForDaily(now);
            if (delayForToday > 0) {
                // 如果还没到计划时间，返回今天的延迟
                return delayForToday;
            }
        }

        int daysUntilNextCheck = scheduleDaysOfWeek.stream()
                .sorted()
                .filter(dayOfWeek -> dayOfWeek > today)
                .map(dayOfWeek -> dayOfWeek - today)
                .findFirst()
                .orElse(7 + scheduleDaysOfWeek.get(0) - today);

        int hourOfDay = Integer.parseInt(checkTime.split(":")[0]);
        int minute = Integer.parseInt(checkTime.split(":")[1]);

        Calendar nextCheck = (Calendar) now.clone();
        nextCheck.add(Calendar.DAY_OF_WEEK, daysUntilNextCheck);
        nextCheck.set(Calendar.HOUR_OF_DAY, hourOfDay);
        nextCheck.set(Calendar.MINUTE, minute);
        nextCheck.set(Calendar.SECOND, 0);
        nextCheck.set(Calendar.MILLISECOND, 0);

        return (nextCheck.getTimeInMillis() - now.getTimeInMillis()) / 50; // 返回ticks
    }

    private long calculateDelayForMonthly(Calendar now) {
        int dayOfMonth = now.get(Calendar.DAY_OF_MONTH);
        if (scheduleDatesOfMonth.contains(dayOfMonth)) {
            // 如果今天是执行日，检查当前时间是否已过计划执行时间
            long delayForToday = calculateDelayForDaily(now);
            if (delayForToday > 0) {
                // 如果还没到计划时间，返回今天的延迟
                return delayForToday;
            }
        }
        int daysUntilNextCheck = scheduleDatesOfMonth.stream()
                .filter(date -> date > dayOfMonth)
                .map(date -> date - dayOfMonth)
                .findFirst()
                .orElse(scheduleDatesOfMonth.get(0) + now.getActualMaximum(Calendar.DAY_OF_MONTH) - dayOfMonth);

        int hourOfDay = Integer.parseInt(checkTime.split(":")[0]);
        int minute = Integer.parseInt(checkTime.split(":")[1]);

        Calendar nextCheck = (Calendar) now.clone();
        nextCheck.add(Calendar.DAY_OF_MONTH, daysUntilNextCheck);
        nextCheck.set(Calendar.HOUR_OF_DAY, hourOfDay);
        nextCheck.set(Calendar.MINUTE, minute);
        nextCheck.set(Calendar.SECOND, 0);
        nextCheck.set(Calendar.MILLISECOND, 0);

        return (nextCheck.getTimeInMillis() - now.getTimeInMillis()) / 50; // 返回ticks
    }


    private void scheduleCheck(long delay) {
        SchedulerUtils.runTaskLater(this, () -> {
            checkAll(null); // 运行任务

            // 任务完成后，计划下一个任务
            scheduleCheck(calculateNextDelay());
        }, delay);
    }

    public void checkPlayer(CommandSender sender, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.hasPlayedBefore()) {
            long currentTime = System.currentTimeMillis();
            final int operationId = getNextOperationId(false);  // false for checkPlayer
            checkBalance(sender, currentTime, target, true, false, operationId);
        } else {
            sender.sendMessage(getFormattedMessage("messages.player_not_found", null));
        }
    }

    public void checkAll(CommandSender sender) {
        final long currentTime = System.currentTimeMillis();
        final OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        final int batchSize = 100; // Number of players to process at once
        final int delay = 10; // Delay in ticks between batches (20 ticks = 1 second)

        final int operationId = getNextOperationId(true);

        class BatchRunnable implements Runnable {
            private int index = 0;

            @Override
            public void run() {
                int start = index;
                int end = Math.min(index + batchSize, players.length);
                for (int i = index; i < end; i++) {
                    OfflinePlayer player = players[i];
                    checkBalance(null, currentTime, player, false, true, operationId);
                }
                index += batchSize;

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("start", Integer.toString(start));
                placeholders.put("end", Integer.toString(end));
                placeholders.put("batch", Integer.toString(end - start));
                placeholders.put("total_players", Integer.toString(players.length));

                sendMessage(sender, "messages.players_processing", placeholders, true);
                if (index < players.length) {
                    // Schedule next batch
                    SchedulerUtils.runTaskLaterAsync(EcoBalancer.this, this, delay);
                } else {
                    // All players have been processed, notify the sender
                    // Send a message to the sender after each batch
                    calculateTotalDeduction(operationId);
                    SchedulerUtils.runTask(EcoBalancer.this, () -> {
                        sendMessage(sender, "messages.all_players_processed", null, true);
                    });
                }
            }
        }

        // Start the first batch
        SchedulerUtils.runTaskAsync(this, new BatchRunnable());
    }

    private void calculateTotalDeduction(int operationId) {
        double totalDeduction = DatabaseUtils.calculateTotalDeduction(this, operationId, getLogger());
        getLogger().info("Operation " + operationId + " total deduction: " + totalDeduction);
    }

    private int getNextOperationId(boolean isCheckAll) {
        return DatabaseUtils.getNextOperationId(this, isCheckAll, getLogger());
    }

    public void generateHistogram(CommandSender sender, int numBars, double low, double up) {

        sender.sendMessage(getFormattedMessage("messages.stats_hist_drawing", null));
        OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        List<Double> balances = new ArrayList<>();

        for (OfflinePlayer player : players) {
            if (econ.hasAccount(player)) {
                double balance = econ.getBalance(player);
                if (balance >= low && balance <= up) {
                    balances.add(balance);
                }
            }
        }

        double min = balances.stream().min(Double::compareTo).orElse(0.0);
        double max = balances.stream().max(Double::compareTo).orElse(0.0);
        double range = max - min;
        double barWidth = range / numBars;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("min", String.format("%.2f", min));
        placeholders.put("max", String.format("%.2f", max));
        sender.sendMessage(getFormattedMessage("messages.stats_min_max", placeholders));

        int[] histogram = new int[numBars];
        for (double balance : balances) {
            int barIndex = (int) ((balance - min) / barWidth);
            if (barIndex == numBars) {
                barIndex--;
            }
            histogram[barIndex]++;
        }

        int maxBarLength = 100; // 可以根据需要调整这个值
        int maxFrequency = Arrays.stream(histogram).max().orElse(0);

        sender.sendMessage(getFormattedMessage("messages.stats_hist_header", null));
        for (int i = 0; i < numBars; i++) {
            double lowerBound = min + i * barWidth;
            double upperBound = lowerBound + barWidth;
            int barLength = (int) (((double) histogram[i] / maxFrequency) * maxBarLength);
            String bar = "§a" + StringUtils.repeat("▏", barLength) + "§r";

            // 创建一个TextComponent作为可点击的条

            Map<String, String> intervalPlaceholders = new HashMap<>();
            intervalPlaceholders.put("bar", bar);
            intervalPlaceholders.put("frequency", Integer.toString(histogram[i]));
            intervalPlaceholders.put("low", formatNumber(lowerBound));
            intervalPlaceholders.put("up", formatNumber(upperBound));

            TextComponent clickableBar = new TextComponent(bar);
            clickableBar.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/interval balance " + lowerBound + " " + upperBound));
            clickableBar.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(getFormattedMessage("messages.stats_check_interval", intervalPlaceholders)).create()));

            TextComponent message = getFormattedMessage("messages.stats_bar", intervalPlaceholders, new String[]{"bar"}, new TextComponent[]{clickableBar});
            sender.spigot().sendMessage(message);
        }

        // Calculate and print additional statistics
        double mean = balances.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double median = calculateMedian(balances);
        double standardDeviation = calculateStandardDeviation(balances, mean);

        Map<String, String> statsPlaceholders = new HashMap<>();
        statsPlaceholders.put("mean", String.format("%.2f", mean));
        statsPlaceholders.put("median", String.format("%.2f", median));
        statsPlaceholders.put("sd", String.format("%.2f", standardDeviation));
        sender.sendMessage(getFormattedMessage("messages.stats_mean_median", statsPlaceholders));
        sender.sendMessage(getFormattedMessage("messages.stats_sd", statsPlaceholders));
    }

    private double calculateMedian(List<Double> values) {
        Collections.sort(values);
        int size = values.size();
        if (size == 0) {
            return 0;
        } else if (size % 2 == 0) {
            return (values.get(size / 2 - 1) + values.get(size / 2)) / 2;
        } else {
            return values.get(size / 2);
        }
    }

    private double calculateStandardDeviation(List<Double> values, double mean) {
        if (values.size() == 0) {
            return 0;
        }
        double sum = 0;
        for (double value : values) {
            sum += Math.pow(value - mean, 2);
        }
        return Math.sqrt(sum / values.size());
    }

    private String formatNumber(double number) {
        if (number >= 1000000000) {
            return String.format("%.1fb", number / 1000000000);
        } else if (number >= 1000000) {
            return String.format("%.1fm", number / 1000000);
        } else if (number >= 1000) {
            return String.format("%.1fk", number / 1000);
        } else {
            return String.format("%.1f", number);
        }
    }

    public double calculatePercentile(double balance, double low, double high) {
        OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        List<Double> balances = new ArrayList<>();

        for (OfflinePlayer player : players) {
            if (econ.hasAccount(player)) {
                double playerBalance = econ.getBalance(player);
                if (playerBalance >= low && playerBalance <= high) {
                    balances.add(playerBalance);
                }
            }
        }

        int totalPlayers = balances.size();
        int playersBelow = (int) balances.stream().filter(b -> b < balance).count();

        return (double) playersBelow / totalPlayers * 100;
    }

    private void saveRecord(OfflinePlayer player, double oldBalance, double newBalance, double deduction, boolean isCheckAll, int operationId) {
        // 在异步线程写库，避免阻塞主线程
        SchedulerUtils.runTaskAsync(this, () -> DatabaseUtils.saveRecord(this, player, oldBalance, newBalance, deduction, isCheckAll, operationId, getLogger()));
    }

    private void cleanupRecords() {
        DatabaseUtils.cleanupRecords(this, recordRetentionDays, getLogger());
    }

    /**
     * 检查是否运行在Folia服务器上
     */
    private boolean checkFoliaSupport() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // Collect all player balances (offline + online) via Vault
    private List<Double> collectAllBalances() {
        List<Double> balances = new ArrayList<>();
        try {
            OfflinePlayer[] players = Bukkit.getOfflinePlayers();
            for (OfflinePlayer player : players) {
                try {
                    if (econ != null && econ.hasAccount(player)) {
                        double bal = econ.getBalance(player);
                        balances.add(bal);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return balances;
    }

    // Nearest-rank percentile (0-100) -> value in sorted list
    private double getPercentileValue(List<Double> sortedValues, double percentile) {
        if (sortedValues == null || sortedValues.isEmpty()) return 0.0;
        if (percentile <= 0) return sortedValues.get(0);
        if (percentile >= 100) return sortedValues.get(sortedValues.size() - 1);
        int n = sortedValues.size();
        // nearest-rank: rank = ceil(p/100 * n), 1-indexed
        int rank = (int) Math.ceil((percentile / 100.0) * n);
        rank = Math.max(1, Math.min(rank, n));
        return sortedValues.get(rank - 1);
    }
}
