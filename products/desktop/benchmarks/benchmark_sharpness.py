import cv2
import numpy as np
import time

def bench_64(gray):
    start = time.time()
    laplacian = cv2.Laplacian(gray, cv2.CV_64F)
    score = laplacian.var()
    mad = np.median(np.abs(laplacian - np.median(laplacian)))
    return time.time() - start

def bench_32(gray):
    start = time.time()
    laplacian = cv2.Laplacian(gray, cv2.CV_32F)
    score = laplacian.var()
    mad = np.median(np.abs(laplacian - np.median(laplacian)))
    return time.time() - start

gray = np.random.randint(0, 256, (4000, 6000), dtype=np.uint8)

t64 = 0
t32 = 0
for _ in range(5):
    t64 += bench_64(gray)
    t32 += bench_32(gray)

print(f"64F: {t64/5:.4f} s")
print(f"32F: {t32/5:.4f} s")
