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

* **Original Atmosphere:** Signature style. Drifting ambient atmospheric clouds that transition to blur when unlocked.
* **Reverse Atmosphere:** Mysterious reveal. Deep ambient clouds fade to a sharp clear view when unlocked.
* **Simple Frosted:** Modern minimalism. Applies a clean, uniform frosted glass blur (no clouds).
* **Simple Frosted (Reverse):** Elegant clarity. Wakes up from a heavy frosted blur into a crystal clear wallpaper.

### 2\. Select & Align Image

After selecting an effect, pick an image from your gallery. Use gestures to pinch-to-zoom and drag the image to your desired position. What you see inside the view is exactly how the wallpaper will be cropped. Once satisfied, press **"Apply"**.

### 3\. Application & Activation

The setup process differs slightly depending on which effect you selected. Please follow the instructions for your chosen mode:

#### 🅰️ If you chose "Sharp -> Blur Effect":

1.  **Configure Options:** A dialog will appear with two checkboxes:
    * **Option 1: Set Static Lock Screen**
        * *What it does:* Sets the cropped image as your system Lock Screen wallpaper.
        * *⚠️ Samsung Users:* Enabling this may cause the Adaptive Clock to stop working. If you need the adaptive clock, uncheck this and set the lock screen manually later.
    * **Option 2: Save Copy to Gallery**
        * *What it does:* Saves the cropped image to `Pictures/Atmosphere` for backup.
2.  **Activate Live Wallpaper:**
    * The app will redirect you to the Android System's Live Wallpaper preview.
    * Tap **"Set Wallpaper"**.
    * **MANDATORY:** You must select **ONLY "Home Screen"** (It'll also work fine for most of the devices if you select Home Screen and Lock Screen).
    * *> Why? The Original effect works by transitioning from a static Lock Screen image to the Live Wallpaper on the Home Screen.*

#### 🅱️ If you chose "Blur -> Sharp Effect":

1.  **Proceed to Preview:** You will be prompted to apply the wallpaper.
2.  **Activate Live Wallpaper:**
    * The app will redirect you to the Android System's Live Wallpaper preview.
    * Tap **"Set Wallpaper"**.
    * **MANDATORY:** You must select **"Home Screen and Lock Screen"**.
    * *> Why? The Reverse effect runs entirely as a live wallpaper to handle the blurred state while the phone is locked.*
## Advanced Customization
Take full control of the animation and look. You can now tweak the following settings dynamically:
### Visual Adjustments
* **Dimness Level:** Adjust the darkening overlay to ensure your home screen icons remain readable against bright wallpapers.
* **Blur Strength:** (Frosted Effects Only) Use the slider to fine-tune the intensity of the blur radius, from a light mist to heavy glass.
* **Experimental Grain (Noise):** Enable a film-grain texture on top of the blur. You can customize:
* **Noise Strength:** How visible the grain is.
* **Noise Scale:** The size/coarseness of the grain particles.
### Animation & Behavior
* **Animation Duration:** Control the total transition duration.
* **Lock Delay (Anti-Flicker):** Adds a configurable pause before the wallpaper resets when you lock the phone. This prevents the visual glitch where the wallpaper "snaps" back to its initial state before the screen turns fully black.
* **Unlock Check Interval:** Adjusts how frequently the app detects unlock events. Tuning this eliminates "delayed start" issues, ensuring the animation begins immediately when you wake your device.

## Known Issues

* **Samsung Adaptive Clock:** As mentioned, programmatically setting the lock screen interferes with Samsung's Adaptive Clock on OneUI.

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

