import struct

def create_gradient_bmp(filename, width, height, start_color, end_color):
    # Colors are in (R, G, B) format
    # Start color at top, End color at bottom
    
    row_size = (width * 3 + 3) & ~3  # Align to 4 bytes
    pixel_data_size = row_size * height
    file_size = 54 + pixel_data_size
    
    # BMP Header (14 bytes)
    bmp_header = struct.pack('<2sIHHI', b'BM', file_size, 0, 0, 54)
    
    # DIB Header (40 bytes)
    dib_header = struct.pack('<IiiHHIIiiII', 
                             40,         # Header Size
                             width,      # Width (signed)
                             height,     # Height (signed)
                             1,          # Planes
                             24,         # Bits per pixel
                             0,          # Compression (BI_RGB)
                             pixel_data_size, # Image size
                             2835,       # X pixels per meter (72 DPI)
                             2835,       # Y pixels per meter (72 DPI)
                             0,          # Colors in color table
                             0           # Important colors
                            )
    
    with open(filename, 'wb') as f:
        f.write(bmp_header)
        f.write(dib_header)
        
        # BMP pixels are stored bottom-up, left-to-right
        # Let's write the rows
        for y in range(height):
            # Interpolate color
            t = y / (height - 1) if height > 1 else 0.0
            r = int(start_color[0] * (1 - t) + end_color[0] * t)
            g = int(start_color[1] * (1 - t) + end_color[1] * t)
            b = int(start_color[2] * (1 - t) + end_color[2] * t)
            
            # Clamp values
            r = max(0, min(255, r))
            g = max(0, min(255, g))
            b = max(0, min(255, b))
            
            # In BMP, color channels are written as B, G, R
            row = bytearray()
            for x in range(width):
                # Add some subtle decorative horizontal noise or patterns for a premium touch
                # e.g., a very light wave pattern
                wave = int(5 * (x / width)) if x % 10 == 0 else 0
                nr = max(0, min(255, r + wave))
                ng = max(0, min(255, g + wave))
                nb = max(0, min(255, b + wave))
                
                row.append(nb)
                row.append(ng)
                row.append(nr)
                
            # Padding to 4 bytes
            padding_len = row_size - (width * 3)
            row.extend([0] * padding_len)
            
            f.write(row)

print("Generating premium vertical wallpapers...")
# Generate 5 high-res (1080x1920) gorgeous uncompressed wallpapers
# Approx 6.2MB each. Total ~31MB of premium high-res wallpapers!
create_gradient_bmp("app/src/main/assets/wallpapers/sunset_glow.bmp", 1080, 1920, (255, 90, 95), (100, 30, 80))
create_gradient_bmp("app/src/main/assets/wallpapers/midnight_neon", 1080, 1920, (15, 12, 75), (120, 20, 150))
create_gradient_bmp("app/src/main/assets/wallpapers/emerald_forest.bmp", 1080, 1920, (10, 45, 30), (50, 200, 120))
create_gradient_bmp("app/src/main/assets/wallpapers/royal_purple.bmp", 1080, 1920, (40, 20, 80), (140, 30, 220))
create_gradient_bmp("app/src/main/assets/wallpapers/ocean_breeze.bmp", 1080, 1920, (0, 150, 200), (5, 20, 60))

print("Completed generating wallpapers!")
