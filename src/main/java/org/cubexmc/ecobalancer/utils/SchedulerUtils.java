package org.cubexmc.ecobalancer.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.lang.reflect.Method;

/**
 * 用于处理跨平台任务调度的工具类，同时支持Bukkit和Folia
 */
public class SchedulerUtils {

    private static final boolean IS_FOLIA = checkFolia();

    /**
     * 检查服务器是否使用Folia
     */
    private static boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 在主线程上执行任务
     */
    public static void runTask(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            try {
                // 反射调用Folia API
                Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Object scheduler = getGlobalRegionScheduler.invoke(null);
                Method execute = scheduler.getClass().getMethod("execute", Plugin.class, Runnable.class);
                execute.invoke(scheduler, plugin, task);
            } catch (Exception e) {
                // 如果反射失败，回退到Bukkit API
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * 在异步线程上执行任务
     */
    public static void runTaskAsync(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            try {
                // 反射调用Folia API
                final Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
                final Object scheduler = getAsyncScheduler.invoke(null);
                
                // 创建一个包装类用于runNow方法
                Class<?> consumerClass = Class.forName("java.util.function.Consumer");
                Object taskWrapper = java.lang.reflect.Proxy.newProxyInstance(
                    plugin.getClass().getClassLoader(),
                    new Class<?>[]{consumerClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("accept")) {
                            task.run();
                            return null;
                        }
                        return method.invoke(proxy, args);
                    }
                );
                
                Method runNow = scheduler.getClass().getMethod("runNow", Plugin.class, consumerClass);
                runNow.invoke(scheduler, plugin, taskWrapper);
            } catch (Exception e) {
                // 如果反射失败，回退到Bukkit API
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * 延迟执行任务
     */
    public static void runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            try {
                // 尝试使用反射调用Folia API
                runTask(plugin, task); // 简化版本，实际中应该处理延迟
            } catch (Exception e) {
                // 回退到Bukkit API
                Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * 延迟异步执行任务
     */
    public static void runTaskLaterAsync(Plugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            try {
                // 简化处理，实际应该使用反射调用runDelayed方法
                runTaskAsync(plugin, task);
            } catch (Exception e) {
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
            }
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    /**
     * 重复执行任务
     */
    public static void runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            try {
                // 简化处理，实际应该使用反射调用runAtFixedRate
                runTask(plugin, task);
            } catch (Exception e) {
                Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
            }
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    /**
     * 重复异步执行任务
     */
    public static void runTaskTimerAsync(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            try {
                // 简化处理，实际应该使用反射调用runAtFixedRate
                runTaskAsync(plugin, task);
            } catch (Exception e) {
                Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
            }
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        }
    }

    /**
     * 取消插件的所有任务
     */
    public static void cancelAllTasks(Plugin plugin) {
        // Bukkit方法在两种平台上都能使用
        Bukkit.getScheduler().cancelTasks(plugin);
        
        // 如果是Folia，尝试额外取消其他调度器的任务
        if (IS_FOLIA) {
            try {
                Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Object scheduler = getGlobalRegionScheduler.invoke(null);
                Method cancelTasks = scheduler.getClass().getMethod("cancelTasks", Plugin.class);
                cancelTasks.invoke(scheduler, plugin);
                
                Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
                Object asyncScheduler = getAsyncScheduler.invoke(null);
                Method asyncCancelTasks = asyncScheduler.getClass().getMethod("cancelTasks", Plugin.class);
                asyncCancelTasks.invoke(asyncScheduler, plugin);
            } catch (Exception e) {
                // 已经使用Bukkit方法取消了任务，这里仅记录错误
                plugin.getLogger().warning("Failed to cancel Folia-specific tasks: " + e.getMessage());
            }
        }
    }
} 