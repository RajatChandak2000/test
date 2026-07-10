# SpendSentry

SpendSentry is an Android MVP that listens for payment-like notifications, creates a transaction draft with merchant and amount prefilled when possible, and keeps the list organized by date so you can review it before the day ends.

## What is included

- A notification listener service for likely transaction alerts.
- A polished dashboard with summary cards, a calendar-style date strip, grouped day sections, and editable transaction cards.
- Local draft persistence so detected transactions stay available after restart.
- End-of-day reminder scheduling for newly detected drafts.

## Development notes

- The parser is covered by unit tests for positive and negative cases.
- The app is intentionally Android-only because notification access is a platform permission.

## Building an APK in GitHub Actions

1. Push the repo to GitHub.
2. Open the `Actions` tab.
3. Run the `Build Android APK` workflow, or let it run automatically on `main`.
4. Download the `spendsentry-debug-apk` artifact from the completed run.

The APK will be at `app/build/outputs/apk/debug/app-debug.apk` inside the artifact bundle.
