# DSA Problem Tracker

> **Philosophy:** Pattern recognition over problem count.
> For every problem: know the pattern, the invariant, and why this data structure fits.
> Target: 41 problems across 10 patterns — lean and complete.

---

## Week 1 — HashMap / HashSet

**Core Idea:** Trade O(n) lookup for O(1) by storing seen values in a map/set.

1. **Two Sum** — Find two numbers whose sum equals a target.
   - Pattern: HashMap lookup (complement search)
   - Invariant: `map[target - nums[i]]` has been seen before index i
   - Key insight: For each number, check if its complement already exists in the map
  
   - for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (seen.containsKey(complement)) {
                return new int[]{seen.get(complement), i};
            }
            seen.put(nums[i], i);
        }

2. **Contains Duplicate** — Determine if an array contains duplicate values.
   - Pattern: HashSet membership check
   - Key insight: Add each element; if add returns false (already exists) → duplicate found

3. **Valid Anagram** — Check whether two strings are anagrams.
   - Pattern: Frequency count map
   - Key insight: Two strings are anagrams iff their character frequency maps are identical

4. **Longest Substring Without Repeating Characters** — Find the longest substring with unique characters.
   - Pattern: Sliding Window + HashMap
   - Invariant: `map[char]` stores the last seen index of char
   - Key insight: On duplicate found, move `left` pointer to `max(left, lastSeen + 1)` — don't just move by 1

5. **Happy Number** — Determine whether a number is happy by detecting cycles.
   - Pattern: HashSet cycle detection
   - Key insight: If sum of squares ever repeats → infinite loop → not happy. If reaches 1 → happy

---

## Week 2 — Two Pointers

**Core Idea:** Use two indices moving toward each other (or at different speeds) to avoid nested loops.

6. **Valid Palindrome** — Check if a string is a palindrome ignoring non-alphanumeric characters.
   - Pattern: Opposite-end two pointers
   - Key insight: Skip non-alphanumeric chars, compare lowercased chars at left and right
   -  boolean isAlphaNumeric = Character.isLetterOrDigit(ch);
   -  public boolean isAlphaNumericManual(char c) {
       return (c >= 'A' && c <= 'Z') || 
           (c >= 'a' && c <= 'z') || 
           (c >= '0' && c <= '9');
      }


7. **Merge Sorted Array** — Merge two sorted arrays in-place.
   - Pattern: Two pointers from the END (avoid overwriting)
   - Key insight: Fill from right → largest elements placed first, no shifting needed

8. **Move Zeroes** — Move all zeroes to the end while preserving order.
   - Pattern: Fast & Slow pointers (slow = next non-zero slot)
   - Key insight: Slow pointer tracks where next non-zero should go; fast scans ahead
   - // Move all non-zero elements to the front
        for (int i = 0; i < nums.length; i++) {
            if (nums[i] != 0) {
                nums[insertPos++] = nums[i];
            }
        }
        
        // Fill the remaining elements with zero
        while (insertPos < nums.length) {
            nums[insertPos++] = 0;
        }
     - fast - slow approach
        int slow = 0;
        
        // Fast pointer scans ahead
        for (int fast = 0; fast < nums.length; fast++) {
            if (nums[fast] != 0) {
                // Swap elements at slow and fast pointers
                int temp = nums[slow];
                nums[slow] = nums[fast];
                nums[fast] = temp;
                
                slow++; // Move slow pointer to the next spot
            }
        }

9. **Pair Sum in Sorted Array** — Find two numbers in a sorted array whose sum equals the target.
   - Pattern: Opposite-end two pointers
   - Invariant: Array is sorted → sum too big → move right left; sum too small → move left right

10. **Remove Duplicates from Sorted Array** — Remove duplicates in-place.
    - Pattern: Fast & Slow pointers
    - Invariant: Everything at index ≤ slow is the unique prefix
    - Key insight: Only advance slow when `nums[fast] != nums[slow]`
    - public int removeDuplicates(int[] nums) {
        if (nums.length == 0) return 0;
        
        int slow = 0; // Tracks the unique elements partition
        
        // Fast pointer scans ahead
        for (int fast = 1; fast < nums.length; fast++) {
            if (nums[fast] != nums[slow]) {
                slow++;
                nums[slow] = nums[fast]; // Move the unique element forward
            }
        }
        return slow + 1; // Number of unique elements
    }

