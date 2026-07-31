## 2024-05-24 - Pre-computing GUI Listbox Elements
**Learning:** O(N * M) complexities easily hide in GUI render loops when item formats dynamically query grouped lists, especially during bulk insertions where `listbox.insert("end", ...)` uses list comprehensions.
**Action:** When inserting many items into a Tkinter listbox that require looking up properties from external grouping objects, pre-compute a lookup dictionary (`O(1)`) once before the loop rather than re-evaluating the group for every item (`O(M)`).
