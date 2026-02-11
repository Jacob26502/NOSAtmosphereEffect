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


### 2\. Select Image & Playlist Mode
After selecting an effect, you will be prompted to choose your wallpaper mode:

* **Single Image:** Standard mode. Pick one image, crop it, and apply.

* **Multiple Images (Playlist):** Select multiple images from your gallery to create a Wallpaper Playlist.
* You can simply apply the play list as it or adjust & crop any image from the playlist you want.
* Once finished, the app will automatically rotate through these wallpapers based on your settings.


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
    * **MANDATORY:** You must select **ONLY "Home Screen"** (It'll also work fine for most of the devices if you select Home Screen and Lock Screen and always select Home Screen and Lock Screen in Playlist Mode).
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
### Playlist & Rotation
(Only available when using Multiple Images mode)
* **Rotation Interval:** Controls how often the wallpaper changes from your playlist.
    * **Options:** Every Lock (Instant), 15 Minutes, 1 Hour, up to 24 Hours.
    * **Smart Rotation:** To prevent lag or visual glitches, the wallpaper only rotates when the screen is OFF.
    * *Example:* If you set "15 Minutes", the app checks the time whenever you lock your phone. If 15 minutes have passed since the last change, it swaps the wallpaper in the background so it's ready the next time you unlock.

## Screenshots
<div align="center">
  <img src="https://github.com/user-attachments/assets/5ca9fd98-880a-4377-973b-9192771aa185" width="45%" alt="1st" />
  <img src="https://github.com/user-attachments/assets/e8bda8f0-821d-43c4-8194-421916560c64" width="45%" alt="2nd" />
  <br/>
  <img src="https://github.com/user-attachments/assets/160d8da4-fe29-40f2-90c1-74f0fc003fdc" width="45%" alt="3rd" />
  <img src="https://github.com/user-attachments/assets/97ce05a1-9315-4371-b3c6-741e3a15c54c" width="45%" alt="4th" />
</div>

## Telegram Group
I've made a telegram group for the discussion of issues and feature suggestion. You can join it using [this link](https://t.me/atmosphereEffect).

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

