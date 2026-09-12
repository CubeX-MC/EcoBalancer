package org.cubexmc.ecobalancer.tax

enum class TaxDecisionResult {
    TAXED,
    EXEMPT,
    INSUFFICIENT_BALANCE_SKIPPED,
    DRAINED_TO_ZERO,
    NEGATIVE_BALANCE_FIXED,
    ZERO_DEDUCTION,
    SKIPPED_ONLINE,
    SKIPPED_INACTIVE_DAYS,
    SKIPPED_TAX_ACCOUNT,
    SKIPPED_NO_ACCOUNT,
    CLEARED,

    /**
     * 经济插件拒绝了扣款：这一笔税**没有收到**。
     *
     * 接入 `cubex-economy` 之前这种情况会被记成一笔正常的税（两次 Vault 调用都不看返回值）。
     * 现在它的 `actualDeduction` 为 0，因此**不进账本**（[TaxLedgerService] 只记 > 0 的金额）。
     */
    ECONOMY_FAILED,
}
