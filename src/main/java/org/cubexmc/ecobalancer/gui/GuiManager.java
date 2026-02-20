package org.cubexmc.ecobalancer.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.cubexmc.ecobalancer.EcoBalancer;
import org.cubexmc.ecobalancer.policies.TaxPolicy;
import org.cubexmc.ecobalancer.utils.EconomicMetrics;

import java.util.*;

public class GuiManager implements Listener {
    private final EcoBalancer plugin;

    // Track which policy the player is viewing details for
    private final Map<UUID, String> viewingPolicyDetails = new HashMap<>();

    public GuiManager(EcoBalancer plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // --- Menus ---

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, tr("messages.gui.main_menu_title", "&6EcoBalancer Menu"));

        // Header
        TaxPolicy active = plugin.getPolicyManager().getActivePolicy();
        String activePolicyName = active != null ? active.getName() : "None";
        String scheduleInfo = active != null ? active.getScheduleType() + " @ " + active.getCheckTime() : "N/A";

        inv.setItem(4, createItem(Material.BEACON, "§b§lEcoBalancer",
                "§7Economy management system",
                "",
                "§7Active Policy: §a" + activePolicyName,
                "§7Schedule: §f" + scheduleInfo));

        // Dashboard
        inv.setItem(20, createItem(Material.PAPER, "§eEconomic Dashboard",
                "§7View real-time economic statistics",
                "",
                "§7• Gini Coefficient",
                "§7• Total Money Supply",
                "§7• Player Statistics",
                "",
                "§aClick to open"));

        // Tax Policies
        inv.setItem(22, createItem(Material.GOLD_INGOT, "§eTax Policies",
                "§7Manage and execute tax policies",
                "",
                "§7Current: §a" + activePolicyName,
                "",
                "§aClick to manage",
                "§cRequires: ecobalancer.gui.admin"));

        // Quick Execute
        if (player.hasPermission("ecobalancer.gui.admin")) {
            inv.setItem(24, createItem(Material.REDSTONE, "§c§lExecute Now",
                    "§7Execute active policy immediately",
                    "",
                    "§7Policy: §e" + activePolicyName,
                    "",
                    "§c⚠ Click to execute on all players"));
        } else {
            inv.setItem(24, createItem(Material.BARRIER, "§8Execute Now",
                    "§7Requires admin permission"));
        }

        // Bottom row - Info
        inv.setItem(38, createItem(Material.BOOK, "§7Help",
                "§fUse /eb help for commands"));

        inv.setItem(40, createItem(Material.COMPARATOR, "§7Settings",
                "§fEdit config.yml manually",
                "§fOr use /eb tax commands"));

        inv.setItem(42, createItem(Material.EXPERIENCE_BOTTLE, "§7View Records",
                "§fUse /eb checkrecords",
                "§ffor operation history"));

