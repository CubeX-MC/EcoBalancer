package org.cubexmc.ecobalancer.utils;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Calendar;
import java.util.List;

/**
 * 调度工具类，用于计算定时任务的执行时间
 */
public class ScheduleUtils {
    
    /**
     * 调度类型枚举
     */
    public enum ScheduleType {
        DAILY, WEEKLY, MONTHLY
    }
    
    /**
     * 计算下一次执行延迟
     * @param scheduleType 调度类型
     * @param scheduleDaysOfWeek 每周调度的日期
     * @param scheduleDatesOfMonth 每月调度的日期
     * @param checkTime 检查时间（格式为HH:mm）
     * @return 延迟时间（以tick为单位）
     */
    public static long calculateNextDelay(String scheduleType, List<Integer> scheduleDaysOfWeek, List<Integer> scheduleDatesOfMonth, String checkTime) {
        Calendar now = Calendar.getInstance();
        
        // 选择最近的一个执行时间
        switch (scheduleType.toLowerCase()) {
            case "daily":
                return calculateDelayForDaily(now, checkTime);
            case "weekly":
                return calculateDelayForWeekly(now, scheduleDaysOfWeek, checkTime);
            case "monthly":
                return calculateDelayForMonthly(now, scheduleDatesOfMonth, checkTime);
            default:
                return calculateDelayForDaily(now, checkTime);
        }
    }
    
    /**
     * 计算每日执行的延迟
     * @param now 当前时间
     * @param checkTime 检查时间（格式为HH:mm）
     * @return 延迟时间（以tick为单位）
     */
    public static long calculateDelayForDaily(Calendar now, String checkTime) {
        int hourOfDay = Integer.parseInt(checkTime.split(":")[0]);
        int minute = Integer.parseInt(checkTime.split(":")[1]);
        return calculateDelayForDaily(now, hourOfDay, minute);
    }
    
    /**
     * 计算每日执行的延迟
     * @param now 当前时间
     * @param hours 小时
     * @param minutes 分钟
     * @return 延迟时间（以tick为单位）
     */
    public static long calculateDelayForDaily(Calendar now, int hours, int minutes) {
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
    
    /**
     * 计算每周执行的延迟
     * @param now 当前时间
     * @param daysOfWeek 每周的日期列表
     * @param checkTime 检查时间（格式为HH:mm）
     * @return 延迟时间（以tick为单位）
     */
    public static long calculateDelayForWeekly(Calendar now, List<Integer> daysOfWeek, String checkTime) {
        int today = now.get(Calendar.DAY_OF_WEEK);
        
        if (daysOfWeek.contains(today)) {
            // 如果今天是执行日，检查当前时间是否已过计划执行时间
            long delayForToday = calculateDelayForDaily(now, checkTime);
            if (delayForToday > 0) {
                // 如果还没到计划时间，返回今天的延迟
                return delayForToday;
            }
        }
        
        // 计算到下一个执行日的天数
        int daysUntilNextCheck = daysOfWeek.stream()
                .sorted()
                .filter(dayOfWeek -> dayOfWeek > today)
                .map(dayOfWeek -> dayOfWeek - today)
                .findFirst()
                .orElse(7 + daysOfWeek.get(0) - today); // 如果没有大于当前日期的执行日，则循环到下周的第一个执行日
        
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
    
    /**
     * 计算每月执行的延迟
     * @param now 当前时间
     * @param datesOfMonth 每月的日期列表
     * @param checkTime 检查时间（格式为HH:mm）
     * @return 延迟时间（以tick为单位）
     */
    public static long calculateDelayForMonthly(Calendar now, List<Integer> datesOfMonth, String checkTime) {
        int today = now.get(Calendar.DAY_OF_MONTH);
        
        if (datesOfMonth.contains(today)) {
            // 如果今天是执行日，检查当前时间是否已过计划执行时间
            long delayForToday = calculateDelayForDaily(now, checkTime);
            if (delayForToday > 0) {
                // 如果还没到计划时间，返回今天的延迟
                return delayForToday;
            }
        }
        
        // 计算到下一个执行日的天数
        int nextDate = datesOfMonth.stream()
                .sorted()
                .filter(date -> date > today)
                .findFirst()
                .orElse(datesOfMonth.get(0)); // 如果没有大于当前日期的执行日，则循环到下月的第一个执行日
        
        int hourOfDay = Integer.parseInt(checkTime.split(":")[0]);
        int minute = Integer.parseInt(checkTime.split(":")[1]);
        
        Calendar nextCheck = (Calendar) now.clone();
        
        if (nextDate <= today) {
            // 如果下一个执行日在下个月
            nextCheck.add(Calendar.MONTH, 1);
        }
        
        // 设置到月份的具体日期
        nextCheck.set(Calendar.DAY_OF_MONTH, nextDate);
        nextCheck.set(Calendar.HOUR_OF_DAY, hourOfDay);
        nextCheck.set(Calendar.MINUTE, minute);
        nextCheck.set(Calendar.SECOND, 0);
        nextCheck.set(Calendar.MILLISECOND, 0);
        
        return (nextCheck.getTimeInMillis() - now.getTimeInMillis()) / 50; // 返回ticks
    }
    
    /**
     * 安排定时任务
     * @param plugin 插件实例
     * @param runnable 要执行的任务
     * @param delay 延迟时间（以tick为单位）
     * @return 任务ID
     */
    public static BukkitTask scheduleTask(JavaPlugin plugin, Runnable runnable, long delay) {
        return plugin.getServer().getScheduler().runTaskLater(plugin, runnable, delay);
    }
    
    /**
     * 安排重复定时任务
     * @param plugin 插件实例
     * @param runnable 要执行的任务
     * @param delay 初始延迟（以tick为单位）
     * @param period 周期（以tick为单位）
     * @return 任务ID
     */
    public static BukkitTask scheduleRepeatingTask(JavaPlugin plugin, Runnable runnable, long delay, long period) {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, runnable, delay, period);
    }
    
    /**
     * 安排异步定时任务
     * @param plugin 插件实例
     * @param runnable 要执行的任务
     * @param delay 延迟时间（以tick为单位）
     * @return 任务ID
     */
    public static BukkitTask scheduleAsyncTask(JavaPlugin plugin, Runnable runnable, long delay) {
        return plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, runnable, delay);
    }
} 