package org.cubexmc.ecobalancer.utils

import net.milkbowl.vault.economy.Economy
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin

/**
 * Vault 的只读查询（余额、账户是否存在），供统计与筛选使用。
 *
 * **税金账户的入账不在这里** —— 那条路由已经交给 `cubex-economy` 的
 * [org.cubexmc.economy.VaultEconomy]（见 `TaxTreasury`），本对象曾经有过的
 * `setupTaxAccount` / `getTaxAccountBalance` / `depositToTaxAccount` 是没人调用的死代码，
 * 已随迁移删除；不要再往这里加第二份入账实现。
 */
object VaultUtils {
    private var economy: Economy? = null

    @JvmStatic
    fun setupEconomy(plugin: JavaPlugin): Boolean {
        if (plugin.server.pluginManager.getPlugin("Vault") == null) {
            plugin.logger.severe("未找到Vault插件，经济系统无法初始化")
            return false
        }

        val rsp = plugin.server.servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            plugin.logger.severe("未找到Vault经济服务提供者")
            return false
        }

        economy = rsp.provider
        return economy != null
    }

    @JvmStatic
    fun getEconomy(): Economy? = economy

    @JvmStatic
    fun hasAccount(player: OfflinePlayer): Boolean = requireEconomy().hasAccount(player)

    @JvmStatic
    fun getBalance(player: OfflinePlayer): Double = requireEconomy().getBalance(player)

    @JvmStatic
    fun depositPlayer(player: OfflinePlayer, amount: Double): Boolean =
        requireEconomy().depositPlayer(player, amount).transactionSuccess()

    @JvmStatic
    fun withdrawPlayer(player: OfflinePlayer, amount: Double): Boolean =
        requireEconomy().withdrawPlayer(player, amount).transactionSuccess()

    private fun requireEconomy(): Economy =
        economy ?: throw IllegalStateException("Vault经济系统未初始化")
}
