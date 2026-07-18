# LLD 02 — Splitwise / Expense Sharing App

> **Design an app where users can add shared expenses, split them in different ways, and settle debts with minimum transactions.**

---

## Clarify Requirements

**Functional:**
- Add users to a group
- Add an expense — paid by one user, split among multiple
- Split types: Equal, Exact amount, Percentage
- View balances — who owes whom how much
- Settle up — minimize number of transactions to clear all debts

**Non-Functional:**
- Correct balance computation at all times
- Extensible split types

---

## Identify Entities → Classes

```
User            → id, name, email
Group           → collection of users + expenses
Expense         → amount, paid by, split among, split type
Split           → per-user share of an expense (base class)
  EqualSplit
  ExactSplit
  PercentSplit
BalanceSheet    → net balance per user pair
```

---

## Class Design

```java
// User
public class User {
    private final String id;
    private final String name;
    public User(String id, String name) { this.id = id; this.name = name; }
    public String getId() { return id; }
    public String getName() { return name; }
}

// Split types (Strategy Pattern)
public abstract class Split {
    protected User user;
    protected double amount;
    public Split(User user) { this.user = user; }
    public User getUser() { return user; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}

public class EqualSplit extends Split {
    public EqualSplit(User user) { super(user); }
}

public class ExactSplit extends Split {
    public ExactSplit(User user, double amount) {
        super(user);
        this.amount = amount;
    }
}

public class PercentSplit extends Split {
    private final double percent;
    public PercentSplit(User user, double percent) {
        super(user);
        this.percent = percent;
    }
    public double getPercent() { return percent; }
}

// Expense
public class Expense {
    private final String id;
    private final double amount;
    private final User paidBy;
    private final List<Split> splits;
    private final String description;

    public Expense(String id, double amount, User paidBy,
                   List<Split> splits, String description) {
        this.id = id;
        this.amount = amount;
        this.paidBy = paidBy;
        this.splits = splits;
        this.description = description;
        resolveSplits();
    }

    private void resolveSplits() {
        if (splits.get(0) instanceof EqualSplit) {
            double share = amount / splits.size();
            splits.forEach(s -> s.setAmount(share));
        } else if (splits.get(0) instanceof PercentSplit) {
            splits.forEach(s -> {
                PercentSplit ps = (PercentSplit) s;
                ps.setAmount(amount * ps.getPercent() / 100.0);
            });
        }
        // ExactSplit amounts are already set
    }

    public User getPaidBy() { return paidBy; }
    public List<Split> getSplits() { return splits; }
    public double getAmount() { return amount; }
}

// Balance Sheet — tracks net balance between pairs
public class BalanceSheet {
    // balances[A][B] = amount A owes B (negative = B owes A)
    private final Map<String, Map<String, Double>> balances = new HashMap<>();

    public void updateBalance(String fromUserId, String toUserId, double amount) {
        balances.computeIfAbsent(fromUserId, k -> new HashMap<>()).merge(toUserId, amount, Double::sum);
        balances.computeIfAbsent(toUserId, k -> new HashMap<>()).merge(fromUserId, -amount, Double::sum);
    }

    public double getBalance(String fromUserId, String toUserId) {
        return balances.getOrDefault(fromUserId, Collections.emptyMap())
                       .getOrDefault(toUserId, 0.0);
    }

    public Map<String, Map<String, Double>> getAllBalances() { return balances; }
}

// Group — the main orchestrator
public class Group {
    private final String id;
    private final List<User> members = new ArrayList<>();
    private final List<Expense> expenses = new ArrayList<>();
    private final BalanceSheet balanceSheet = new BalanceSheet();

    public Group(String id) { this.id = id; }

    public void addMember(User user) { members.add(user); }

    public void addExpense(Expense expense) {
        expenses.add(expense);
        String payerId = expense.getPaidBy().getId();

        for (Split split : expense.getSplits()) {
            String owerId = split.getUser().getId();
            if (!owerId.equals(payerId)) {
                // ower owes payer
                balanceSheet.updateBalance(owerId, payerId, split.getAmount());
            }
        }
    }

    public void printBalances() {
        balanceSheet.getAllBalances().forEach((from, toMap) ->
            toMap.forEach((to, amount) -> {
                if (amount > 0) {
                    System.out.printf("%s owes %s: %.2f%n", from, to, amount);
                }
            })
        );
    }
}
```

