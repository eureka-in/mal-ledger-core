# Architectural Ambiguities

## 1. Overdraft Fee Timing

**Ambiguity:** E7 is processed on Day 5 but has a value date of Day 2. Should the overdraft fee be charged on Day 2 or Day 5?

**Decision:** Assess the fee on Day 5, when E7 is processed and the closing balance becomes negative. Book the fee with value date Day 5. Do not retroactively change earlier fee assessments.

## 2. Partial Settlement of an Authorization

**Ambiguity:** Auth-A has a hold of AED 200.00, but E5 settles only AED 185.00. Should the remaining AED 15.00 stay on hold?

**Decision:** Release the entire Auth-A hold when the settlement arrives. Record AED 185.00 as the ledger debit. This is a simplifying assumption because the specification does not define partial-settlement behavior.

## 3. Reversal of E7

**Ambiguity:** Should E9 delete E7 or counteract its financial effect?

**Decision:** Keep both events in the append-only event log. When calculating balances after Day 6, recognize E9 and exclude E7's financial effect. Queries ending on Day 5 must not apply E9 because it has not yet been processed. The Day 5 overdraft fee remains booked unless a separate fee reversal is recorded.

## 4. Unmatched Settlement

**Ambiguity:** E6 settles Auth-Z, but no authorization exists for Auth-Z. Should the settlement be rejected?

**Decision:** Honor the AED 180.00 settlement as a debit and record a warning. This is the chosen behavior for this simulation; actual payment-processing rules may differ.

## 5. Interest Rounding

**Ambiguity:** How should the system ensure daily rounded interest equals the capitalized total?

**Decision:** Calculate and round each day's interest to the currency's precision. Sum those rounded amounts and post that exact sum as a single credit at the end of Day 6.

## 6. BHD Instalments

**Ambiguity:** How can BHD 10.000 be split into three instalments without changing the total?

**Decision:** Use BHD 3.333, BHD 3.333, and BHD 3.334. The instalments total exactly BHD 10.000.

## 7. Rejected Authorizations

**Ambiguity:** Should a rejected authorization create a hold?

**Decision:** No. Record the rejected attempt and its reason, but do not create an active hold. Auth-B is rejected because the available balance cannot support the additional AED 90.00 hold without becoming negative.
