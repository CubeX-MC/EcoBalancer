package org.cubexmc.ecobalancer.utils;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 数据库操作工具类
 */
public class DatabaseUtils {
    private static File getDatabaseFile(Plugin plugin) {
        File dataFolder = plugin.getDataFolder();
        return new File(dataFolder, "records.db");
    }

    /**
     * 获取数据库连接
     * @param plugin 插件实例
     * @return 数据库连接
     * @throws SQLException 连接异常
     */
    public static Connection getConnection(Plugin plugin) throws SQLException {
        File databaseFile = getDatabaseFile(plugin);
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }

    /**
     * 初始化数据库表
     * @param plugin 插件实例
     * @param logger 日志器
     */
    public static void initializeTables(Plugin plugin, Logger logger) {
        try (Connection connection = getConnection(plugin)) {
            try (Statement statement = connection.createStatement()) {
                // 创建operations表
                statement.execute("CREATE TABLE IF NOT EXISTS operations (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, is_checkall BOOLEAN NOT NULL, is_restored BOOLEAN NOT NULL DEFAULT 0)");
                
                // 创建records表
                statement.execute("CREATE TABLE IF NOT EXISTS records (id INTEGER PRIMARY KEY AUTOINCREMENT, player_name TEXT NOT NULL, player TEXT NOT NULL, old_balance REAL NOT NULL, new_balance REAL NOT NULL, deduction REAL NOT NULL, timestamp INTEGER NOT NULL, is_checkall BOOLEAN NOT NULL, operation_id INTEGER NOT NULL)");
            }
        } catch (SQLException e) {
            logger.severe("初始化数据库表失败: " + e.getMessage());
        }
    }

    /**
     * 保存玩家余额变更记录
     * @param plugin 插件实例
     * @param player 玩家
     * @param oldBalance 旧余额
     * @param newBalance 新余额
     * @param deduction 扣除金额
     * @param isCheckAll 是否检查所有玩家
     * @param operationId 操作ID
     * @param logger 日志器
     */
    public static void saveRecord(Plugin plugin, OfflinePlayer player, double oldBalance, double newBalance, double deduction, boolean isCheckAll, int operationId, Logger logger) {
        try (Connection connection = getConnection(plugin)) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "INSERT INTO records (player_name, player, old_balance, new_balance, deduction, timestamp, is_checkall, operation_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                preparedStatement.setString(1, player.getName());
                preparedStatement.setString(2, player.getUniqueId().toString());
                preparedStatement.setDouble(3, oldBalance);
                preparedStatement.setDouble(4, newBalance);
                preparedStatement.setDouble(5, deduction);
                preparedStatement.setLong(6, System.currentTimeMillis());
                preparedStatement.setBoolean(7, isCheckAll);
                preparedStatement.setInt(8, operationId);
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("保存记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取下一个操作ID
     * @param plugin 插件实例
     * @param isCheckAll 是否检查所有玩家
     * @param logger 日志器
     * @return 操作ID
     */
    public static int getNextOperationId(Plugin plugin, boolean isCheckAll, Logger logger) {
        try (Connection connection = getConnection(plugin)) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "INSERT INTO operations (timestamp, is_checkall, is_restored) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                preparedStatement.setLong(1, System.currentTimeMillis());
                preparedStatement.setBoolean(2, isCheckAll);
                preparedStatement.setBoolean(3, false);
                preparedStatement.executeUpdate();

                try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("创建操作失败，未获取到ID");
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("获取下一个操作ID失败: " + e.getMessage());
            return -1;
        }
    }

    /**
     * 计算操作的总扣除金额
     * @param plugin 插件实例
     * @param operationId 操作ID
     * @param logger 日志器
     * @return 总扣除金额
     */
    public static double calculateTotalDeduction(Plugin plugin, int operationId, Logger logger) {
        try (Connection connection = getConnection(plugin)) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT SUM(deduction) AS total_deduction FROM records WHERE operation_id = ?")) {
                preparedStatement.setInt(1, operationId);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getDouble("total_deduction");
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("计算总扣除金额失败: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 清理过期记录
     * @param plugin 插件实例
     * @param retentionDays 保留天数
     * @param logger 日志器
     */
    public static void cleanupRecords(Plugin plugin, int retentionDays, Logger logger) {
        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
        
        try (Connection connection = getConnection(plugin)) {
            // 删除过期的记录
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "DELETE FROM records WHERE timestamp < ?")) {
                preparedStatement.setLong(1, cutoffTime);
                int recordsDeleted = preparedStatement.executeUpdate();
                logger.info("已清理 " + recordsDeleted + " 条过期记录");
            }
            
            // 删除没有关联记录的操作
            try (Statement statement = connection.createStatement()) {
                int operationsDeleted = statement.executeUpdate(
                        "DELETE FROM operations WHERE id NOT IN (SELECT DISTINCT operation_id FROM records)");
                logger.info("已清理 " + operationsDeleted + " 条无关联记录的操作");
            }
        } catch (SQLException e) {
            logger.severe("清理过期记录失败: " + e.getMessage());
        }
    }
} 