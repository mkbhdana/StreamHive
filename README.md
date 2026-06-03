<p align="center">
  <img src="https://github.com/user-attachments/assets/5383f0e0-724d-4b09-8906-354f1c03b0a8" align="center" width="128" />
<p>
<h1 align="center">
  StreamHive
</h1>
<p align="center">
Stream your video files directly from Google Drive with this specialized client. Unlike the official app, this app streams the original files for a better viewing experience. Download now and start streaming your favorite videos from Google Drive.
</p>

<p align="center">
  This app is powered by <a href="https://developers.google.com/drive/api">Drive API</a>, <a href="https://github.com/google/ExoPlayer">ExoPlayer</a> and <a href="https://github.com/abdallahmehiz/mpv-android">mpv-android</a>.
</p>

<div align="center">
  <a href="https://github.com/mkbhdana/StreamHive/releases/latest">
    <img alt="GitHub release" src="https://img.shields.io/github/v/release/mkbhdana/StreamHive?style=flat-square">
  </a>
  
<img alt="GitHub Downloads (all assets, all releases)" src="https://img.shields.io/github/downloads/mkbhdana/Streamhive/total?style=flat-square&link=https%3A%2F%2Fgithub.com%2Fmkbhdana%2FStreamHive%2Freleases">

  <a href="https://github.com/mkbhdana/StreamHive/blob/master/LICENSE">
    <img alt="GitHub License" src="https://img.shields.io/github/license/mkbhdana/StreamHive?style=flat-square">
  </a>
</div>

**Key Feature**

- Simpler and Easier to Use UI
- Material3 Expressive Design
- Enhanced Playback Features
- High-Quality Rendering
- Completely free and open source and without any ads or excessive permissions
- External Subtitle support
- Zoom gesture
- Search Functionality

