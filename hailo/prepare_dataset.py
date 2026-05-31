import os
import argparse
import numpy as np
from PIL import Image
from glob import glob

def prepare_calibration_data(image_dir, output_path, input_size=(640, 640), num_images=1024):
    """
    Reads images using PIL, resizes them, and saves as a .npy file.
    
    Args:
        image_dir (str): Path to directory containing images.
        output_path (str): Path to save the .npy file.
        input_size (tuple): Target size as (width, height).
        num_images (int): Number of images to include in the calibration set.
    """
    # Collect image paths
    extensions = ("*.jpg", "*.jpeg", "*.png")
    image_files = []
    for ext in extensions:
        image_files.extend(glob(os.path.join(image_dir, ext)))

    if not image_files:
        print(f"No images found in {image_dir}")
        return

    # Shuffle and subset
    np.random.shuffle(image_files)
    image_files = image_files[:num_images]
    
    calib_data = []
    
    print(f"Processing {len(image_files)} images with PIL...")
    
    for img_path in image_files:
        try:
            with Image.open(img_path) as img:
                # Ensure 3-channel RGB (removes Alpha channel or converts Grayscale)
                img = img.convert("RGB")
                
                # Resize using Lanczos interpolation for high quality
                # PIL expects (width, height)
                img_resized = img.resize(input_size, Image.Resampling.LANCZOS)
                
                # Convert to numpy array and float32
                # Resulting shape: (H, W, C)
                img_array = np.array(img_resized).astype(np.float32)
                
                calib_data.append(img_array)
        except Exception as e:
            print(f"Could not process {img_path}: {e}")
    
    if not calib_data:
        print("No images were successfully processed.")
        return

    # Create the 4D array: (N, H, W, C)
    calib_array = np.stack(calib_data)
    
    np.save(output_path, calib_array)
    print(f"Successfully saved to {output_path}")
    print(f"Final shape: {calib_array.shape}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Prepare calibration data for model quantization")
    parser.add_argument("data_dir", help="Path to directory containing images")
    parser.add_argument("--save-name", default="libreyolox_calib.npy", help="Name of the output .npy file")
    parser.add_argument("--input-wh", type=int, nargs=2, default=[640, 640], help="Model input width and height (default: 640 640)")
    parser.add_argument("--num-images", type=int, default=1024, help="Number of images to include (default: 1024)")
    
    args = parser.parse_args()
    
    prepare_calibration_data(args.data_dir, args.save_name, input_size=tuple(args.input_wh), num_images=args.num_images)
