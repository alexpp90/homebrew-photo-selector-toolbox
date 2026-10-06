## 2026-10-06 - Reusing OpenCV Mat Objects in Inner Loops
**Learning:** Instantiating native OpenCV Mat objects (Mat, MatOfDouble) inside hot loops causes excessive JNI allocation overhead and GC thrashing.
**Action:** Allocate intermediate OpenCV Mat objects once outside the loop and pass them into helper functions, releasing them in a finally block.
