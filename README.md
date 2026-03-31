# UptimeKuma Widget

A native Android home screen widget that displays monitor statuses from an [Uptime Kuma](https://github.com/louislam/uptime-kuma) public status page. No account or authentication required — works with any publicly accessible status page.

## Screenshots

<a href="https://github.com/user-attachments/assets/87ec5094-8e83-40b0-b64b-387cbe85a9e4"><img
src="https://github.com/user-attachments/assets/87ec5094-8e83-40b0-b64b-387cbe85a9e4" width="300"></a>
<a href="https://github.com/user-attachments/assets/4d171a0a-61a2-4786-8aea-746fae39df25"><img
src="https://github.com/user-attachments/assets/4d171a0a-61a2-4786-8aea-746fae39df25" width="300"></a>
<a href="https://github.com/user-attachments/assets/1991e20b-bebf-40eb-af23-453e14dc3d8c"><img
src="https://github.com/user-attachments/assets/1991e20b-bebf-40eb-af23-453e14dc3d8c" width="300"></a>
<a href="https://github.com/user-attachments/assets/2bfebdef-d567-48d0-91bd-79db467080ee"><img
src="https://github.com/user-attachments/assets/2bfebdef-d567-48d0-91bd-79db467080ee" width="300"></a>
<a href="https://github.com/user-attachments/assets/3e572183-14f0-44ac-b868-8c92a02d4505"><img
src="https://github.com/user-attachments/assets/3e572183-14f0-44ac-b868-8c92a02d4505" width="300"></a>

## Features

- Grouped monitors matching your status page layout
- Uptime percentage per monitor (colour-coded green / orange / red)
- Heartbeat history bar chart per monitor
- Configurable auto-refresh interval (1–60 minutes)
- Tap the refresh button to update on demand
- Tap the header to open the status page in a browser
- Settings screen to configure hostname, slug, and refresh interval

## Requirements

- Android 8.0 (API 26) or higher
- A publicly accessible Uptime Kuma instance with a status page

## Building from source

### Prerequisites

- JDK 17
- Android SDK with API 35 and build-tools 35.0.0

CLI-only setup (no Android Studio required):
```bash
sudo apt install -y openjdk-17-jdk
# Download cmdline-tools from https://developer.android.com/studio#command-line-tools-only
# Extract to ~/Android/Sdk/cmdline-tools/latest/
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

### local.properties

Create `local.properties` in the project root (not committed to git):
```properties
sdk.dir=/path/to/your/Android/Sdk
```

### Debug build

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Adding the widget

1. Long-press your home screen
2. Tap **Widgets**
3. Search for **UptimeKuma Widget**
4. Drag it onto your home screen

Then open the **UptimeKuma Widget** app from your app drawer and enter:

| Field | Description |
|-------|-------------|
| Hostname | Full URL of your Uptime Kuma instance, e.g. `https://uptime.example.com` |
| Dashboard slug | The slug of your status page (from the status page URL) |
| Refresh interval | How often the widget polls for updates |

Tap **Save & Refresh** to apply and update the widget immediately.

## OxygenOS / OnePlus

OxygenOS aggressively restricts background processes. To keep auto-refresh working:

> Settings → Battery → Battery Optimization → UptimeKuma Widget → **Don't optimize**

## API endpoints used

```
GET /api/status-page/{slug}
GET /api/status-page/heartbeat/{slug}
```

No authentication. Both endpoints are part of Uptime Kuma's public status page API.

## Architecture

| Component | Role |
|-----------|------|
| `UptimeWidget` | `AppWidgetProvider` — handles lifecycle, schedules `AlarmManager` |
| `WidgetUpdateService` | Background `Service` — fetches data on a thread, updates `RemoteViews` |
| `MonitorListService` | `RemoteViewsService` — adapter-backed `ListView` for the widget |
| `UptimeRepository` | Network + JSON parsing via `HttpURLConnection` and `org.json` |
| `BootReceiver` | Reschedules alarm and triggers refresh after device reboot |
| `MainActivity` | Settings screen — hostname, slug, refresh interval |

**No third-party libraries.** Uses `HttpURLConnection` for networking and `org.json` (bundled with the Android runtime) for JSON parsing.

## License

MIT — see [LICENSE](LICENSE)
