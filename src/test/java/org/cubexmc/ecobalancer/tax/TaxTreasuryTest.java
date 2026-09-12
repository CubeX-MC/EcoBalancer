package org.cubexmc.ecobalancer.tax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.cubexmc.core.CubexLogger;
import org.cubexmc.economy.VaultEconomy;
import org.junit.jupiter.api.Test;

/**
 * The point of these tests is the failure path: before EcoBalancer moved onto
 * {@code cubex-economy}, both Vault calls were fire-and-forget, so a refused
 * withdrawal still produced a ledger entry.
 */
class TaxTreasuryTest {

    private final Economy economy = mock(Economy.class);
    private final OfflinePlayer player = mock(OfflinePlayer.class);

    @Test
    void reportsFailureWhenTheProviderRefusesTheWithdrawal() {
        // Arrange
        TaxTreasury treasury = treasuryWithAccount("tax");
        when(economy.withdrawPlayer(player, 25.0)).thenReturn(failure("account is read-only"));

        // Act
        TaxTreasury.Outcome outcome = treasury.collect(player, 25.0);

        // Assert
        assertFalse(outcome.collected, "a refused withdrawal must not be recorded as collected tax");
        assertFalse(outcome.banked);
        assertEquals("account is read-only", outcome.reason);
        verify(economy, never()).depositPlayer(anyString(), anyDouble());
    }

    @Test
    void treatsANullResponseAsAFailureRatherThanCrashing() {
        // Arrange: Economy is a third-party implementation; null is not hypothetical.
        TaxTreasury treasury = treasuryWithAccount("tax");
        when(economy.withdrawPlayer(player, 10.0)).thenReturn(null);

        // Act
        TaxTreasury.Outcome outcome = treasury.collect(player, 10.0);

        // Assert
        assertFalse(outcome.collected);
    }

    @Test
    void countsTaxAsPaidWhenTheWithdrawalWorkedButTheTaxAccountRefusedTheDeposit() {
        // Arrange
        TaxTreasury treasury = treasuryWithAccount("tax");
        // The module names the payer in its "money is gone" warning, so it reads the identity.
        when(player.getName()).thenReturn("Steve");
        when(economy.withdrawPlayer(player, 25.0)).thenReturn(success());
        when(economy.depositPlayer("tax", 25.0)).thenReturn(failure("tax account is frozen"));

        // Act
        TaxTreasury.Outcome outcome = treasury.collect(player, 25.0);

        // Assert: the player did pay, so the ledger entry is correct; the money that
        // never reached the tax account is what the caller has to log.
        assertTrue(outcome.collected);
        assertFalse(outcome.banked, "the caller must be told the tax account did not receive it");
    }

    @Test
    void routesTheTaxToTheAccountNamedByTaxAccountName() {
        // Arrange
        TaxTreasury treasury = treasuryWithAccount("cubex_bank");
        when(economy.withdrawPlayer(player, 8.5)).thenReturn(success());
        when(economy.depositPlayer("cubex_bank", 8.5)).thenReturn(success());

        // Act
        TaxTreasury.Outcome outcome = treasury.collect(player, 8.5);

        // Assert: the name goes to Vault as-is, exactly like the pre-migration code did.
        assertTrue(outcome.collected);
        assertTrue(outcome.banked);
        verify(economy).depositPlayer("cubex_bank", 8.5);
    }

    @Test
    void destroysTheTaxWhenTheTaxAccountIsTurnedOff() {
        // Arrange: tax-account: false is the documented "money is destroyed" setting.
        TaxTreasury treasury = new TaxTreasury(vaultEconomy());
        assertTrue(treasury.useAccount(false, "tax"));
        when(economy.withdrawPlayer(player, 5.0)).thenReturn(success());

        // Act
        TaxTreasury.Outcome outcome = treasury.collect(player, 5.0);

        // Assert
        assertTrue(outcome.collected);
        assertTrue(outcome.banked, "with no destination there is nothing that can fail to arrive");
        verify(economy, never()).depositPlayer(anyString(), anyDouble());
    }

    @Test
    void rejectsAnUnusableAccountNameInsteadOfGuessingAnAccount() {
        // Arrange
        TaxTreasury treasury = new TaxTreasury(vaultEconomy());

        // Act
        boolean usable = treasury.useAccount(true, "server tax fund");

        // Assert: a name with spaces cannot be a Vault account; degrade to "not banked".
        assertFalse(usable);
        when(economy.withdrawPlayer(player, 3.0)).thenReturn(success());
        assertTrue(treasury.collect(player, 3.0).collected);
        verify(economy, never()).depositPlayer(anyString(), anyDouble());
    }

    private TaxTreasury treasuryWithAccount(String accountName) {
        TaxTreasury treasury = new TaxTreasury(vaultEconomy());
        when(economy.hasAccount(accountName)).thenReturn(true);
        assertTrue(treasury.useAccount(true, accountName));
        return treasury;
    }

    private VaultEconomy vaultEconomy() {
        when(economy.getName()).thenReturn("MockEconomy");
        return new VaultEconomy(economy, new CubexLogger(Logger.getLogger("TaxTreasuryTest")));
    }

    private EconomyResponse success() {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse failure(String error) {
        return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, error);
    }
}
