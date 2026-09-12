package org.cubexmc.ecobalancer.tax

import org.bukkit.OfflinePlayer
import org.cubexmc.economy.EconomyAccount
import org.cubexmc.economy.VaultEconomy
import java.math.BigDecimal

/**
 * 税款的收取与去处：从玩家身上扣走税金，并转进税金账户。
 *
 * 接入 `cubex-economy` 之前，这两步是 `withdrawPlayer` + `depositPlayer` 两次裸调用，
 * **两次都不看返回值** —— 经济插件拒绝扣款时（余额被别的插件锁住、账户只读、提供方返回 null），
 * 账本照样记一笔并不存在的税：玩家的 `total_tax_paid` 与服务器的 `tax_fund_balance` 一起虚高，
 * 而且**没有任何日志**能让服主发现。现在扣款失败会如实报告（[Outcome.collected] = false），
 * 调用方据此把这一笔记成 0。
 *
 * 配置键**没有改**：仍是 `tax-account`（开关）+ `tax-account-name`（账户名），
 * 由 [useAccount] 翻译成模块的 [EconomyAccount]。名字原样交给 Vault 的 name 重载，
 * 与迁移前 `depositPlayer(String, Double)` 是同一个调用 —— 税金账户从不登录，这条路最短。
 */
class TaxTreasury(private val economy: VaultEconomy) {

    /**
     * 设置税金去处。enable、reload 与 `/ecobal tax account ...` 之后各调一次，
     * **不要**放进每个玩家的扣税循环：解析账户要问一次经济插件。
     *
     * @param enabled `tax-account`
     * @param accountName `tax-account-name`
     * @return 配置能不能用；`false` 表示名字非法（已按"不入账"降级，钱会被销毁）
     */
    fun useAccount(enabled: Boolean, accountName: String?): Boolean {
        if (!enabled || accountName.isNullOrBlank()) {
            economy.useAccount(EconomyAccount.None)
            return true
        }
        val account = try {
            // 走 parse 而不是直接 new：账户名的合法性校验只此一份,和别的插件同规则。
            EconomyAccount.parse(EconomyAccount.NAME_PREFIX + accountName)
        } catch (ex: IllegalArgumentException) {
            economy.useAccount(EconomyAccount.None)
            return false
        }
        economy.useAccount(account)
        return true
    }

    /** 当前税金去处的人类可读描述，用于启动日志与 `/ecobal tax status`。 */
    fun accountDescription(): String = economy.accountDescription()

    /** 交给经济插件格式化金额（日志用）。 */
    fun format(amount: Double): String = economy.format(BigDecimal.valueOf(amount))

    /**
     * 从 [player] 扣走 [amount] 并转进税金账户。
     *
     * @return [Outcome.collected] = 玩家那一侧扣到没有；[Outcome.banked] = 税金账户收到没有
     */
    fun collect(player: OfflinePlayer, amount: Double): Outcome {
        if (amount <= 0.0) {
            return Outcome(collected = false, banked = false, reason = "nothing to collect")
        }
        val result = economy.charge(player, BigDecimal.valueOf(amount))
        if (!result.success()) {
            return Outcome(collected = false, banked = false, reason = result.reason())
        }
        // 扣款成功、入账失败：钱确实从玩家身上走了,所以账本照记 —— 玩家付过了。
        // 少掉的是税金账户那一侧,模块已经记了 WARNING 留痕,服主据此对账。
        return Outcome(collected = true, banked = !result.depositFailed(), reason = result.reason())
    }

    /**
     * 把钱还给玩家：负余额修复走这条路，不是退税。
     *
     * @return 到账没有；`false` 时调用方应当留一条日志，否则又是一次静默失败
     */
    fun repay(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0.0) return true
        return economy.deposit(player, BigDecimal.valueOf(amount)).success()
    }

    /** 一次收税的结果。 */
    class Outcome(
        /** 玩家那一侧扣到了没有。`false` 时这笔税**不该进账本**。 */
        @JvmField val collected: Boolean,
        /** 税金账户收到了没有。`collected && !banked` = 钱消失了，需要人工核账。 */
        @JvmField val banked: Boolean,
        /** 经济插件给的失败原因；成功时为空串。 */
        @JvmField val reason: String,
    )
}
