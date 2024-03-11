package org.cubexmc.ecobalancer;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
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
import java.sql.*;
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

public final class EcoBalancer extends JavaPlugin {
    private static Economy econ = null;
    private static Chat chat = null;
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

        // 建立数据库连接
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath())) {
            // 检查 'operations' 表是否存在,如果不存在则创建它
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS operations (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, is_checkall BOOLEAN NOT NULL, is_restored BOOLEAN NOT NULL)");
            }

            // 检查 'records' 表是否存在,如果不存在则创建它
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS records (id INTEGER PRIMARY KEY AUTOINCREMENT, player_name TEXT NOT NULL, player TEXT NOT NULL, old_balance REAL NOT NULL, new_balance REAL NOT NULL, deduction REAL NOT NULL, timestamp INTEGER NOT NULL, is_checkall BOOLEAN NOT NULL, operation_id INTEGER NOT NULL)");
            }
        } catch (SQLException e) {
            getLogger().severe("检查或创建数据库表时出错: " + e.getMessage());
        }

        long initialDelay = calculateDelayForDaily(Calendar.getInstance(), 0, 0); // 在每天的午夜12点运行
        long cleanupPeriod = 24 * 60 * 60 * 20; // 24小时(以tick为单位)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::cleanupRecords, initialDelay, cleanupPeriod);

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
            fileLogger.setUseParentHandlers(false);
        } catch (IOException e) {
            getLogger().severe("Could not create the log file handler for EcoBalancer.");
            e.printStackTrace();
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
        getCommand("ecobal").setExecutor(new UtilCommand(this));
        getCommand("checkall").setExecutor(new CheckAllCommand(this));
        getCommand("checkplayer").setExecutor(new CheckPlayerCommand(this));
        getCommand("stats").setExecutor(new DescripStatsCommand(this));
        getCommand("perc").setExecutor(new PercentileCommand(this));
        getCommand("checkrecords").setExecutor(new CheckRecordsCommand(this));
        getCommand("checkrecord").setExecutor(new CheckRecordCommand(this));
        getCommand("restore").setExecutor(new RestoreCommand(this));
        displayAsciiArt();
        getLogger().info("EcoBalancer enabled!");
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
        final String ANSI_GREEN = "\u001B[32m";
        final String ANSI_CYAN = "\u001B[36m";
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
        Bukkit.getScheduler().cancelTasks(this);
        // load language config
        loadLangFile();
        messagePrefix = langConfig.getString("prefix", "&7[&6EcoBalancer&7]&r");
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

        for (Map<?, ?> bracket : rawTaxBrackets) {
            Integer threshold = bracket.get("threshold") == null ? Integer.MAX_VALUE : (Integer) bracket.get("threshold");
            Double rate = ((Number) bracket.get("rate")).doubleValue();
            taxBrackets.put(threshold, rate);
        }
    }

    private void loadLangFile() {
        // Load the language file based on config
        String lang = getConfig().getString("lang", "zh_CN");
        File langFile = new File(getDataFolder(), "lang" + File.separator + lang + ".yml");
        if (!langFile.exists()) {
            saveResource("lang/zh_CN.yml", false);
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
        if (isLog) for (String str : message.split("\n")) fileLogger.info(str);
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
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
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
                    Bukkit.getScheduler().runTaskLaterAsynchronously(EcoBalancer.this, this, delay);
                } else {
                    // All players have been processed, notify the sender
                    // Send a message to the sender after each batch
                    calculateTotalDeduction(operationId);
                    Bukkit.getScheduler().runTask(EcoBalancer.this, () -> {
                        sendMessage(sender, "messages.all_players_processed", null, true);
                    });
                }
            }
        }

        // Start the first batch
        Bukkit.getScheduler().runTaskAsynchronously(this, new BatchRunnable());
    }

    private void calculateTotalDeduction(int operationId) {
        // 获取数据库文件路径
        File dataFolder = getDataFolder();
        File databaseFile = new File(dataFolder, "records.db");

        // 建立数据库连接
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath())) {
            // 查询该操作ID的所有记录,并计算扣除金额的总和
            try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT SUM(deduction) AS total_deduction FROM records WHERE operation_id = ?")) {
                preparedStatement.setInt(1, operationId);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        double totalDeduction = resultSet.getDouble("total_deduction");
                        // 在这里,你可以选择将总扣除金额记录到数据库的另一个表中,或者简单地记录到日志文件中
                        getLogger().info("Operation " + operationId + " total deduction: " + totalDeduction);
                    }
                }
            }
        } catch (SQLException e) {
            getLogger().severe("无法计算总扣除金额: " + e.getMessage());
        }
    }

    private int getNextOperationId(boolean isCheckAll) {
        // 获取数据库文件路径
        File dataFolder = getDataFolder();
        File databaseFile = new File(dataFolder, "records.db");

        // 建立数据库连接
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath())) {
            // 如果表不存在,则创建表
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS operations (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, is_checkall BOOLEAN NOT NULL, is_restored BOOLEAN NOT NULL DEFAULT 0)");
            }

            // 插入一个新的操作记录
            try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO operations (timestamp, is_checkall, is_restored) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                preparedStatement.setLong(1, System.currentTimeMillis());
                preparedStatement.setBoolean(2, isCheckAll);
                preparedStatement.setBoolean(3, false);
                preparedStatement.executeUpdate();

                // 获取新插入的操作的ID
                try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Creating operation failed, no ID obtained.");
                    }
                }
            }
        } catch (SQLException e) {
            getLogger().severe("无法获取下一个操作ID: " + e.getMessage());
            return -1;  // 返回一个无效的操作ID表示出错
        }
    }

    public void generateHistogram(CommandSender sender, int numBars, double low, double high) {

        sender.sendMessage(getFormattedMessage("messages.stats_hist_drawing", null));
        OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        List<Double> balances = new ArrayList<>();

        for (OfflinePlayer player : players) {
            if (econ.hasAccount(player)) {
                double balance = econ.getBalance(player);
                if (balance >= low && balance <= high) {
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

        int maxBarLength = 50; // 可以根据需要调整这个值
        int maxFrequency = Arrays.stream(histogram).max().orElse(0);

        sender.sendMessage(getFormattedMessage("messages.stats_hist_header", null));
        for (int i = 0; i < numBars; i++) {
            double lowerBound = min + i * barWidth;
            double upperBound = lowerBound + barWidth;
            int barLength = (int) (((double) histogram[i] / maxFrequency) * maxBarLength);
            String bar = "§a" + StringUtils.repeat("▏", barLength) + "§r";
            String interval = "§7(" + formatNumber(lowerBound) + " - " + formatNumber(upperBound) + ")§r";
            sender.sendMessage(String.format("%s §e%d p§r %s", bar, histogram[i], interval));
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
        if (size % 2 == 0) {
            return (values.get(size / 2 - 1) + values.get(size / 2)) / 2;
        } else {
            return values.get(size / 2);
        }
    }

    private double calculateStandardDeviation(List<Double> values, double mean) {
        double sum = 0;
        for (double value : values) {
            sum += Math.pow(value - mean, 2);
        }
        return Math.sqrt(sum / values.size());
    }

    private double calculateMode(List<Double> values) {
        Map<Double, Integer> frequencyMap = new HashMap<>();
        int maxFrequency = 0;
        double mode = 0;

        for (double value : values) {
            int frequency = frequencyMap.getOrDefault(value, 0) + 1;
            frequencyMap.put(value, frequency);
            if (frequency > maxFrequency) {
                maxFrequency = frequency;
                mode = value;
            }
        }

        return mode;
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
        // 获取数据库文件路径
        File dataFolder = getDataFolder();
        File databaseFile = new File(dataFolder, "records.db");

        // 建立数据库连接
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath())) {
            // 如果表不存在,则创建表
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS records (id INTEGER PRIMARY KEY AUTOINCREMENT, player_name TEXT NOT NULL, player TEXT NOT NULL, old_balance REAL NOT NULL, new_balance REAL NOT NULL, deduction REAL NOT NULL, timestamp INTEGER NOT NULL, is_checkall BOOLEAN NOT NULL, operation_id INTEGER NOT NULL)");
            }

            // 插入记录
            try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO records (operation_id, player_name, player, old_balance, new_balance, deduction, timestamp, is_checkall) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                preparedStatement.setInt(1, operationId);
                preparedStatement.setString(2, player.getName());
                preparedStatement.setString(3, player.getUniqueId().toString());
                preparedStatement.setDouble(4, oldBalance);
                preparedStatement.setDouble(5, newBalance);
                preparedStatement.setDouble(6, deduction);
                preparedStatement.setLong(7, System.currentTimeMillis());
                preparedStatement.setBoolean(8, isCheckAll);
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            getLogger().severe(getFormattedMessage("messages.sql_save_error", null) + e.getMessage());
        }
    }

    private void cleanupRecords() {
        // 计算过期时间
        long expirationTime = System.currentTimeMillis() - recordRetentionDays * 24 * 60 * 60 * 1000;

        // 获取数据库文件路径
        File dataFolder = getDataFolder();
        File databaseFile = new File(dataFolder, "records.db");

        // 建立数据库连接
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath())) {
            // 首先,我们需要找到所有过期的操作ID
            try (PreparedStatement selectExpiredOperations = connection.prepareStatement("SELECT id FROM operations WHERE timestamp < ?")) {
                selectExpiredOperations.setLong(1, expirationTime);
                try (ResultSet expiredOperations = selectExpiredOperations.executeQuery()) {
                    while (expiredOperations.next()) {
                        int operationId = expiredOperations.getInt("id");

                        // 删除records表中所有与该操作ID相关的记录
                        try (PreparedStatement deleteRecords = connection.prepareStatement("DELETE FROM records WHERE operation_id = ?")) {
                            deleteRecords.setInt(1, operationId);
                            deleteRecords.executeUpdate();
                        }

                        // 删除operations表中的该操作记录
                        try (PreparedStatement deleteOperation = connection.prepareStatement("DELETE FROM operations WHERE id = ?")) {
                            deleteOperation.setInt(1, operationId);
                            deleteOperation.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            getLogger().severe(getFormattedMessage("messages.sql_clean_error", null) + e.getMessage());
        }
    }
}
