# Offline face-attribute diagnostic

Run from `C:\DriveAlert` in PowerShell. The isolated environment used for the
2026-11-13 check contains Python 3.13, ai-edge-litert 2.3.0, numpy 2.5.4 and
Pillow 13.0.0. It does not change the Android runtime or dependencies.

```powershell
python -m venv output\face-model-env
& output\face-model-env\Scripts\python.exe -m pip install ai-edge-litert==2.3.0 numpy==2.5.4 pillow==13.0.0
& output\face-model-env\Scripts\python.exe scripts\face_attribute_probe.py path\to\frame.jpg --box 100 50 180 160 --output output\face-frame-probe
```

Supply the face bounds (left, top, right, bottom) in original image pixels.
The diagnostic reproduces the app's square crop and quantization policy across
four padding choices. It writes the 128×128 RGB crops and scores with the model
hash to the output folder. Pillow resizing approximates Android Canvas and is
not a pixel-identical replay. It makes no network requests or application-data
changes. Keep face images local and out of Git.

The existing user screenshot is suitable only for checking that this utility
runs. Its modal scrim changes image brightness, its dialog overlaps the crop,
and its face bounds are manually estimated. Neither its scores nor the cropped
images establish what the production classifier saw. Original camera frames and
matching landmarks are still needed to investigate the live false positives.

## Original-input capture on the existing phone

The opt-in `FaceAttributeCropDiagnosticTest` can save five camera frames,
their landmarks and the Android-prepared input bitmaps under the app-private
`files/crop-diagnostic/` directory when `exportModelInputs=true` is supplied.
Without that flag it saves no images. It uses the existing camera session,
tries the saved endpoint and named-camera discovery, and writes no test records
to Room or Firestore. Monitoring should be stopped first. Instrumentation closes
the normal app; reopen MainActivity afterward. Run only this named test on the
physical phone, never the entire instrumentation suite.

```powershell
& $adb -s 080633723R000519 shell am instrument -w -e liveCameraDiagnostic true -e exportModelInputs true -e class com.lhordkent.drivealert.detection.FaceAttributeCropDiagnosticTest com.lhordkent.drivealert.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s 080633723R000519 shell am start -n com.lhordkent.drivealert/.MainActivity
```

`$adb` denotes the installed platform-tools `adb.exe`. Copy only that diagnostic
directory through `run-as`; do not export app databases or credentials. Captures
are named by phase/sample and overwrite matching names on subsequent runs.
Scores remain in `DriveAlertCropDiagnostic` logcat output. Retain the matching
log with each capture batch. Remove the private diagnostic directory and test
helper after collecting the batch. A passing measurement test is not an
accuracy test; inspect actual images before assigning ground-truth labels.

Supply `-e diagnosticSamples 20` for a bounded longer batch (allowed range 1–20;
default 5). Save each batch separately: filenames on the phone are reused.
After retrieving inputs and saving the matching log as `scores.log` in the
batch directory, compare exported Android inputs against native mask scores:

```powershell
& output\face-model-env\Scripts\python.exe scripts\face_attribute_replay.py output\original-input-capture
```

The replay also tests synthetic detail loss. This does not reproduce changed
camera distance, perspective or landmark detection, and cannot validate a
resolution/firmware fix. The 2026-11-13 batches matched all 100 native mask
scores exactly; neither batch reproduced the historical false warning.
