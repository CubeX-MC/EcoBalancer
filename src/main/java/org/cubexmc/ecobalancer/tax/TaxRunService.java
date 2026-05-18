package org.cubexmc.ecobalancer.tax;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.command.CommandSender;

public class TaxRunService {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile TaxRunState state = idleState();

    public boolean tryStart(int operationId, String policyName, TaxOperationType trigger, int totalPlayers,
            CommandSender sender) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        String senderName = sender == null ? "Console/Scheduler" : sender.getName();
        state = new TaxRunState(true, operationId, policyName, System.currentTimeMillis(), totalPlayers, 0, 0, 0.0,
                trigger, senderName);
        return true;
    }

    public void updateProgress(int processedPlayers, int affectedPlayers, double totalDeducted) {
        TaxRunState current = state;
        if (!current.isRunning()) {
            return;
        }
        state = new TaxRunState(true, current.getOperationId(), current.getPolicyName(), current.getStartedAt(),
                current.getTotalPlayers(), processedPlayers, affectedPlayers, totalDeducted, current.getTrigger(),
                current.getSenderName());
    }

    public void finish() {
        state = idleState();
        running.set(false);
    }

    public TaxRunState getState() {
        return state;
    }

    public boolean isRunning() {
        return running.get();
    }

    private static TaxRunState idleState() {
        return new TaxRunState(false, -1, "", 0L, 0, 0, 0, 0.0, null, "");
    }
}
