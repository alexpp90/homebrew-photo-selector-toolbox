import timeit
import pathlib
import sys

def setup_benchmark(num_files):
    files = [pathlib.Path(f"file_{i}.jpg") for i in range(num_files)]
    errors = [Exception(f"Error {i}") for i in range(num_files)]
    failed_files = list(zip(files, errors))
    target_path = pathlib.Path(f"file_{num_files-1}.jpg")
    return target_path, failed_files

target_path, failed_files = setup_benchmark(100)

def test_list_comprehension():
    return target_path in [f for f, e in failed_files]

def test_set_comprehension():
    return target_path in {f for f, e in failed_files}

def test_generator_any():
    return any(f == target_path for f, _ in failed_files)

if __name__ == "__main__":
    n = 100000
    list_time = timeit.timeit(test_list_comprehension, number=n)
    set_time = timeit.timeit(test_set_comprehension, number=n)
    gen_time = timeit.timeit(test_generator_any, number=n)

    print("--- Benchmark Results (100 items, target at end) ---")
    print(f"Baseline (List Comprehension): {list_time:.4f}s")
    print(f"Set Comprehension: {set_time:.4f}s")
    print(f"Optimization (Generator any()): {gen_time:.4f}s")
    print(f"Improvement (Any vs List): {(list_time - gen_time) / list_time * 100:.2f}%")
