# DSA Problem Tracker

## Week 1 -- HashMap / HashSet

1.  **Two Sum** -- Find two numbers whose sum equals a target.
2.  **Contains Duplicate** -- Determine if an array contains duplicate
    values.
3.  **Valid Anagram** -- Check whether two strings are anagrams.
4.  **Longest Substring Without Repeating Characters** -- Find the
    longest substring with unique characters using a HashMap.
5.  **Happy Number** -- Determine whether a number is happy by detecting
    cycles.

## Week 2 -- Two Pointers

6.  **Valid Palindrome** -- Check if a string is a palindrome ignoring
    non-alphanumeric characters.
7.  **Merge Sorted Array** -- Merge two sorted arrays in-place.
8.  **Move Zeroes** -- Move all zeroes to the end while preserving
    order.
9.  **Pair Sum in Sorted Array** -- Find two numbers in a sorted array
    whose sum equals the target.
10. **Remove Duplicates from Sorted Array** -- Remove duplicates
    in-place using fast and slow pointers.
11. **Longest Common Prefix** -- Find the common prefix among an array
    of strings.

## Week 3 -- Sliding Window / Single Pass

12. **Maximum Average Subarray I** -- Find the maximum average of any
    subarray of size k.
13. **Maximum Number of Vowels in a Substring** -- Find the maximum
    vowels in any substring of length k.
14. **Best Time to Buy and Sell Stock** -- Keep a running minimum price
    and update the maximum profit.

-   Pattern: Running Minimum (Single Pass)
-   Invariant: `minPrice` is the minimum price seen before the current
    day.

## Week 4 -- Binary Search

15. **Binary Search** -- Find a target in a sorted array.
16. **Search Insert Position** -- Find the index where a target exists
    or should be inserted.
17. **Guess Number Higher or Lower** -- Guess a hidden number using
    binary search.
18. **Binary Search -- First Occurrence (Duplicates)** -- Find the first
    occurrence of a target.
19. **Search in Rotated Sorted Array** -- Search for a target in a
    rotated sorted array.
20. **Find Rotation Point / Find Minimum in Rotated Sorted Array** --
    Find the index/value of the smallest element using binary search.

## Week 5 -- Trees (DFS/BFS)

21. **Maximum Depth of Binary Tree** -- Find the height of a binary
    tree.
22. **Same Tree** -- Determine whether two binary trees are identical.
23. **Binary Tree Level Order Traversal** -- Traverse the tree level by
    level.

## Week 6 -- Linked Lists (Fast & Slow Pointers)

24. **Middle of the Linked List** -- Find the middle node.
25. **Linked List Cycle** -- Detect a cycle in a linked list.
26. **Palindrome Linked List** -- Determine whether a linked list is a
    palindrome.

## Additional Problem

27. **String Deletion Indices** -- Return all indices whose deletion
    from the longer string makes it equal to the shorter string.
    Ex : ip ["adbsssc","adbssc"] op [3,4,5] deleting one char from long str in any this pos to get the smaller string
    solve with two pointers 

------------------------------------------------------------------------

## Core Algorithm Reminders

-   Two Sum → HashMap lookup
-   Longest Substring Without Repeating Characters → Sliding Window +
    HashMap
-   Pair Sum in Sorted Array → Two Pointers
-   Remove Duplicates from Sorted Array → Fast & Slow Pointers
-   Best Time to Buy and Sell Stock → Keep a running minimum and update
    profits
-   Binary Search with Duplicates → Bias search left after finding
    target
-   Search in Rotated Sorted Array → One half is always sorted
-   Find Rotation Point → Compare `mid` with `right`
