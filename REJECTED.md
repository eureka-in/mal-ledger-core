# Refused Criteria and Abandoned Architectural Approaches

## 1. Refused Acceptance Criteria

### Criterion 2: "E7 causes exactly one overdraft fee to be assessed, on Day 2."
*   **Status:** REJECTED & REFUSED
*   **Reasoning:** E7 is a backdated DEBIT of AED 620.00 with `value_date = Day 2` processed on Day 5. By retroactively applying an AED 620.00 reduction to Day 2's closing value, it drops the historical closing balances for **both Day 2 and Day 4** below zero (Day 3 remains positive at +AED 30.00 due to E4's credit). Because the non-negotiable directive mandates that an overdraft penalty is assessed *once per day per account when that day's closing ledger balance is negative*, E7 triggers exactly **two** distinct overdraft events across the window (one for Day 2, and one for Day 4). Claiming it only causes one fee is mathematically and contractually false.

### Criterion 4: "Any settlement referencing an authorization ID not present in the ledger must be rejected and the funds must not leave the account."
*   **Status:** REJECTED & REFUSED
*   **Reasoning:** In core transactional banking networks (e.g., Visa/Mastercard clearing networks), forced clearings or unsolicited settlements (like `Auth-Z` in event E6) are legally binding payment records. If an external entity files a settlement charge without a matching authorization, the bank cannot unilaterally reject it to save the customer's balance. Doing so creates a massive financial imbalance with the central clearing house. The settlement must be honored as a ledger debit, even if it forces an account into overdraft.

### Criterion 6: "After E9, all balances and fees return to their pre-E7 values."
*   **Status:** REJECTED & REFUSED
*   **Reasoning:** The system rules explicitly state that the ledger is **append-only and immutable**. Event E9 arrives on Day 6 as a *Reversal* of E7. While E9 counterbalances E7's principal value, it cannot retroactively erase the physical history of Day 5, where an overdraft fee had already been processed, calculated, and appended to the ledger data store. Erasures break audit logs. If a fee is reversed, it must be recorded as an explicit *Fee Credit* entry on Day 6, keeping history intact.

### Criterion 7: "The three BHD instalments in E10 must each be BHD 3.334."
*   **Status:** REJECTED & REFUSED
*   **Reasoning:** Simple math disproves this claim: 3.334 × 3 = 10.002. This causes a balance discrepancy of `+0.002 BHD`, meaning money would be created out of thin air. To divide 10.000 BHD into three equal parts with 3 decimal places, the remainder must be allocated dynamically across the entries (e.g., Segment 1 = 3.333, Segment 2 = 3.333, Segment 3 = 3.334). They cannot all be 3.334.

### Criterion 8: "If the rounded daily interest accruals do not sum to the capitalized total, the remainder is discarded."
*   **Status:** REJECTED & REFUSED
*   **Reasoning:** This contradicts the strict rule: *"The rounded daily accruals must sum exactly to the capitalized total."* Discarding remainders breaks financial reconciliation. The final capitalization event must act as an absorbing entry to capture fractional discrepancies.

---

## 2. Abandoned Architectural Approaches

### In-Memory State Mutation (Stateful OOP Updating)
*   **Abandoned Reason:** Modifying an `account.balance` primitive variable directly inside an event handler loop makes backdated value updates (`value_date < processing_date`) nearly impossible to trace, audit, or recalculate safely. It was abandoned in favor of a query-based **Event Playback Pattern**, which recalculates point-in-time state records dynamically based on active filter rules.
