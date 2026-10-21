# FaceTrack (Android)

FaceTrack is an Android Kotlin project with a front-camera preview, on-device ML Kit face detection, a tracking indicator, and CameraX MP4 recording to `Movies/FaceTrack`.

## Important current limitation

**This initial project does not yet perform dynamic reframing in the encoded video.** It detects the face and draws an approximate indicator over the preview, while CameraX records the normal camera stream. Moving the preview marker/crop is not equivalent to changing the recorded frames. The requested Center Stage-like encoded output needs a custom GL + MediaCodec pipeline (or another camera pipeline that explicitly supports per-frame crop transformations); that pipeline is not claimed to be implemented here.

## Build in a mobile browser

1. On your phone, open GitHub in your browser and sign in.
2. Create a new repository named `FaceTrack`. Choose Public or Private.
3. Upload the contents of this ZIP, preserving folders and file paths. GitHub's mobile web UI can upload files, but uploading many files individually may be awkward; use GitHub's web upload page if available.
4. Ensure the workflow file exists at `.github/workflows/android.yml`.
5. Open the repository's **Actions** tab and enable Actions if asked.
6. Choose **Build FaceTrack APK** → **Run workflow**.
7. Wait for the workflow to finish. Open the completed run and scroll to **Artifacts**.
8. Download `FaceTrack-debug-apk`, then extract the ZIP artifact in your phone's Downloads app or a file manager.
9. Tap `app-debug.apk`. If Android asks, allow your browser or file manager to install unknown apps, then install.

## Notes

- The APK is a debug build and is not a Play Store release.
- Audio is included only if microphone permission was granted.
- Video is saved under `Movies/FaceTrack`.
- No camera frames are sent to a server; ML Kit detection runs on-device.
- No physical-device test or successful cloud build is claimed by this archive.
- `gradle-wrapper.properties` is included, but the wrapper launcher JAR/script are not included in this starter archive. The workflow uses the runner's installed Gradle command. For local reproducible builds, generate the wrapper with Gradle; mobile users can rely on GitHub Actions.
