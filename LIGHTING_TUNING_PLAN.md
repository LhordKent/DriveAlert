# Lighting warning tuning plan — 2026-11-16

Status: proposed; no lighting rules, thresholds or timing changed yet.
Save and push the existing app/firmware work to `main` before implementing this plan.

## Problem

The user reports that the driver's face is visible under a taxi cabin light,
but a head nod exposing mostly hair produces an insufficient-lighting warning.
The current missing-face rule uses the scene's low-light hint to choose that
message. A dim cabin can already have that hint while facial lighting is usable.
This code path explains the report; the taxi event has not been captured or replayed.

Current facial thresholds are luminance <25, or luminance <45 with contrast <20
(0–255). One poor sample pauses assessment immediately. Good light needs two
seconds to recover; visibility speech normally starts after three seconds.

## Proposed behavior

1. **Keep measurements fast.** Retain the current sampling cadence and one-second
   freshness limit during startup, monitoring and calibration. Checking only every
   5–10 seconds would make readings expire and leave visibility decisions outdated.
   Startup and calibration still require two seconds of usable lighting.
2. **Separate face loss from lighting failure.** Without a current selected face,
   show face unavailable/checking view. Ambient darkness remains a hint in guidance;
   it cannot establish insufficient facial lighting. Apply this consistently to
   setup, monitoring, notifications and the foreground-service status. A covered
   lens with no face will therefore use the face-unavailable warning too.
3. **Confirm poor lighting before warning.** Pause assessment on the first poor
   facial sample; show checking lighting during a two-second confirmation period.
   After continuous confirmation, allow the existing three-second warning delay:
   about five seconds total from the first uninterrupted poor sample. A fresh good
   sample ends an unconfirmed dip immediately if lighting was already usable.
   Confirmed darkness still needs two seconds of continuous good samples to resume.
   Missing, stale, duplicate or out-of-order samples cannot establish darkness or
   recovery. Target, stream and alignment resets discard old lighting evidence.
4. **Allow short face-loss movements.** Increase the monitoring face-unavailable
   speech delay from three to five seconds, independently of facial-light recovery.
   Assessment remains paused while the face is missing. A nod cannot be measured
   reliably when only hair is visible; sustained loss still needs a warning.
   Regional mask/sunglasses warning delays and the 30-second repeat interval stay
   unchanged.
5. **Trial a modest threshold relaxation.** Candidate values: severe-dark luminance
   25→20 and low-contrast-dark luminance 45→40, keeping contrast at20. These are
   provisional engineering values, not validated taxi settings. Compare usable
   dim-face readings against genuinely unusable darkness before accepting them.
   Do not change EAR/MAR/head thresholds, calibration formulas or camera placement.

## Verification and rollout

- Unit regressions: dim scene + usable face, brief missing-face nod, brief poor
  crop followed by good light, sustained darkness, five-second face-loss warning,
  recovery, interrupted/stale samples, target resets and unchanged regional warnings.
- Check all presentation paths agree about face loss versus confirmed poor lighting.
- Run Android unit tests, build the APK and compile on-device tests on JDK17.
- Parked camera check: clear face in taxi-like light, short nod and recovery,
  sustained face loss, real darkness/covered lens, and calibration in dim light.
  Confirm displayed status, measurement availability and actual speaker timing.
- Build a separate candidate APK after implementation. Report software checks and
  physical-camera results separately. Do not claim taxi accuracy from unit tests.

The earlier downward-gaze versus eye-closure investigation remains stopped.
This plan does not solve that known limitation. No firmware flash or cloud change
is required by this proposal.