**This project is still in development and is expected to have bugs. Please report any bugs you find in
the [Issues](https://github.com/mkbhdana/StreamHive/issues) section.**

<!-- <div align="center">
    <a href="https://github.com/mkbhdana/StreamHive/releases">

  <img alt="Download count" src="https://img.shields.io/github/downloads/mkbhdana/StreamHive/total?style=for-the-badge">
  </a>
      <a href="https://github.com/mkbhdana/StreamHive/latest">
    <img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/mkbhdana/StreamHive?style=for-the-badge">
  </a>
  <a href="https://github.com/mkbhdana/StreamHive/blob/master/LICENSE.txt">
    <img alt="GitHub" src="https://img.shields.io/github/license/mkbhdana/StreamHive?style=for-the-badge">
  </a>
  <img alt="Codefactor rating" src="https://img.shields.io/codefactor/grade/github/mkbhdana/StreamHive/master?style=for-the-badge">
</div> -->

<br>

## Showcase

<div class="image-row" align="center">
  <img src="https://github.com/user-attachments/assets/031ba7a3-c446-4761-940d-495469a9923e" width="98%" />
</div>

<div class="image-row" align="center" justify-content="space-between">
  <img src="https://github.com/user-attachments/assets/1b3746a0-25ab-4a51-806a-40e34d155dde" width="23.5%"/>
  <img src="https://github.com/user-attachments/assets/12a67155-5449-4b3f-beeb-aa9f6535e446" width="23.5%"/>
  <img src="https://github.com/user-attachments/assets/a41c1b4d-3f43-4860-a39b-f4903bec3da4" width="23.5%"/>
   <img src="https://github.com/user-attachments/assets/8ae98084-c982-49c1-aed9-739557b4f670" width="23.5%"/>
</div>

<div class="image-row" align="center" justify-content="space-between">
  
  <img src="https://github.com/user-attachments/assets/7afb8faf-db5c-468c-b908-a154f829e750" width="23.5%"/>
  <img src="https://github.com/user-attachments/assets/a408d657-3d17-433f-9993-647e7dc07bfd" width="23.5%"/>
  <img src="https://github.com/user-attachments/assets/8ef6b333-d9ef-4c60-8cb3-c89f1bfe15fb" width="23.5%"/>
  <img src="https://github.com/user-attachments/assets/4b0240c0-3e65-4b0e-9021-3bd41cba5009" width="23.5%"/>
</div>

<div class="image-row" align="center" justify-content="space-between">
   
  <img src="https://github.com/user-attachments/assets/7382d7ec-f2e1-4354-afd7-073ac022d1ff" width="23.5%"/>
  <img src="https://github.com/user-attachments/assets/9c427784-462a-4e2c-b330-90f1a481ce15" width="23.5%"/>
  <img src="https://github.com/user-attachments/assets/1120c701-46b7-4de9-8ed3-1845e862b8fe" width="23.5%"/>
  <img src="https://github.com/user-attachments/assets/12013dd3-3fe0-46ec-91f7-e3b8dea2cfec" width="23.5%"/>
  
</div>

<div class="image-row" align="center" justify-content="space-between">
   
  <img src="https://github.com/user-attachments/assets/975bd335-cd39-4aa1-b70e-b6a54d720057" width="23.5%"/>
  <img src="https://github.com/user-attachments/assets/f91e90ae-bddb-48ac-9cbe-effac63054bb" width="23.5%"/>
  <img src="https://github.com/user-attachments/assets/33ec76f2-a0b6-4e9f-a8d3-5df538405f29" width="23.5%"/>
  
</div>




## 📂 How to Create a Catalog

You can organize your media catalog using a simple folder structure. Follow the guidelines below to ensure proper metadata detection.

---

### 🆔 TMDB ID (Optional)

Adding a TMDB or IMDb ID helps improve metadata accuracy.

---

### 📁 1. Folder Structure

#### 🎬 Movies

```
/SelectedMovieFolder
└── MovieName/
└── movie-file.mp4
```

#### 📺 Series

You can use either of the following formats:

**Option A: Direct files inside series folder**

```
/SelectedSeriesFolder
└── SeriesName/
├── episode1.mp4
├── episode2.mp4
```

**Option B: Season-wise folders (Recommended)**

```
/Series
└── SeriesName/
├── Season 1/
│ ├── episode1.mp4
│ ├── episode2.mp4
├── Season 2/
├── episode1.mp4
```

---

### ⚙️ 2. App Settings

- Select the correct **content type** (Movie / Series)
- Choose the appropriate **folder path** that follows the structure above

---

## 📁 Catalog Folder Controls

You can manage how catalog folders behave directly from settings.

### 🔀 Reorder Folders

- Change the **sequence/order** of catalog folders
- This affects how content is displayed in the app

---

### ⭐ Mark for "Recently Added"

- Mark folders using the ⭐ icon
- Marked folders will contribute to the **Recently Added** hero section on the Home screen

**How it works:**

- The app fetches the **latest 10 items**
- Items are collected **across all marked folders**
- Helps highlight newly added content in one place

---

### ❗ Metadata Not Showing?

If metadata is missing, you have **two ways to fix it:**

#### 🔹 Option 1: Fix at Drive Level

Rename your folder to include ID:

```
MovieName [tmdb-id or imdb-id]
SeriesName [tmdb-id or imdb-id]
```

✔ Best for permanent and automatic fixes  
✔ Works across all devices

#### 🔹 Option 2: Fix at App Level

1. Open the **Meta Screen**
2. Click the **Edit ✏️ button**
3. Manually add the **TMDB / IMDb ID**

✔ Quick fix inside the app  
❌ Needs to be done per item

2. Go to the **Home tab**
3. Click the **Refresh 🔄 icon**

---

## 🔄 Import / Export Settings

You can easily back up or transfer your app data using the import/export feature.

### 📤 Export

Export your current app configuration, which includes:

- User settings
- TMDB catalogs _(optional)_
- TMDB API key _(optional)_
- Continue watching list
- Edited metadata (app-level changes)

---

### 📥 Import

Import a previously exported file to restore your setup:

- Restores all supported data automatically
- Useful when switching devices or reinstalling the app

## ScreenShots

<div class="image-row" align="center">
  <img src="https://github.com/user-attachments/assets/49421d12-c877-4f2c-824f-78ba0f0b3788" width="48.5%" />
  <img src="https://github.com/user-attachments/assets/4ade2e13-1d20-42b0-89f9-cfe9ccdaeac9" width="48.5%" />
</div>

## Download

Go to the [Releases](https://github.com/mkbhdana/StreamHive/releases) to download the latest APK.

## What scopes are used?

[List of all drive scopes](https://developers.google.com/identity/protocols/oauth2/scopes#drive)

This app uses `https://www.googleapis.com/auth/drive.readonly` scope that for the most part only lists files granted.

## 🙏 Credits

- [TheMovieDB](https://www.themoviedb.org/) – for metadata & search API

## Acknowledgments

- [DriveStream](https://github.com/itszechs/DriveStream)
- [mpv-android](https://github.com/mpv-android)
- [mpvKt](https://github.com/abdallahmehiz/mpvKt)

## ⚠️ Note

> The app does **not provide built-in credentials**, so each user must configure their own for Drive integration.

<details>
<summary>📺 Step-by-step video guide: Create OAuth client & Service Account</summary>

https://github.com/user-attachments/assets/5506d6c1-b096-4879-96f7-e124b4631e16

</details>

---

## 🔐 Important: Service Account Access

After creating your **Service Account**, you must grant it access to your Shared Drive:

1. Go to your **Google Drive**
2. Open the **Shared Drive**
3. Click **Manage Members**
4. Add your **Service Account email**
5. Assign role: **Content Manager**

❗ Without this step, the app will **not be able to access your files**
