import numpy as np
import cv2

def simulate_noise_correction():
    # 1. Create a clean sharp image (with a square)
    clean_sharp = np.zeros((400, 400), dtype=np.uint8)
    clean_sharp[100:300, 100:300] = 255
    
    # 2. Create a clean blurry image (gaussian blur on the sharp image)
    clean_blurry = cv2.GaussianBlur(clean_sharp, (25, 25), 0)
    
    # 3. Add same amount of high noise to both
    noise_std = 10.0
    noise = np.random.normal(0, noise_std, clean_sharp.shape)
    
    noisy_sharp = np.clip(clean_sharp.astype(float) + noise, 0, 255).astype(np.uint8)
    noisy_blurry = np.clip(clean_blurry.astype(float) + noise, 0, 255).astype(np.uint8)
    
    def analyze(img, name):
        # 1. Standard Laplacian Variance
        lap = cv2.Laplacian(img, cv2.CV_64F)
        raw_sharpness = lap.var()
        
        # 2. Gaussian Blur Preprocessing
        gauss = cv2.GaussianBlur(img, (3, 3), 0)
        gauss_sharpness = cv2.Laplacian(gauss, cv2.CV_64F).var()
        
        # 3. Bilateral Filter Preprocessing
        bilat = cv2.bilateralFilter(img, 5, 50, 50)
        bilat_lap = cv2.Laplacian(bilat, cv2.CV_64F)
        bilat_sharpness = bilat_lap.var()
        
        # Estimate noise on bilateral image
        bilat_mad = np.median(np.abs(bilat_lap - np.median(bilat_lap)))
        bilat_noise_sigma = bilat_mad / 0.6745
        bilat_noise_var = bilat_noise_sigma ** 2
        bilat_corrected = max(0.0, bilat_sharpness - bilat_noise_var)
        
        # 4. Median Blur Preprocessing
        med = cv2.medianBlur(img, 3)
        med_sharpness = cv2.Laplacian(med, cv2.CV_64F).var()

        # 5. MAD noise estimation on raw Laplacian
        mad = np.median(np.abs(lap - np.median(lap)))
        est_noise_sigma = mad / 0.6745
        est_noise_var = est_noise_sigma ** 2
        corrected_sharpness = max(0.0, raw_sharpness - est_noise_var)
        
        print(f"[{name}]")
        print(f"  Raw Sharpness:              {raw_sharpness:10.2f}")
        print(f"  Corrected (Raw-Noise):      {corrected_sharpness:10.2f}")
        print(f"  With Gaussian (3x3):        {gauss_sharpness:10.2f}")
        print(f"  With Bilateral:             {bilat_sharpness:10.2f}")
        print(f"  Bilateral + Noise Subtract: {bilat_corrected:10.2f} (noise_sigma={bilat_noise_sigma:.2f})")
        print(f"  With Median (3x3):          {med_sharpness:10.2f}")
        print()

    print(f"Simulating with added noise standard deviation: {noise_std}")
    print(f"Laplacian variance of pure noise of std {noise_std}: {20.0 * (noise_std ** 2):.2f}\n")
    
    analyze(clean_sharp, "Clean Sharp")
    analyze(clean_blurry, "Clean Blurry")
    analyze(noisy_sharp, "Noisy Sharp")
    analyze(noisy_blurry, "Noisy Blurry")

    # Performance benchmark
    import time
    large_img = np.random.randint(0, 255, (2000, 2000), dtype=np.uint8)
    
    t0 = time.time()
    cv2.Laplacian(large_img, cv2.CV_64F).var()
    t_raw = time.time() - t0
    
    t0 = time.time()
    bilat = cv2.bilateralFilter(large_img, 5, 50, 50)
    cv2.Laplacian(bilat, cv2.CV_64F).var()
    t_bilat = time.time() - t0
    
    t0 = time.time()
    gauss = cv2.GaussianBlur(large_img, (3, 3), 0)
    cv2.Laplacian(gauss, cv2.CV_64F).var()
    t_gauss = time.time() - t0
    
    print("Performance (2000x2000 grayscale):")
    print(f"  Raw Laplacian:       {t_raw*1000:8.2f} ms")
    print(f"  Bilateral + Lap:     {t_bilat*1000:8.2f} ms")
    print(f"  Gaussian + Lap:      {t_gauss*1000:8.2f} ms")


if __name__ == "__main__":
    simulate_noise_correction()
