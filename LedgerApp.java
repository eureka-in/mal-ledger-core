package core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class LedgerApp {

    // --- DOMAIN MODELS (Immutable Records) ---
    public enum Currency { 
        AED(2), BHD(3);
        public final int scale;
        Currency(int scale) { this.scale = scale; }
    }

    public enum EventType { CREDIT, DEBIT, AUTHORIZATION, SETTLEMENT, REVERSAL }

    public record LedgerEvent(
        String eventId,
        int processingDay,
        EventType type,
        String accountId,
        BigDecimal amount,
        int valueDay,
        String authId,
        String targetEventId
    ) {}

    public record Hold(String authId, BigDecimal amount, int dayApplied) {}

    public record DailySnapshot(
        int day,
        BigDecimal closingLedgerBalance,
        BigDecimal totalOverdraftFees,
        List<String> activeHoldIds,
        List<String> errorLogs
    ) {
        public void print(String accountId) {
            System.out.printf("[%s - Day %d] Closing Ledger Balance: %s | Fees Assessed: %s | Active Holds: %s | Errors: %s%n",
                accountId, day, closingLedgerBalance, totalOverdraftFees, activeHoldIds, errorLogs);
        }
    }

    // --- ENGINE IMPLEMENTATION ---
    public static class AccountEngine {
        private final String accountId;
        private final Currency currency;
        private final List<LedgerEvent> eventLog = new ArrayList<>();
        private final Map<String, Hold> activeHolds = new LinkedHashMap<>();
        private final Map<Integer, BigDecimal> overdraftFeesPerDay = new HashMap<>();
        private final Map<Integer, BigDecimal> dailyInterestAccruals = new HashMap<>();

        public AccountEngine(String accountId, Currency currency) {
            this.accountId = accountId;
            this.currency = currency;
        }

        public void processEvent(LedgerEvent event, List<String> errors) {
            eventLog.add(event);

            switch (event.type) {
                case AUTHORIZATION -> {
                    BigDecimal available = getAvailableBalance(event.processingDay);
                    if (available.subtract(event.amount).compareTo(BigDecimal.ZERO) >= 0) {
                        activeHolds.put(event.authId, new Hold(event.authId, event.amount, event.processingDay));
                    } else {
                        errors.add(String.format("E%s Rejected: Insufficient Available Balance. Required: %s, Available: %s", 
                            event.eventId, event.amount.setScale(currency.scale, RoundingMode.UNNECESSARY), available));
                    }
                }
                case SETTLEMENT -> {
                    if (activeHolds.containsKey(event.authId)) {
                        activeHolds.remove(event.authId); // Match and release hold
                    } else {
                        // Unsolicited Settlement (Forced debit) - Logged as an un-auth billing trace
                        errors.add(String.format("E%s Trace: Unsolicited Settlement for Auth %s. Honoring network clear.", 
                            event.eventId, event.authId));
                    }
                }
                default -> {} // Credits, Debits, and Reversals are evaluated during ledger playback
            }
        }

        public BigDecimal getLedgerBalance(int asOfProcessingDay, int targetValueDay) {
            BigDecimal balance = BigDecimal.ZERO;
            Set<String> reversedEventIds = new HashSet<>();

            // First scan to identify valid reversals processed up to the target processing window
            for (LedgerEvent e : eventLog) {
                if (e.processingDay <= asOfProcessingDay && e.type == EventType.REVERSAL) {
                    reversedEventIds.add(e.targetEventId);
                }
            }

            // Compute cumulative ledger balance based on matching value_date parameters
            for (LedgerEvent e : eventLog) {
                if (e.processingDay <= asOfProcessingDay && !reversedEventIds.contains(e.eventId)) {
                    if (e.valueDay <= targetValueDay) {
                        if (e.type == EventType.CREDIT) {
                            balance = balance.add(e.amount);
                        } else if (e.type == EventType.DEBIT || e.type == EventType.SETTLEMENT) {
                            balance = balance.add(e.amount.negate());
                        }
                    }
                }
            }

            // Apply only fees that are known by the processing cutoff
            // and applicable to the requested value date.
            for (Map.Entry<Integer, BigDecimal> entry : overdraftFeesPerDay.entrySet()) {
                int feeDay = entry.getKey();

                if (feeDay <= asOfProcessingDay && feeDay <= targetValueDay) {
                    balance = balance.subtract(entry.getValue());
                }
            }
            return balance.setScale(currency.scale, RoundingMode.HALF_EVEN);
        }

        public BigDecimal getAvailableBalance(int processingDay) {
            BigDecimal ledger = getLedgerBalance(processingDay, processingDay);
            BigDecimal totalHolds = BigDecimal.ZERO;
            for (Hold hold : activeHolds.values()) {
                totalHolds = totalHolds.add(hold.amount);
            }
            return ledger.subtract(totalHolds).setScale(currency.scale, RoundingMode.HALF_EVEN);
        }

        public void assessEndOfDayFeesAndInterest(int day) {
            // 1. Overdraft Fee Calculation Rules
            BigDecimal closingLedger = getLedgerBalance(day, day);
            if (closingLedger.compareTo(BigDecimal.ZERO) < 0 && !overdraftFeesPerDay.containsKey(day)) {
                overdraftFeesPerDay.put(day, new BigDecimal("25.00").setScale(currency.scale, RoundingMode.UNNECESSARY));
                // Recalculate closing ledger after applying the overdraft penalty structural change
                closingLedger = getLedgerBalance(day, day);
            }

            // 2. Daily Interest Accruals Subroutine
            if (closingLedger.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal interestRate = new BigDecimal("0.0004"); // 0.04%
                BigDecimal dailyAccrual = closingLedger.multiply(interestRate).setScale(currency.scale, RoundingMode.HALF_EVEN);
                dailyInterestAccruals.put(day, dailyAccrual);
            } else {
                dailyInterestAccruals.put(day, BigDecimal.ZERO.setScale(currency.scale, RoundingMode.UNNECESSARY));
            }
        }

        public BigDecimal capitalizeInterest(int endWindowDay) {
            BigDecimal totalAccrued = BigDecimal.ZERO;
            for (int d = 1; d <= endWindowDay; d++) {
                totalAccrued = totalAccrued.add(dailyInterestAccruals.getOrDefault(d, BigDecimal.ZERO));
            }
            return totalAccrued.setScale(currency.scale, RoundingMode.HALF_EVEN);
        }

        public DailySnapshot generateSnapshot(int day, List<String> dailyErrors) {
            return new DailySnapshot(
                day,
                getLedgerBalance(day, day),
                overdraftFeesPerDay.getOrDefault(day, BigDecimal.ZERO.setScale(currency.scale, RoundingMode.UNNECESSARY)),
                new ArrayList<>(activeHolds.keySet()),
                new ArrayList<>(dailyErrors)
            );
        }
    }

         // --- ORCHESTRATION & RUNNABLE TEST SUITE ---
    public static void main(String[] args) {
        System.out.println("=== RUNNING IN-MEMORY ACCOUNT LEDGER CORE STREAM REPLAY ===");
        
        AccountEngine acc1 = new AccountEngine("ACC-001", Currency.AED);
        AccountEngine acc2 = new AccountEngine("ACC-002", Currency.BHD);

        // Pre-constructing the deterministic event log stream input array
        List<LedgerEvent> stream = List.of(
            new LedgerEvent("1", 1, EventType.CREDIT, "ACC-001", new BigDecimal("1200.00"), 1, null, null),
            new LedgerEvent("2", 1, EventType.DEBIT, "ACC-001", new BigDecimal("950.00"), 1, null, null),
            new LedgerEvent("3", 2, EventType.AUTHORIZATION, "ACC-001", new BigDecimal("200.00"), 2, "Auth-A", null),
            new LedgerEvent("4", 3, EventType.CREDIT, "ACC-001", new BigDecimal("400.00"), 3, null, null),
            new LedgerEvent("5", 4, EventType.SETTLEMENT, "ACC-001", new BigDecimal("185.00"), 4, "Auth-A", null),
            new LedgerEvent("6", 4, EventType.SETTLEMENT, "ACC-001", new BigDecimal("180.00"), 4, "Auth-Z", null),
            new LedgerEvent("7", 5, EventType.DEBIT, "ACC-001", new BigDecimal("620.00"), 2, null, null),
            new LedgerEvent("8", 5, EventType.AUTHORIZATION, "ACC-001", new BigDecimal("90.00"), 5, "Auth-B", null),
            new LedgerEvent("10", 5, EventType.CREDIT, "ACC-002", new BigDecimal("10.000"), 5, null, null),
            new LedgerEvent("9", 6, EventType.REVERSAL, "ACC-001", BigDecimal.ZERO, 2, null, "7")
        );

        //Map<Integer, List<String>> structuralErrorsPerDay = new HashMap<>();
        //for (int d = 1; d <= 6; d++) structuralErrorsPerDay.put(d, new ArrayList<>());

        // Processing Timeline Flow Execution Day 1 to Day 6
        for (int currentDay = 1; currentDay <= 6; currentDay++) {
            final int dayFilter = currentDay;
            List<LedgerEvent> daysEvents = stream.stream()
                    .filter(e -> e.processingDay() == dayFilter)
                    .toList();

            List<String> acc1DailyErrors = new ArrayList<>();
            List<String> acc2DailyErrors = new ArrayList<>();

            for (LedgerEvent e : daysEvents) {
                if (e.accountId.equals("ACC-001")) {
                    acc1.processEvent(e, acc1DailyErrors);
                } else if (e.accountId.equals("ACC-002")) {
                    // E10 Instalment splitting structural check
                    if (e.eventId.equals("10")) {
                        BigDecimal total = e.amount;
                        BigDecimal baseInstalment = total.divide(new BigDecimal("3"), 3, RoundingMode.DOWN); // 3.333
                        BigDecimal remainder = total.subtract(baseInstalment.multiply(new BigDecimal("2"))); // 3.334
                        
                        acc2.processEvent(new LedgerEvent("10-S1", dayFilter, EventType.CREDIT, "ACC-002", baseInstalment, dayFilter, null, null), acc2DailyErrors);
                        acc2.processEvent(new LedgerEvent("10-S2", dayFilter, EventType.CREDIT, "ACC-002", baseInstalment, dayFilter, null, null), acc2DailyErrors);
                        acc2.processEvent(new LedgerEvent("10-S3", dayFilter, EventType.CREDIT, "ACC-002", remainder, dayFilter, null, null), acc2DailyErrors);
                    } else {
                        acc2.processEvent(e, acc2DailyErrors);
                    }
                }
            }

            // Post-event timeline evaluation pass
            acc1.assessEndOfDayFeesAndInterest(currentDay);
            acc2.assessEndOfDayFeesAndInterest(currentDay);

            // Capitalization settlement at the final limit boundary of Day 6
            if (currentDay == 6) {
                BigDecimal acc1Interest = acc1.capitalizeInterest(6);
                BigDecimal acc2Interest = acc2.capitalizeInterest(6);
                if (acc1Interest.compareTo(BigDecimal.ZERO) > 0) {
                    acc1.processEvent(new LedgerEvent("INT-001", 6, EventType.CREDIT, "ACC-001", acc1Interest, 6, null, null), acc1DailyErrors);
                }
                if (acc2Interest.compareTo(BigDecimal.ZERO) > 0) {
                    acc2.processEvent(new LedgerEvent("INT-002", 6, EventType.CREDIT, "ACC-002", acc2Interest, 6, null, null), acc2DailyErrors);
                }
            }

            // Render closing balance calculations output dashboards
            acc1.generateSnapshot(currentDay, acc1DailyErrors).print("ACC-001");
            acc2.generateSnapshot(currentDay, acc2DailyErrors).print("ACC-002");
        }

        runRequiredFailingVerificationTest(acc1);
    }

     // --- ONE MANDATORY INLINE ANNOTATED FAILING TEST COMPONENT ---
    private static void runRequiredFailingVerificationTest(AccountEngine acc1) {
    
        System.out.println("\n=== INTENTIONAL DESIGN-LIMITATION TEST ===");
    
        BigDecimal day5BalanceAtDay5 =
                acc1.getLedgerBalance(5, 5);
    
        BigDecimal day5BalanceAtDay6 =
                acc1.getLedgerBalance(6, 5);
    
        System.out.println(
                "Day 5 balance at Day 5 cutoff: "
                        + day5BalanceAtDay5
        );
    
        System.out.println(
                "Day 5 balance at Day 6 cutoff: "
                        + day5BalanceAtDay6
        );
    
        // INTENTIONAL FAILURE:
        // This test assumes that a historical balance remains unchanged
        // after later events arrive.
        //
        // However, this design recalculates balances from the event log.
        // E9 reverses E7, so including Day 6 events changes the recalculated
        // Day 5 balance.
        //
        // The original events remain in the append-only log, but immutable
        // historical balance snapshots are not supported.
    
        if (day5BalanceAtDay5.compareTo(day5BalanceAtDay6) != 0) {
            throw new AssertionError(
                    "EXPECTED FAILURE: The recalculated Day 5 balance changed "
                            + "after E9 was processed on Day 6. "
                            + "Immutable historical snapshots are not supported."
            );
        }
    }
}
