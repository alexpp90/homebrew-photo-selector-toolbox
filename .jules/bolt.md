## 2024-05-18 - First entry
## 2024-05-18 - Optimized cv2.Laplacian precision
**Learning:** Using `cv2.CV_64F` for Laplacian calculation takes significantly longer than `cv2.CV_32F` on large images without a noticeable benefit in standard sharpness or noise measurement.
**Action:** When calculating Laplacian variance or noise on typical photographs, always use `cv2.CV_32F`. Ensure the result is cast to a standard `float` using `float()` because NumPy might return `numpy.float32`, which can break type expectations.
