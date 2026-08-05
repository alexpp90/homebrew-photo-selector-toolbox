import os
from PIL import Image

def generate_icons():
    logo_path = 'assets/logo.png'
    res_base = 'android/app/src/main/res'
    
    if not os.path.exists(logo_path):
        print(f"Error: {logo_path} does not exist.")
        return
        
    img = Image.open(logo_path)
    
    # Scale factors:
    # mdpi: 1.0, hdpi: 1.5, xhdpi: 2.0, xxhdpi: 3.0, xxxhdpi: 4.0
    densities = {
        'mipmap-mdpi': 1.0,
        'mipmap-hdpi': 1.5,
        'mipmap-xhdpi': 2.0,
        'mipmap-xxhdpi': 3.0,
        'mipmap-xxxhdpi': 4.0
    }
    
    for folder, scale in densities.items():
        folder_path = os.path.join(res_base, folder)
        os.makedirs(folder_path, exist_ok=True)
        
        # 1. Legacy Launcher Icon (48 * scale)
        legacy_size = int(48 * scale)
        legacy_img = img.resize((legacy_size, legacy_size), Image.Resampling.LANCZOS)
        
        legacy_img.save(os.path.join(folder_path, 'ic_launcher.png'))
        legacy_img.save(os.path.join(folder_path, 'ic_launcher_round.png'))
        print(f"Generated legacy launcher icon in {folder} ({legacy_size}x{legacy_size})")
        
        # 2. Adaptive Foreground Icon (108 * scale canvas, 72 * scale logo centered)
        canvas_size = int(108 * scale)
        logo_size = int(72 * scale)
        
        foreground_logo = img.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
        
        # Create transparent canvas
        canvas = Image.new('RGBA', (canvas_size, canvas_size), (0, 0, 0, 0))
        offset = (canvas_size - logo_size) // 2
        canvas.paste(foreground_logo, (offset, offset), foreground_logo)
        
        canvas.save(os.path.join(folder_path, 'ic_launcher_foreground.png'))
        print(f"Generated adaptive foreground icon in {folder} ({canvas_size}x{canvas_size}, logo {logo_size}x{logo_size})")

if __name__ == '__main__':
    generate_icons()
