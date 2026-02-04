# NOS Atmosphere Effect

**NOS Atmosphere Effect** is an Android application designed to replicate the distinctive "Atmosphere" transition effect found in Nothing OS.
## ⚠️ Device Support & Disclaimer

**Current Testing Status:**
This application has currently been tested **exclusively on the Samsung Galaxy S25 Ultra**.

While it may work on other Android devices running Android 13+ (API 33+), behavior on different manufacturers' skins (FuntouchOS, OxygenOS, etc.) is not guaranteed.

## Usage Guide

Follow these steps to set up the effect properly on your device.

### 1\. Select Your Effect

Open the app and choose your desired atmosphere style from the selection screen:

* **Original Atmosphere:** The wallpaper is original sharp image initially and then transitions to blurred version when unlocked (Classic Nothing OS style).
* **Reverse Atmosphere:** The wallpaper is blurred initially and then sharpens back to original image when you unlock (Misty wake-up effect).

### 2\. Select & Align Image

After selecting an effect, pick an image from your gallery. Use gestures to pinch-to-zoom and drag the image to your desired position. What you see inside the view is exactly how the wallpaper will be cropped. Once satisfied, press **"Apply"**.

### 3\. Application & Activation

The setup process differs slightly depending on which effect you selected. Please follow the instructions for your chosen mode:

#### 🅰️ If you chose "Original Atmosphere":

1.  **Configure Options:** A dialog will appear with two checkboxes:
    * **Option 1: Set Static Lock Screen**
        * *What it does:* Sets the cropped image as your system Lock Screen wallpaper.
        * *⚠️ Samsung Users:* Enabling this may cause the Adaptive Clock to stop working. If you need the adaptive clock, uncheck this and set the lock screen manually later.
    * **Option 2: Save Copy to Gallery**
        * *What it does:* Saves the cropped image to `Pictures/Atmosphere` for backup.
2.  **Activate Live Wallpaper:**
    * The app will redirect you to the Android System's Live Wallpaper preview.
    * Tap **"Set Wallpaper"**.
    * **MANDATORY:** You must select **ONLY "Home Screen"**.
    * *> Why? The Original effect works by transitioning from a static Lock Screen image to the Live Wallpaper on the Home Screen.*

#### 🅱️ If you chose "Reverse Atmosphere":

1.  **Proceed to Preview:** You will be prompted to apply the wallpaper.
2.  **Activate Live Wallpaper:**
    * The app will redirect you to the Android System's Live Wallpaper preview.
    * Tap **"Set Wallpaper"**.
    * **MANDATORY:** You must select **"Home Screen and Lock Screen"**.
    * *> Why? The Reverse effect runs entirely as a live wallpaper to handle the blurred state while the phone is locked.*

## Known Issues

* **Samsung Adaptive Clock:** As mentioned, programmatically setting the lock screen interferes with Samsung's Adaptive Clock on OneUI.
* **Device Compatibility:** Only verified on S25 Ultra.

## Build & Installation

This project is built using Kotlin and Gradle.

1.  Clone the repository.
2.  Open in Android Studio (Ladybug or newer recommended).
3.  Sync Gradle.
4.  Build and Run on your device.

<!-- end list -->

```bash
git clone https://github.com/yourusername/NOSAtmosphereEffect.git
```

## Author

**Saad Ullah Khan**
📍 Saudi Arabia
📧 [khansaad45678900@gmail.com](mailto:khansaad45678900@gmail.com)
🔗 [LinkedIn](https://www.linkedin.com/in/saadullahkhan456)
💻 [GitHub](https://github.com/saad-khan-rind)
📄 [Download Resume](https://drive.usercontent.google.com/u/0/uc?id=1tj_Cz6jpkkibTZ4Ed-ReYybzOUu6k4Vw&export=download)

## License

This project is open-source and available under the [MIT License](LICENSE).

---

⭐️ **Feel free to fork, star, and use this code!**

---

