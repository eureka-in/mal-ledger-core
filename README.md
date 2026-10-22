# In-Memory Account Ledger Core Engine

Production-grade, highly performant, single-file ledger implementation engineered for high-concurrency neobanking environments. Built using plain Java 21 with zero third-party framework dependencies.

## 🚀 How to Execute the Project

### Prerequisites
*   Java Development Kit (JDK) 21 installed.

### Compilation and Execution Steps
1. Save the source code block into a file named `LedgerApp.java`.
2. Open your terminal application and navigate to the directory containing the file.
3. Compile the file using the Java compiler:
   ```bash
   javac -d . LedgerApp.java
   ```
4. Run the compiled application binary file:
   ```bash
   java core.LedgerApp
   ```

## 📊 Interpreting the Output Terminal Stream
The program loops sequentially from Day 1 through Day 6. Every day, the core engine replays its input events and prints a structured console log line for both accounts:
*   `Closing Ledger Balance`: The net funds present in the account after accounting for credits, debits, settlements, and fees.
*   `Fees Assessed`: Overdraft penalties applied during that specific processing day.
*   `Active Holds`: Authorization tracking locks restricting the available balance.
*   `Errors`: Structural exceptions, validation errors, or informational trace warnings logged during real-time transaction processing.

### Intentional Failing Test

The application includes one deliberately failing verification test to demonstrate a limitation of the current design.

The event log is append-only, but historical balances are recalculated using the processing-day and value-day cutoffs. When E9 reverses E7 on Day 6, a balance recalculated for Day 5 changes from AED -360.00 to AED 260.00.

The test exposes the fact that the current implementation does not preserve immutable historical balance snapshots. The failure is intentional and documents a design limitation rather than indicating that the original event was deleted from the event log.