11. **Longest Common Prefix** — Find the common prefix among an array of strings.
    - Pattern: Vertical scanning (char by char across all strings)
    - Key insight: If any string doesn't have the char at position i → stop
    -  // Initialize prefix with the first string
        String prefix = strs[0];
        
        // Compare prefix with every other string in the array
        for (int i = 1; i < strs.length; i++) {
            while (strs[i].indexOf(prefix) != 0) {
                // Shorten the prefix by one character from the end
                prefix = prefix.substring(0, prefix.length() - 1);
                
                // If prefix becomes empty, there is no common prefix
                if (prefix.isEmpty()) return "";
            }
        }
        return prefix;

   - common sub string
         public class CommonSubstring {
          public String longestCommonSubstring(String[] strs) {
              if (strs == null || strs.length == 0) return "";
              
              String first = strs[0];
              int len = first.length();
              
              // Check substrings of the first word, starting from the longest possible length
              for (int subLen = len; subLen > 0; subLen--) {
                  for (int start = 0; start <= len - subLen; start++) {
                      String candidate = first.substring(start, start + subLen);
                      
                      // Verify if this candidate exists in all other strings
                      boolean matchAll = true;
                      for (int i = 1; i < strs.length; i++) {
                          if (!strs[i].contains(candidate)) {
                              matchAll = false;
                              break;
                          }
                      }
                      
                      // Return the first (and therefore longest) match found
                      if (matchAll) {
                          return candidate;
                      }
                  }
              }
              return "";
          }
      }


12. **String Deletion Indices** — Return all indices whose deletion from the longer string makes it equal to the shorter string.
    - Example: `["adbsssc", "adbssc"]` → `[3, 4, 5]`
    - Pattern: Two pointers on both strings simultaneously
    - Invariant: `j` pointer on shorter string advances only on match; `i` on longer always advances
    - Key insight: When chars match → advance both; when mismatch → record index `i` as deletion candidate, advance only `i`
    - import java.util.ArrayList;
      import java.util.List;
      
      public class ValidDeletions {
          public List<Integer> findDeletionIndices(String longer, String shorter) {
              List<Integer> result = new ArrayList<>();
              
              // Edge case: lengths must differ by exactly 1 character
              if (longer == null || shorter == null || longer.length() != shorter.length() + 1) {
                  return result;
              }
      
              int n = shorter.length();
              int left = 0;
              int right = n - 1;
      
              // Scan from left to find the first mismatch
              while (left < n && longer.charAt(left) == shorter.charAt(left)) {
                  left++;
              }
      
              // Scan from right to find the first mismatch
              while (right >= 0 && longer.charAt(right + 1) == shorter.charAt(right)) {
                  right--;
              }
      
              // Collect all valid indices in the boundary range
              if (left > right) {
                  for (int i = right + 1; i <= left; i++) {
                      result.add(i);
                  }
              }
      
              return result;
          }
      }
      

---

## Week 3 — Sliding Window / Single Pass

**Core Idea:** Maintain a window over the array. Expand right, shrink left when constraint violated.

13. **Maximum Average Subarray I** — Find the maximum average of any subarray of size k.
    - Pattern: Fixed-size sliding window
    - Key insight: Slide by adding `nums[i]` and removing `nums[i - k]` — no need to recompute sum

14. **Maximum Number of Vowels in a Substring** — Find the maximum vowels in any substring of length k.
    - Pattern: Fixed-size sliding window with a counter
    - Key insight: Same sliding sum — just count vowels entering/leaving the window

15. **Best Time to Buy and Sell Stock** — Single pass to find max profit.
    - Pattern: Running Minimum (Single Pass)
    - Invariant: `minPrice` is the minimum price seen before the current day
    - Key insight: `profit = price[i] - minPrice`; update minPrice if `price[i] < minPrice`

