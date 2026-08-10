# YOLOv8 Model Setup

## Required Files

To use YOLOv8 object detection, you need to place the following files in this `assets` directory:

### 1. YOLOv8 Model File
Choose one of the following models based on your performance/accuracy requirements:

- **yolov8n.tflite** (Nano - ~6MB) - Fastest, lowest accuracy
- **yolov8s.tflite** (Small - ~22MB) - Good balance of speed and accuracy  
- **yolov8m.tflite** (Medium - ~52MB) - Better accuracy, moderate speed
- **yolov8l.tflite** (Large - ~87MB) - High accuracy, slower
- **yolov8x.tflite** (Extra Large - ~136MB) - Highest accuracy, slowest

### 2. Labels File
The `labels.txt` file is already present and contains COCO dataset class names.

## How to Get YOLOv8 TensorFlow Lite Models

1. **From Ultralytics (Official)**:
   ```bash
   pip install ultralytics
   yolo export model=yolov8n.pt format=tflite
   ```

2. **Pre-converted models**: Download from Ultralytics releases or model zoo

3. **Custom trained models**: Export your custom YOLOv8 model using Ultralytics

## Model Configuration

After adding the model file:

1. Update `Constants.kt` to use the correct model filename:
   ```kotlin
   const val MODEL_PATH = "yolov8n.tflite"  // or your chosen model
   ```

2. The current implementation supports:
   - Input size: 640x640 (adjustable in Constants)
   - Output format: Standard YOLOv8 format
   - COCO dataset classes (80 classes)

## Current Status

- ✅ Detection.kt - Data class for detection results
- ✅ YOLOv8Preprocessor.kt - Letterboxing and preprocessing utilities  
- ✅ Detector.kt - Updated with suspend function and YOLOv8 processing
- ✅ labels.txt - COCO class names
- ❌ YOLOv8 TensorFlow Lite model (need to add)

## Notes

- The current `model.tflite` is a placeholder and may not be optimized for YOLOv8
- Letterboxing preprocessing maintains aspect ratio and improves accuracy
- NMS (Non-Maximum Suppression) is implemented for duplicate removal
- The suspend function allows for non-blocking detection calls
