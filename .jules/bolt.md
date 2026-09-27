## 2024-05-25 - Avoid Dictionary Comprehensions in Tight Loops
**Learning:** In highly-executed loops like group_files_by_similarity, caching metadata via dict comprehension creates O(N) memory overhead and expensive dict insertions/lookups.
**Action:** Track previous element stats iteratively to achieve O(1) memory and >3x performance gain in tight similarity grouping loops.