16. **Minimum Size Subarray Sum** *(new)* — Find smallest subarray whose sum ≥ target.
    - Pattern: Variable-size sliding window (shrink when valid)
    - Invariant: Expand right until sum ≥ target, then shrink left to find minimum length
    - Key insight: Unlike fixed window, left pointer moves inward whenever the constraint is satisfied

---

## Week 4 — Binary Search

**Core Idea:** On every iteration, eliminate half the search space. Requires a monotonic (sorted) property.

17. **Binary Search** — Find a target in a sorted array.
    - Pattern: Standard binary search
    - Invariant: Target is always within `[left, right]` if it exists

18. **Search Insert Position** — Find the index where a target exists or should be inserted.
    - Pattern: Binary search — return `left` at termination
    - Key insight: When loop ends, `left` is where target would be inserted

19. **Guess Number Higher or Lower** — Guess a hidden number using binary search.
    - Pattern: Binary search on answer space
    - Key insight: API result tells you which half to eliminate — same as comparing `nums[mid]` to target

20. **Binary Search — First Occurrence (Duplicates)** — Find the first occurrence of a target.
    - Pattern: Binary search biased left
    - Key insight: On match, don't return immediately — record answer and continue searching left (`right = mid - 1`)

21. **Search in Rotated Sorted Array** — Search for a target in a rotated sorted array.
    - Pattern: Binary search with half-sorted invariant
    - Invariant: One half is ALWAYS sorted
    - Key insight: Identify which half is sorted → check if target falls in it → eliminate the other half

22. **Find Rotation Point / Find Minimum in Rotated Sorted Array** — Find the smallest element.
    - Pattern: Binary search comparing `mid` with `right`
    - Key insight: If `nums[mid] > nums[right]` → rotation point is in right half; else in left half

---

## Week 5 — Trees (DFS / BFS)

**Core Idea:** DFS (recursion/stack) for path/depth problems. BFS (queue) for level-order problems.

23. **Maximum Depth of Binary Tree** — Find the height of a binary tree.
    - Pattern: DFS post-order (compute children first, then parent)
    - Recurrence: `depth(node) = 1 + max(depth(left), depth(right))`

24. **Same Tree** — Determine whether two binary trees are identical.
    - Pattern: DFS simultaneous traversal
    - Key insight: Both null → true; one null → false; values differ → false; recurse both sides

25. **Binary Tree Level Order Traversal** — Traverse the tree level by level.
    - Pattern: BFS with a queue; snapshot queue size at start of each level
    - Key insight: `size = queue.size()` before inner loop → process exactly that many nodes per level

26. **Validate Binary Search Tree** *(new)* — Check if a tree is a valid BST.
    - Pattern: DFS with min/max bounds passed down
    - Invariant: Every node must satisfy `min < node.val < max`
    - Key insight: Pass `(node.left, min, node.val)` and `(node.right, node.val, max)` — don't just compare with children

27. **Lowest Common Ancestor of BST** *(new)* — Find the LCA of two nodes.
    - Pattern: DFS — use BST property to navigate
    - Key insight: If both p and q are less than node → go left; both greater → go right; else current node IS the LCA

28. **Binary Tree Right Side View** *(new)* — Return the last node visible at each level.
    - Pattern: BFS level order — capture last node of each level
    - Key insight: Same as level order traversal; just record `queue.peek()` (or last node) at each level end

29. **Path Sum** *(new)* — Determine if a root-to-leaf path exists with a given sum.
    - Pattern: DFS — subtract node value, check at leaf
    - Invariant: At each node, remaining = `targetSum - node.val`
    - Key insight: Base case = leaf node AND remaining == 0

---

## Week 6 — Linked Lists (Fast & Slow Pointers)

**Core Idea:** Two pointers at different speeds reveal midpoints and cycles without extra space.

30. **Middle of the Linked List** — Find the middle node.
    - Pattern: Fast & Slow pointers
    - Invariant: When fast reaches end, slow is at middle
    - Key insight: Fast moves 2 steps, slow moves 1 step

31. **Linked List Cycle** — Detect a cycle in a linked list.
    - Pattern: Floyd's cycle detection (fast & slow)
    - Key insight: If there's a cycle, fast will lap slow and they will meet

