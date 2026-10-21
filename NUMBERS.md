# Constant Selection Rationale

### 1. Overdraft Penalty Constant (`AED 25.00`)
*   **Value Chosen:** `25.00`
*   **Why not half?** This penalty parameter acts as a strict structural deterrent against liquidity exhaustion inside the neobank core. Halving it reduces the penalty cost to a level that might not discourage high-risk overdraft behavior, exposing the ledger engine to uncollateralized credit risk.

### 2. Interest Rate Accumulator (`0.0004` / 0.04% daily)
*   **Value Chosen:** `0.0004`
*   **Why not half?** This rate represents a high-yield retail incentive model designed to secure core customer deposits. Halving the rate to 0.02% would fail to meet product target requirements, reducing customer acquisition metrics across competing digital platforms.

### 3. Allocation Division Step Size (`3`)
*   **Value Chosen:** `3`
*   **Why not half?** Event E10 explicitly mandates that the incoming payment is divided into three equal installments. Halving this variable would change the payment schedule to a 2-part division, which directly breaks the transaction contract rules.
