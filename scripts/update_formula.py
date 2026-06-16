#!/usr/bin/env python3
import argparse
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description="Update Homebrew formula/cask files")
    parser.add_argument("--file", required=True, help="Path to the Ruby file to update")
    parser.add_argument("--version-val", help="New version value to set (e.g. 0.1.0)")
    parser.add_argument("--macos-sha256", help="New macOS SHA256 hash")
    parser.add_argument("--linux-sha256", help="New Linux SHA256 hash")
    args = parser.parse_args()

    file_path = Path(args.file)
    if not file_path.exists():
        print(f"Error: File {file_path} does not exist.")
        return 1

    content = file_path.read_text()
    lines = content.splitlines()
    new_lines = []

    for line in lines:
        updated_line = line
        
        # Check for version replacement
        if args.version_val and line.strip().startswith('version '):
            # Keep the indentation
            indent = line[:len(line) - len(line.lstrip())]
            updated_line = f'{indent}version "{args.version_val}"'
        
        # Check for macOS sha256 replacement
        elif args.macos_sha256 and '# macos_sha256' in line:
            indent = line[:len(line) - len(line.lstrip())]
            updated_line = f'{indent}sha256 "{args.macos_sha256}" # macos_sha256'
            
        # Check for Linux sha256 replacement
        elif args.linux_sha256 and '# linux_sha256' in line:
            indent = line[:len(line) - len(line.lstrip())]
            updated_line = f'{indent}sha256 "{args.linux_sha256}" # linux_sha256'

        new_lines.append(updated_line)

    new_content = "\n".join(new_lines) + ("\n" if content.endswith("\n") else "")
    file_path.write_text(new_content)
    print(f"Successfully updated {file_path}")
    return 0

if __name__ == "__main__":
    import sys
    sys.exit(main())