32. **Palindrome Linked List** — Determine whether a linked list is a palindrome.
    - Pattern: Find middle → reverse second half → compare both halves
    - Key insight: Three steps: find mid (fast/slow), reverse from mid, compare head with reversed half

---

## Week 7 — Stack / Monotonic Stack *(new)*

**Core Idea:** Stack for nested/matching problems. Monotonic stack (always increasing or decreasing) for "next greater/smaller" problems.

33. **Valid Parentheses** *(new)* — Check if brackets are balanced.
    - Pattern: Stack for matching pairs
    - Key insight: Push open brackets; on close bracket, check if top of stack is the matching open bracket
    - Edge case: Stack not empty at end → unmatched open brackets

34. **Daily Temperatures** *(new)* — For each day, find how many days until a warmer temperature.
    - Pattern: Monotonic Decreasing Stack (stores indices)
    - Invariant: Stack holds indices of days with no warmer day found yet
    - Key insight: When `temp[i] > temp[stack.top()]` → found the answer for stack.top(); pop and record `i - popped_index`

35. **Largest Rectangle in Histogram** *(new)* — Find the largest rectangle area in a histogram.
    - Pattern: Monotonic Increasing Stack
    - Invariant: Stack holds indices in order of increasing height
    - Key insight: When current bar is shorter than stack top → the top bar can't extend further right; calculate its max rectangle width using current index and next stack element as left boundary

---

## Week 8 — Backtracking *(new)*

**Core Idea:** Explore all possibilities by making a choice, recursing, then undoing (backtracking). Build the solution incrementally.

**Template:**
```
backtrack(current, remaining):
    if base case → record answer
    for each choice:
        make choice
        backtrack(updated, reduced)
        undo choice  ← this is the backtrack step
```

36. **Subsets** *(new)* — Return all subsets of an array.
    - Pattern: Backtracking — include or exclude each element
    - Key insight: At each index, two choices: include `nums[i]` or skip. No "undo" needed — just don't add to current path on skip branch

37. **Permutations** *(new)* — Return all permutations of an array.
    - Pattern: Backtracking with a `used[]` boolean array
    - Key insight: At each position, try every unused element. Mark used before recurse, unmark after (classic backtrack)

38. **Combination Sum** *(new)* — Find all combinations that sum to target (elements reusable).
    - Pattern: Backtracking with remaining sum
    - Invariant: `remaining` decreases with each choice; base case is `remaining == 0`
    - Key insight: Reuse allowed → don't advance start index after picking an element. If `remaining < 0` → prune

---

## Week 9 — Graphs (BFS / DFS on Grid & Adjacency List) *(new)*

**Core Idea:** Graphs generalize trees — add a `visited` set to avoid revisiting nodes. Grid problems are implicit graphs (cells = nodes, adjacency = up/down/left/right).

39. **Number of Islands** *(new)* — Count connected components of '1's in a grid.
    - Pattern: DFS/BFS flood fill on grid
    - Key insight: On finding '1', flood-fill the entire island (mark visited by setting to '0'), increment counter
    - Why it matters: Most common graph entry point in interviews — master this first

40. **Clone Graph** *(new)* — Deep copy a graph.
    - Pattern: BFS + HashMap (original node → cloned node)
    - Invariant: `map[node]` always points to the clone of that node
    - Key insight: HashMap prevents revisiting AND serves as the mapping from old to new nodes

