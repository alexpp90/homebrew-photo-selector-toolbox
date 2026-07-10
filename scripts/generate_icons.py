import os
import cairosvg
from PIL import Image

def generate_icons():
    svg_path = 'assets/logo.svg'
    png_path = 'assets/logo.png'
    ico_path = 'assets/logo.ico'
    icns_path = 'assets/logo.icns'
    
    if not os.path.exists(svg_path):
        print(f"Error: {svg_path} does not exist.")
        return
        
    print(f"Rendering {svg_path} to {png_path}...")
    cairosvg.svg2png(url=svg_path, write_to=png_path)
    
    print("Generating .ico and .icns files...")
    img = Image.open(png_path)
    
    # Generate ICO
    img.save(ico_path, format='ICO', sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print(f"Saved {ico_path}")
    
    # Generate ICNS
    img.save(icns_path, format='ICNS')
    print(f"Saved {icns_path}")
    
    # Densities and scale factors
    densities = {
        'mipmap-mdpi': 1.0,
        'mipmap-hdpi': 1.5,
        'mipmap-xhdpi': 2.0,
        'mipmap-xxhdpi': 3.0,
        'mipmap-xxxhdpi': 4.0
    }
    
    res_bases = [
        'android/app/src/main/res',
        'android/phototok/src/main/res'
    ]
    
    for res_base in res_bases:
        print(f"Generating launcher icons in {res_base}...")
        for folder, scale in densities.items():
            folder_path = os.path.join(res_base, folder)
            os.makedirs(folder_path, exist_ok=True)
            
            # 1. Legacy Launcher Icon (48 * scale)
            legacy_size = int(48 * scale)
            legacy_img = img.resize((legacy_size, legacy_size), Image.Resampling.LANCZOS)
            
            legacy_img.save(os.path.join(folder_path, 'ic_launcher.png'))
            legacy_img.save(os.path.join(folder_path, 'ic_launcher_round.png'))
            print(f"  Generated legacy launcher icons in {folder} ({legacy_size}x{legacy_size})")
            
            # 2. Adaptive Foreground Icon (108 * scale canvas, 72 * scale logo centered)
            canvas_size = int(108 * scale)
            logo_size = int(72 * scale)
            
            foreground_logo = img.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
            
            # Create transparent canvas
            canvas = Image.new('RGBA', (canvas_size, canvas_size), (0, 0, 0, 0))
            offset = (canvas_size - logo_size) // 2
            canvas.paste(foreground_logo, (offset, offset), foreground_logo)
            
            canvas.save(os.path.join(folder_path, 'ic_launcher_foreground.png'))
            print(f"  Generated adaptive foreground icon in {folder} ({canvas_size}x{canvas_size})")

if __name__ == '__main__':
    generate_icons()
