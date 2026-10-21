# Architectural Ambiguities & Logical Resolutions

### 1. Overdraft Assessment Timing vs. Backdated Entry Processing
*   **Ambiguity:** If a backdated transaction processed on Day 5 makes Day 2's balance negative retroactively, is the fee booked on Day 2 or Day 5?
*   **Resolution:** The rule states: *"Booked with value_date equal to the day assessed."* I resolved this by determining that a fee can only be assessed on the calendar day the system actually becomes aware of the negative balance condition (Day 5). Therefore, the overdraft penalty is appended on Day 5 with a matching `value_date = Day 5`. It does not retroactively change the core ledger balance calculation of Day 2 *prior* to Day 5.

### 2. Available Balance Definition vs. Backdated Hold Drops
*   **Ambiguity:** Event E5 settles `Auth-A` for AED 185.00 on Day 4, but the original hold was AED 200.00. Does the remaining AED 15.00 hold continue to restrict the available balance?
*   **Resolution:** In standard card networks, a settlement event acts as the final clearing signal for an authorization block. I resolved this by releasing the entire associated hold container (`AED 200.00`) immediately upon receiving the settlement signal, ensuring the remaining balance is completely unfrozen.

### 3. Reversal Processing Target Definition
*   **Ambiguity:** Event E9 reverses E7 on Day 6. Does this mean E7 is removed from memory, or is a matching offset appended?
*   **Resolution:** To uphold the immutable, append-only ledger constraint, E7 cannot be edited or deleted. The system resolves this by keeping E7 in log memory, but marking its unique identifier as "neutralized" in subsequent query iterations. This ensures that any calculation path looking at history after Day 6 excludes E7's impact, while anyone viewing the ledger *as of Day 5* still sees E7 as fully active.
