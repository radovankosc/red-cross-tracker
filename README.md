# Ride Log

A simple Android app for a volunteer driver to log rides, issue numbered receipts and e-mail period reports.

## What it does

- **Log a ride.** Search a customer by name (accents don't matter) or type a new name to create one. Home address and the last destination fill in automatically. Pick one way or return; the distance comes from Google Maps (shortest car route) or can be typed in.
- **Receipts.** Every ride gets the next receipt number of its year (`1/2026`, `2/2026`, …, restarting each January). Price = starting fee + km × rate, both set in Settings (default 1.00 € + 0.40 €/km). The receipt can be shared or printed as a PDF.
- **Reports.** Pick a period (this month, last month, …) and e-mail a PDF listing the receipts with totals.
- **Backup.** Settings → Save backup file writes everything to one JSON file that can be restored on another phone.

## Installing on the phone

Every push to `main` is built by GitHub Actions. Download `ride-log.apk` from the **latest** release (or the workflow run's artifacts), open it on the phone and allow installing from that source. New builds install over the old one and keep the data.

The APK is signed with `app/signing.keystore`, which is kept in the repo on purpose so every build has the same signature. Replace it with a private key before publishing the app anywhere public.

## Google Maps key

Distances and address suggestions need a Google Maps Platform API key:

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project and add a billing account (one person's volume stays within Google's free monthly usage).
2. Enable **Routes API** and **Places API (New)**.
3. Create an API key under *APIs & Services → Credentials*, ideally restricted to those two APIs.
4. Paste it into the app under Settings → Google Maps.

Without a key the app still works; km are typed by hand.

## Building locally

Android Studio (or JDK 17 + Android SDK 35): `./gradlew assembleDebug`.
