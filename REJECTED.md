# Refused Criteria and Abandoned Architectural Approaches

## 1. Refused Acceptance Criteria

### Criterion 2: "E7 causes exactly one overdraft fee to be assessed, on Day 2."

- **Status:** REJECTED
- **Reasoning:** E7 is a debit of AED 620.00 processed on Day 5 with `value_date = Day 2`. Its arrival changes the retrospectively calculated historical balance for Day 2, but it does not automatically change the fee assessment made when Day 2 was originally processed.

  Under the selected interpretation, fees are assessed using the closing ledger balance available at each day's processing cutoff. The original Day 2 closing balance is AED 250.00, so no overdraft fee is assessed on Day 2. The original Day 4 closing balance is AED 285.00, so no fee is assessed on Day 4 either.

  When E7 arrives on Day 5, the closing ledger balance becomes negative:

  `250.00 + 400.00 - 185.00 - 180.00 - 620.00 = -335.00`

  Therefore, an overdraft fee of AED 25.00 is assessed on Day 5 and booked with value date Day 5.

  The historical Day 2 balance, viewed after E7 arrives, is AED -370.00. However, a retrospectively calculated balance is not equivalent to the balance used for the original Day 2 fee assessment.

  This interpretation preserves the original fee-assessment history and prevents later backdated events from silently rewriting previously recorded decisions. The specification does not explicitly define retroactive fee reassessment, so this choice is documented in `AMBIGUITIES.md`.

### Criterion 4: "Any settlement referencing an authorization ID not present in the ledger must be rejected and the funds must not leave the account."

- **Status:** REJECTED
- **Reasoning:** E6 is a settlement for Auth-Z, for which no preceding authorization exists in the event stream.

  An unmatched settlement is not necessarily invalid in every payment-processing workflow. Some payment-network and clearing arrangements may permit a settlement or presentment without a matching authorization in the local ledger. The appropriate treatment depends on the applicable network rules and transaction type.

  For this simulation, E6 is honored as a ledger debit of AED 180.00. Because there is no matching authorization, no hold is released, and a diagnostic warning is recorded.

  This is a deliberate business-rule interpretation for the assessment, not a universal claim that all unmatched settlements must be accepted.

### Criterion 6: "After E9, all balances and fees return to their pre-E7 values."

- **Status:** REJECTED
- **Reasoning:** E9 reverses E7, the AED 620.00 debit. The reversal counteracts E7's principal effect, but it does not automatically erase independent fee assessments already booked.

  Before E7, the Day 5 ledger balance is AED 285.00. After E7, it becomes AED -335.00, triggering a Day 5 overdraft fee of AED 25.00.

  When E9 arrives on Day 6, E7's debit is excluded from the effective ledger calculation, but the Day 5 fee remains booked. The resulting balance, before any applicable Day 6 interest capitalization, is:

  `250.00 + 400.00 - 185.00 - 180.00 - 25.00 = 260.00`

  Therefore, the balance does not return to AED 285.00.

  The ledger is append-only: E7 remains recorded, E9 is added as a separate reversal event, and the previously booked fee is retained. If the business rules require a fee refund, that refund must be recorded as a separate compensating entry rather than achieved by deleting or mutating the original fee record.

### Criterion 7: "The three BHD instalments in E10 must each be BHD 3.334."

- **Status:** REJECTED
- **Reasoning:** Three instalments of BHD 3.334 total BHD 10.002, exceeding the original E10 credit of BHD 10.000 by BHD 0.002.

  Since BHD uses three decimal places, the instalments must respect that precision and sum exactly to the source amount.

  The selected allocation is:

  - Instalment 1: BHD 3.333
  - Instalment 2: BHD 3.333
  - Instalment 3: BHD 3.334
  - Total: BHD 10.000

  The remainder is assigned to the final instalment so that the split conserves the original amount without creating or losing money.

### Criterion 8: "If the rounded daily interest accruals do not sum to the capitalized total, the remainder is discarded."

- **Status:** REJECTED
- **Reasoning:** This criterion conflicts with the explicit requirement that rounded daily interest accruals must sum exactly to the capitalized total.

  Each daily accrual is calculated and rounded to the account currency's precision. The capitalized interest at the end of Day 6 must equal the sum of those recorded daily accruals.

  The implementation therefore sums the rounded daily accruals and posts that exact total as a single credit at the end of Day 6. It does not discard a difference or independently calculate a conflicting capitalization amount.

---

## 2. Abandoned Architectural Approaches

### In-Memory State Mutation (Direct Balance Updates)

- **Abandoned approach:** Updating an `account.balance` variable directly inside each event handler.
- **Reason for rejection:** Direct balance mutation makes backdated value-date events difficult to recalculate and audit. It also makes it harder to distinguish the processing timeline from the value-date timeline and to reproduce historical balance views.
- **Chosen approach:** Use an append-only event log and query-based event playback. Balances are calculated from applicable events using processing-day and value-day cutoffs. Reversals are recorded as new events and affect the calculated result without mutating or deleting the original event.

### Retroactively Rewriting Previously Assessed Fees

- **Abandoned approach:** Automatically reassessing historical fees whenever a backdated event changes a historical balance.
- **Reason for rejection:** The specification does not explicitly require retroactive fee reassessment. Automatically changing historical fee decisions would conflate retrospective balance calculation with the original daily assessment process.
- **Chosen approach:** Assess and record fees during the day-by-day replay. Preserve the assessment history when later events arrive, unless an explicit business rule defines a compensating fee entry.

### Splitting Instalments Using Identical Rounded Amounts

- **Abandoned approach:** Assigning BHD 3.334 to all three E10 instalments.
- **Reason for rejection:** The resulting total exceeds the original credit.
- **Chosen approach:** Calculate the base instalment at the currency's precision and assign the remainder to the final instalment.

### Independently Calculating the Capitalized Interest Total

- **Abandoned approach:** Calculating a separate interest total and allowing it to differ from the sum of the rounded daily accruals.
- **Reason for rejection:** This violates the explicit reconciliation requirement.
- **Chosen approach:** Sum the rounded daily accrual records and capitalize that exact sum once at the end of Day 6.

### Silently Rounding Invalid Amounts During Error Formatting

- **Abandoned approach:** Rounding an amount only when generating an error message, without first validating the amount's currency precision.
- **Reason for rejection:** The displayed amount could differ from the actual event amount, or formatting with `RoundingMode.UNNECESSARY` could throw an exception and prevent the intended error from being recorded.
- **Chosen approach:** Validate monetary precision at the event-processing boundary. Format validated amounts using the relevant currency scale.

---

## 3. Scope and Limitations

These decisions describe the behavior selected for this in-memory ledger assessment. They are not intended to represent every production banking or payment-network rule.

In particular:

- Unmatched settlement handling depends on the applicable payment-network and transaction rules.
- A reversal of a principal transaction does not automatically imply a fee refund.
- Historical balance calculation and historical fee assessment are separate concerns.
- Event validation and financial posting must be distinguishable so rejected events do not accidentally affect balances.
- Existing ledger events must remain immutable and available for audit and replay.
- Additional business behavior introduced beyond the explicit requirements must be documented and tested.
