import numpy as np
import timeit
from PIL import Image

def calculate_dhash_old(image: Image.Image, hash_size: int = 8) -> int:
    resized = image.resize(
        (hash_size + 1, hash_size), Image.Resampling.BILINEAR
    ).convert("L")
    pixels = np.array(resized)

    diff = pixels[:, :-1] > pixels[:, 1:]

    flat = diff.flatten()
    total_bits = hash_size * hash_size
    packed = np.packbits(flat)
    extra_bits = len(packed) * 8 - total_bits
    result = int.from_bytes(packed.tobytes(), "big") >> extra_bits
    return result

img = Image.new('RGB', (150, 150), color='white')

print(timeit.timeit(lambda: calculate_dhash_old(img), number=10000))