        fillBackground(inv);
        player.openInventory(inv);
    }

    public void openDashboard(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, tr("messages.gui.dashboard_title", "&6Economic Dashboard"));

        List<Double> balances = plugin.collectAllBalances();
        if (balances == null)
            balances = new ArrayList<>();

        double totalMoney = balances.stream().mapToDouble(Double::doubleValue).sum();
        double gini = 0;
        double mean = 0;
        double median = 0;
        double top1 = 0;
        double top10 = 0;

        try {
            gini = EconomicMetrics.calculateGini(balances);
            mean = EconomicMetrics.calculateMean(balances);
            List<Double> sorted = EconomicMetrics.getSortedBalances(balances);
            median = EconomicMetrics.calculateMedian(sorted);
            top1 = EconomicMetrics.calculateConcentration(balances, 1.0);
            top10 = EconomicMetrics.calculateConcentration(balances, 10.0);
        } catch (Exception e) {
            // Ignore calculation errors
        }

        // Header
        inv.setItem(4, createItem(Material.BEACON, "§b§lEconomic Dashboard",
                "§7Real-time economy status",
                "",
                "§7Total Players: §f" + balances.size()));

        // Row 2 - Main Stats
        inv.setItem(19, createItem(Material.GOLD_BLOCK, "§e§lTotal Supply",
                "§f" + EconomicMetrics.formatLargeNumber(totalMoney),
                "",
                "§7Combined balance of all players"));

        String giniColor = gini < 0.3 ? "§a" : gini < 0.5 ? "§e" : "§c";
        inv.setItem(21, createItem(Material.DIAMOND, "§b§lGini Coefficient",
                giniColor + String.format("%.4f", gini),
                "",
                "§70.0 = Perfect Equality",
                "§71.0 = Maximum Inequality",
                "",
                getGiniDescription(gini)));

        inv.setItem(23, createItem(Material.EMERALD, "§a§lMean Balance",
                "§f" + EconomicMetrics.formatLargeNumber(mean),
                "",
                "§7Average player balance"));

        inv.setItem(25, createItem(Material.HEART_OF_THE_SEA, "§d§lMedian Balance",
                "§f" + EconomicMetrics.formatLargeNumber(median),
                "",
                "§7Middle player balance"));

        // Row 3 - Concentration
        String top1Color = top1 < 20 ? "§a" : top1 < 40 ? "§e" : "§c";
        String top10Color = top10 < 50 ? "§a" : top10 < 70 ? "§e" : "§c";

        inv.setItem(29, createItem(Material.GOLDEN_APPLE, "§6§lTop 1% Wealth",
                top1Color + String.format("%.1f%%", top1),
                "",
                "§7Wealth held by richest 1%"));

        inv.setItem(31, createItem(Material.APPLE, "§c§lTop 10% Wealth",
                top10Color + String.format("%.1f%%", top10),
                "",
                "§7Wealth held by richest 10%"));

        inv.setItem(33, createItem(Material.CLOCK, "§e§lActive Players",
                "§7Use /eb health for details",
                "",
                "§7Tracks 7-day and 30-day",
                "§7player activity"));

        // Row 4 - Actions
        if (player.hasPermission("ecobalancer.gui.admin")) {
            inv.setItem(47, createItem(Material.WRITABLE_BOOK, "§eView Health Report",
                    "§7Detailed economy analysis",
                    "",
                    "§aRun: /eb health"));

            inv.setItem(49, createItem(Material.MAP, "§eView Trends",
                    "§7Historical data over time",
                    "",
                    "§aRun: /eb trends"));

            inv.setItem(51, createItem(Material.CHEST, "§eView Records",
                    "§7Tax operation history",
                    "",
                    "§aRun: /eb checkrecords"));
        }

        // Navigation
        inv.setItem(45, createItem(Material.ARROW, "§cBack", "§7Return to Main Menu"));

        fillBackground(inv);
        player.openInventory(inv);
    }

    private String getGiniDescription(double gini) {
        if (gini < 0.2)
            return "§aExcellent - Very equal distribution";
        if (gini < 0.3)
            return "§aGood - Relatively equal";
        if (gini < 0.4)
            return "§eModerate - Some inequality";
        if (gini < 0.5)
            return "§eWarning - High inequality";
        return "§cCritical - Severe inequality";
    }

    public void openTaxPolicies(Player player) {
        if (!player.hasPermission("ecobalancer.gui.admin")) {
            player.sendMessage(plugin.getFormattedMessage("messages.gui.no_permission_tax_policies", null));
            return;
        }

        List<String> policyNames = plugin.getPolicyManager().getPolicyNames();
        int rows = Math.max(4, (int) Math.ceil((policyNames.size() + 9) / 9.0) + 1);
        int size = Math.min(54, rows * 9);

        Inventory inv = Bukkit.createInventory(null, size, tr("messages.gui.tax_policies_title", "&6Tax Policies"));

        TaxPolicy active = plugin.getPolicyManager().getActivePolicy();
        String activeName = active != null ? active.getName() : "";

        // Header row
        inv.setItem(4, createItem(Material.GOLD_INGOT, "§e§lTax Policy Manager",
                "§7Manage your tax policies",
                "",
                "§7Active: §a" + (activeName.isEmpty() ? "None" : activeName),
                "",
                "§e§lLeft-click§7 - View details / Activate",
                "§c§lShift+Left-click§7 - Execute immediately"));

        int slot = 9; // Start from second row
        for (String name : policyNames) {
            if (slot >= size - 9)
                break;

            boolean isActive = name.equals(activeName);
            Material mat = isActive ? Material.ENCHANTED_BOOK : Material.BOOK;
            String displayName = (isActive ? "§a✓ " : "§e") + name;

            TaxPolicy p = plugin.getPolicyManager().getPolicy(name);
            String desc = p != null ? p.getDescription() : "";
            String schedule = p != null ? p.getScheduleType() : "?";
            String time = p != null ? p.getCheckTime() : "?";
            boolean isRoutine = p != null && p.isRoutine();

            List<String> lore = new ArrayList<>();
            lore.add("§7" + desc);
            lore.add("");
            lore.add("§7Schedule: §f" + capitalize(schedule) + " @ " + time);
            lore.add("§7Routine: " + (isRoutine ? "§aYes (Auto)" : "§cNo (Manual)"));
            lore.add("");
            if (isActive) {
                lore.add("§a§lCURRENT ACTIVE POLICY");
            } else {
                lore.add("§eLeft-click to activate");
            }
            lore.add("§cShift+click to execute now");

            inv.setItem(slot++, createItem(mat, displayName, lore.toArray(new String[0])));
        }

        // Controls at bottom
        inv.setItem(size - 9, createItem(Material.ARROW, "§cBack", "§7Return to Main Menu"));

        if (active != null) {
            inv.setItem(size - 5, createItem(Material.REDSTONE, "§c§lExecute Active Policy",
                    "§7Run §e" + activeName + "§7 now",
                    "",
                    "§c⚠ Affects all players!"));
        }

        inv.setItem(size - 1, createItem(Material.CHEST, "§eReload Policies",
                "§7Reload policies from disk"));

        fillBackground(inv);
        player.openInventory(inv);
    }

    public void openPolicyDetails(Player player, String policyName) {
        TaxPolicy policy = plugin.getPolicyManager().getPolicy(policyName);
        if (policy == null) {
            player.sendMessage(plugin.getFormattedMessage("messages.policy_not_found",
                    java.util.Collections.singletonMap("name", policyName)));
            return;
        }

        viewingPolicyDetails.put(player.getUniqueId(), policyName);

        Inventory inv = Bukkit.createInventory(null, 54, tr("messages.gui.policy_title_prefix", "&6Policy: ") + policyName);

        TaxPolicy active = plugin.getPolicyManager().getActivePolicy();
        boolean isActive = active != null && active.getName().equals(policyName);

        // Header
        inv.setItem(4, createItem(isActive ? Material.ENCHANTED_BOOK : Material.BOOK,
                (isActive ? "§a§l" : "§e§l") + policyName,
                "§7" + policy.getDescription(),
                "",
                isActive ? "§a✓ Currently Active" : "§7Inactive"));

        // Schedule info
        inv.setItem(19, createItem(Material.CLOCK, "§eSchedule",
                "§7Type: §f" + capitalize(policy.getScheduleType()),
                "§7Time: §f" + policy.getCheckTime(),
                "",
                "§7Routine: " + (policy.isRoutine() ? "§aYes" : "§cNo")));

        // Days/Dates
        String daysInfo = "";
        if ("weekly".equalsIgnoreCase(policy.getScheduleType())) {
            List<Integer> days = policy.getScheduleDaysOfWeek();
            daysInfo = days.isEmpty() ? "All days" : formatDaysOfWeek(days);
        } else if ("monthly".equalsIgnoreCase(policy.getScheduleType())) {
            List<Integer> dates = policy.getScheduleDatesOfMonth();
            daysInfo = dates.isEmpty() ? "All dates" : dates.toString();
        } else {
            daysInfo = "Every day";
        }
        inv.setItem(21, createItem(Material.CLOCK, "§eWhen",
                "§7" + daysInfo));

        // Settings
        inv.setItem(23, createItem(Material.IRON_INGOT, "§eSettings",
                "§7Max Deduction: §f" + EconomicMetrics.formatLargeNumber(policy.getMaxDeductionPerPlayer()),
                "§7Min Protection: §f" + EconomicMetrics.formatLargeNumber(policy.getMinBalanceProtection()),
                "§7Offline Only: " + (policy.isOnlyOfflinePlayers() ? "§aYes" : "§cNo"),
                "§7Percentile Mode: " + (policy.isPercentileThresholds() ? "§aYes" : "§cNo")));

        // Inactive settings
        inv.setItem(25, createItem(Material.ROTTEN_FLESH, "§eInactivity Rules",
                "§7Days to Deduct: §f"
                        + (policy.getInactiveDaysToDeduct() > 0 ? policy.getInactiveDaysToDeduct() : "Disabled"),
                "§7Days to Clear: §f"
                        + (policy.getInactiveDaysToClear() > 0 ? policy.getInactiveDaysToClear() : "Disabled")));

        // Tax brackets
        List<Map<String, Object>> brackets = policy.getTaxBrackets();
        inv.setItem(31, createItem(Material.GOLD_NUGGET, "§6§lTax Brackets",
                "§7" + brackets.size() + " bracket(s) configured"));

        brackets.sort(Comparator.comparingDouble(m -> ((Number) m.get("threshold")).doubleValue()));
        for (int i = 0; i < Math.min(brackets.size(), 7); i++) {
            Map<String, Object> b = brackets.get(i);
            double threshold = ((Number) b.get("threshold")).doubleValue();
            double rate = ((Number) b.get("rate")).doubleValue();

            inv.setItem(37 + i, createItem(Material.GOLD_NUGGET,
                    "§eBracket " + (i + 1),
                    "§7Threshold: §f" + EconomicMetrics.formatLargeNumber(threshold),
                    "§7Rate: §e" + String.format("%.2f%%", rate * 100)));
        }

        // Actions at bottom
        inv.setItem(45, createItem(Material.ARROW, "§cBack", "§7Return to Policy List"));

        if (!isActive) {
            inv.setItem(49, createItem(Material.LIME_DYE, "§aActivate This Policy",
                    "§7Set as the active policy",
                    "",
                    "§aClick to activate"));
        }

        inv.setItem(53, createItem(Material.REDSTONE, "§c§lExecute Now",
                "§7Run this policy immediately",
                "",
                "§c⚠ Affects all players!"));

        fillBackground(inv);
        player.openInventory(inv);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty())
            return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private String formatDaysOfWeek(List<Integer> days) {
        String[] dayNames = { "", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
        StringBuilder sb = new StringBuilder();
        for (int d : days) {
            if (d >= 1 && d <= 7) {
                if (sb.length() > 0)
                    sb.append(", ");
                sb.append(dayNames[d]);
            }
        }
        return sb.toString();
    }

    // --- Helpers ---

    private ItemStack createItem(Material mat, int data, String name, String... lore) {
        ItemStack item = new ItemStack(mat, 1, (short) data);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        return createItem(mat, 0, name, lore);
    }

    private void fillBackground(Inventory inv) {
        ItemStack pane = createItem(Material.BLACK_STAINED_GLASS_PANE, 0, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane);
            }
        }
    }

    // --- Event Handling ---

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();

        if (!title.startsWith("§6"))
            return;

        e.setCancelled(true);
        ItemStack current = e.getCurrentItem();
        if (current == null || current.getType() == Material.AIR)
            return;

        if (current.getType() == Material.BLACK_STAINED_GLASS_PANE)
            return;

        // Main Menu
        if (title.equals(tr("messages.gui.main_menu_title", "&6EcoBalancer Menu"))) {
            handleMainMenuClick(player, current);
        }
        // Dashboard
        else if (title.equals(tr("messages.gui.dashboard_title", "&6Economic Dashboard"))) {
            if (current.getType() == Material.ARROW)
                openMainMenu(player);
        }
        // Tax Policies List
        else if (title.equals(tr("messages.gui.tax_policies_title", "&6Tax Policies"))) {
            handleTaxPoliciesClick(player, current, e.getClick());
        }
        // Policy Details
        else if (title.startsWith(tr("messages.gui.policy_title_prefix", "&6Policy: "))) {
            handlePolicyDetailsClick(player, current, title);
        }
    }

    private void handleMainMenuClick(Player player, ItemStack current) {
        switch (current.getType()) {
            case PAPER:
                openDashboard(player);
                break;
            case GOLD_INGOT:
                openTaxPolicies(player);
                break;
            case REDSTONE:
                if (player.hasPermission("ecobalancer.gui.admin")) {
                    TaxPolicy active = plugin.getPolicyManager().getActivePolicy();
                    if (active != null) {
                        player.closeInventory();
                        player.sendMessage(plugin.getFormattedMessage("messages.gui.executing_active_policy",
                                java.util.Collections.singletonMap("name", active.getName())));
                        plugin.checkAll(player);
                    } else {
                        player.sendMessage(plugin.getFormattedMessage("messages.gui.no_active_policy", null));
                    }
                }
                break;
            default:
                break;
        }
    }

    private void handleTaxPoliciesClick(Player player, ItemStack current, ClickType click) {
        switch (current.getType()) {
            case ARROW:
                openMainMenu(player);
                break;
            case CHEST:
                plugin.getPolicyManager().loadPolicies();
                player.sendMessage(plugin.getFormattedMessage("messages.gui.policies_reloaded", null));
                openTaxPolicies(player);
                break;
            case REDSTONE:
                // Execute active policy
                TaxPolicy active = plugin.getPolicyManager().getActivePolicy();
                if (active != null) {
                    player.closeInventory();
                    player.sendMessage(plugin.getFormattedMessage("messages.gui.executing_policy",
                            java.util.Collections.singletonMap("name", active.getName())));
                    plugin.checkAll(player);
                }
                break;
            case BOOK:
            case ENCHANTED_BOOK:
                if (current.hasItemMeta() && current.getItemMeta().hasDisplayName()) {
                    String displayName = current.getItemMeta().getDisplayName();
                    String rawName = displayName.replaceAll("§[0-9a-fk-or]", "")
                            .replace("✓ ", "").trim();

                    if (plugin.getPolicyManager().getPolicyNames().contains(rawName)) {
                        if (click.isShiftClick()) {
                            // Shift+click = Execute immediately
                            player.closeInventory();
                            player.sendMessage(plugin.getFormattedMessage("messages.gui.executing_policy",
                                    java.util.Collections.singletonMap("name", rawName)));
                            plugin.executePolicy(player, rawName);
                        } else {
                            // Normal click = Open details
                            openPolicyDetails(player, rawName);
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    private void handlePolicyDetailsClick(Player player, ItemStack current, String title) {
        String policyName = viewingPolicyDetails.get(player.getUniqueId());
        if (policyName == null) {
            policyName = title.replace(tr("messages.gui.policy_title_prefix", "&6Policy: "), "");
        }

        switch (current.getType()) {
            case ARROW:
                openTaxPolicies(player);
                break;
            case LIME_DYE:
                // Activate
                if (plugin.getPolicyManager().getPolicyNames().contains(policyName)) {
                    plugin.getPolicyManager().setActivePolicy(policyName);
                    player.sendMessage(plugin.getFormattedMessage("messages.tax.policy_set_success",
                            java.util.Collections.singletonMap("name", policyName)));
                    openPolicyDetails(player, policyName); // Refresh
                }
                break;
            case REDSTONE:
                // Execute
                player.closeInventory();
                player.sendMessage(plugin.getFormattedMessage("messages.gui.executing_policy",
                        java.util.Collections.singletonMap("name", policyName)));
                plugin.executePolicy(player, policyName);
                break;
            default:
                break;
        }
    }

    private String tr(String key, String fallback) {
        String value = plugin.getLangConfig().getString(key, fallback);
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