---

## Debt Simplification Algorithm (The Hard Part)

```
Problem: After many expenses, settling pair by pair = many transactions
Goal: Minimize number of transactions to settle all debts

Algorithm:
  1. Compute net balance for each user (positive = owed money, negative = owes money)
  2. Use two pointers: max creditor and max debtor
  3. Settle as much as possible between them
  4. Repeat until all balances are 0

Example:
  A owes B ₹100
  B owes C ₹100
  Without simplification: A→B, B→C = 2 transactions
  With simplification: A→C = 1 transaction
```

```java
public class DebtSimplifier {

    public List<String> simplify(Map<String, Double> netBalances) {
        // netBalances: userId → net amount (+ve = owed, -ve = owes)
        List<Map.Entry<String, Double>> credits = new ArrayList<>();
        List<Map.Entry<String, Double>> debts = new ArrayList<>();

        netBalances.forEach((user, balance) -> {
            if (balance > 0.01)       credits.add(Map.entry(user, balance));
            else if (balance < -0.01) debts.add(Map.entry(user, Math.abs(balance)));
        });

        // Sort descending
        credits.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        debts.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<String> transactions = new ArrayList<>();
        int i = 0, j = 0;

        while (i < credits.size() && j < debts.size()) {
            double credit = credits.get(i).getValue();
            double debt   = debts.get(j).getValue();
            double settle = Math.min(credit, debt);

            transactions.add(String.format("%s pays %s ₹%.2f",
                debts.get(j).getKey(), credits.get(i).getKey(), settle));

            credits.set(i, Map.entry(credits.get(i).getKey(), credit - settle));
            debts.set(j,   Map.entry(debts.get(j).getKey(),   debt   - settle));

            if (credits.get(i).getValue() < 0.01) i++;
            if (debts.get(j).getValue()   < 0.01) j++;
        }

        return transactions;
    }
}
```

---

## Usage

```java
User alice = new User("u1", "Alice");
User bob   = new User("u2", "Bob");
User carol = new User("u3", "Carol");

Group trip = new Group("g1");
trip.addMember(alice); trip.addMember(bob); trip.addMember(carol);

// Alice paid ₹300, split equally
trip.addExpense(new Expense("e1", 300, alice,
    List.of(new EqualSplit(alice), new EqualSplit(bob), new EqualSplit(carol)),
    "Dinner"));

// Bob paid ₹200, Carol owes ₹80, Alice owes ₹120
trip.addExpense(new Expense("e2", 200, bob,
    List.of(new ExactSplit(alice, 120), new ExactSplit(carol, 80)),
    "Hotel"));

trip.printBalances();
```

---

## Design Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Strategy** | Split types (Equal/Exact/Percent) | Swap split logic without changing Expense |
| **Observer** | Notify users of new expenses | Decouple expense addition from notification |
| **Factory** | SplitFactory.create(type) | Create split by type string |

---

## Follow-up Depth Points

**1. Floating point precision?**
> Use `BigDecimal` for all monetary calculations. `double` causes rounding errors (₹33.333...).

**2. Concurrent expense addition?**
> Multiple users adding expenses simultaneously. Use `synchronized` on `addExpense()` or use `ConcurrentHashMap` + atomic updates in BalanceSheet.

**3. Expense categories?**
> Add a `Category` enum to Expense. Filter expenses by category for spending reports.

**4. Recurring expenses?**
> Add an `interval` field to Expense. Scheduler (Problem 3!) auto-creates the expense periodically.

**5. Database design?**
```
users(id, name, email)
groups(id, name)
group_members(group_id, user_id)
expenses(id, group_id, amount, paid_by, description, created_at)
splits(id, expense_id, user_id, split_type, amount, percent)
balances(group_id, from_user, to_user, amount)  ← denormalized for fast reads
```

---

## One-Line Interview Answer

> *"Splitwise has User, Group, Expense, and Split as core entities. Split is a Strategy pattern — EqualSplit, ExactSplit, PercentSplit implement the same interface. On each expense add, update a balance matrix between user pairs. The hard algorithmic part is debt simplification — compute net balance per user, then greedily settle max creditor against max debtor to minimize transaction count. Use BigDecimal, not double, for all money math."*