41. **Course Schedule** *(new)* — Determine if you can finish all courses (cycle detection in directed graph).
    - Pattern: DFS with 3-color marking (WHITE=unvisited, GRAY=in-progress, BLACK=done)
    - Key insight: If you reach a GRAY node during DFS → cycle exists → return false
    - Alternative: Topological sort (Kahn's BFS) — if all nodes processed → no cycle

---

## Week 10 — Heap / Priority Queue *(new)*

**Core Idea:** Heap gives O(log n) insert and O(1) peek at min/max. Use min-heap of size K to track top-K largest elements.

42. **Kth Largest Element in an Array** *(new)* — Find the Kth largest element.
    - Pattern: Min-heap of size K
    - Key insight: Maintain a min-heap of the K largest seen so far. If new element > heap.top() → pop and push. Final heap.top() = Kth largest
    - Why min-heap for largest: The smallest of the K largest is at the top — easy to evict when a bigger one arrives

43. **Top K Frequent Elements** *(new)* — Return the K most frequent elements.
    - Pattern: HashMap frequency count + min-heap of size K
    - Key insight: Count frequencies with HashMap, then use min-heap keyed by frequency. Same K-window trick as above

---

## Week 11 — Dynamic Programming *(new)*

**Core Idea:** Break problem into subproblems. Store results to avoid recomputation. Define: what is `dp[i]`? What is the recurrence?

44. **Climbing Stairs** *(new)* — How many ways to climb n stairs (1 or 2 steps at a time)?
    - Pattern: 1D DP (Fibonacci)
    - Recurrence: `dp[i] = dp[i-1] + dp[i-2]`
    - Key insight: To reach stair i, you came from i-1 (1 step) or i-2 (2 steps). Base: `dp[1]=1, dp[2]=2`

45. **House Robber** *(new)* — Max money robbing houses without robbing adjacent ones.
    - Pattern: 1D DP with skip logic
    - Recurrence: `dp[i] = max(dp[i-1], dp[i-2] + nums[i])`
    - Key insight: At each house, choose: skip it (take `dp[i-1]`) or rob it (take `dp[i-2] + nums[i]`)
    - Space optimization: Only need last two values → use two variables instead of array

---

## Core Algorithm Reminders

### Pattern → Data Structure mapping

| Problem Type | Reach For |
|---|---|
| Complement / membership lookup | HashMap / HashSet |
| Sorted array, find pair | Two Pointers (opposite ends) |
| Subarray of size k | Fixed Sliding Window |
| Smallest/largest subarray satisfying condition | Variable Sliding Window |
| Sorted array, find target | Binary Search |
| Tree path / depth | DFS (recursion) |
| Tree level by level | BFS (queue) |
| Matching brackets / nesting | Stack |
| Next greater / smaller element | Monotonic Stack |
| All combinations / subsets / permutations | Backtracking |
| Connected components / shortest path | Graph BFS/DFS + visited set |
| Top K elements | Min-Heap of size K |
| Overlapping subproblems | Dynamic Programming |

---

### Key Invariants to Memorize

```
Two Sum              → map[complement] exists before current index
Sliding Window       → window [left, right] always satisfies the constraint
Binary Search        → target is always within [left, right] if it exists
Rotated Binary Search→ one half is ALWAYS sorted
Fast & Slow Pointers → when fast reaches end, slow is at middle
Floyd's Cycle        → if cycle exists, fast will meet slow
Validate BST         → every node must satisfy min < val < max (not just vs children)
Backtracking         → make choice → recurse → UNDO choice
Monotonic Stack      → stack top is always the "unresolved" candidate
Min-Heap of size K   → heap.top() is always the Kth largest seen so far
DP House Robber      → dp[i] = max(skip, rob) — never look back more than 2 steps
```

---

### Edge Cases That Trip People Up

```
Binary Search        → off-by-one: use left <= right, mid = left + (right - left) / 2
Tree LCA             → null checks before comparing values
Linked List Cycle    → always check fast != null AND fast.next != null before moving
Valid BST            → don't compare node with direct children — use min/max bounds
Backtracking         → forgetting to undo the choice (remove from current path)
Graph                → forgetting to mark visited BEFORE pushing to queue (causes duplicates)
Heap                 → min-heap for top-K largest (counterintuitive but correct)
DP                   → define dp[0] and dp[1] base cases explicitly before the loop
```

---

### Problem Count by Topic

| Topic | Problems | Status |
|---|---|---|
| HashMap / HashSet | 5 | ✅ Original |
| Two Pointers | 7 | ✅ Original + String Deletion |
| Sliding Window | 4 | ✅ + 1 added |
| Binary Search | 6 | ✅ Original |
| Trees | 7 | ✅ + 4 added |
| Linked Lists | 3 | ✅ Original |
| Stack / Monotonic Stack | 3 | 🆕 New |
| Backtracking | 3 | 🆕 New |
| Graphs | 3 | 🆕 New |
| Heap | 2 | 🆕 New |
| Dynamic Programming | 2 | 🆕 New |
| **Total** | **45** | |
