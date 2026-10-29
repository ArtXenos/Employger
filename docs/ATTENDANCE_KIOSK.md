# Attendance Kiosk

Employees clock in and out by holding their printed QR badge up to the webcam of the kiosk computer at the entrance. The first scan of the day clocks them in, and the next one clocks them out. The server records the time from its own clock into the usual attendance record, so hours, LATE status, payroll locks and payroll work exactly as for attendance typed in by HR.

No scanner hardware, HTTPS or GPS is needed: a laptop's built-in webcam is enough.

## One-time setup

1. **Apply migration V17** (adds the badge code and the `KIOSK` role). Back up first, then as `root`:
   ```powershell
   .\scripts\backup-db.ps1 -MysqlBin "C:\Program Files\MariaDB 10.11\bin" -Port 3307
   & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source db/migrations/V17__attendance_kiosk.sql"
   ```
   Undo: `V17__rollback.sql`.
2. **Create the kiosk login.** As Administrator, open **Users**, click **Add User**, choose the role **Kiosk**. It has no linked employee: it belongs to the computer, not a person. Use a long password.
3. **Set up the kiosk.** On the **server computer** (the one running Em-Ployger), open <http://localhost:8080>, log in as the kiosk account, and allow the camera when the browser asks. The kiosk page opens by itself. Press F11 for full screen.

## Badges

- On **Employees**, click **Badge** on an employee, then **Issue badge** and **Print badge**. The card is ID size (85.6 × 54 mm): cut it out and put it in an ID holder. Print at 100% scale so the QR code stays at least 3 cm wide.
- **Lost or damaged card:** click **Reissue**. The old card stops working at once; print the new one.
- Only administrators and HR managers can see or issue badges. Whoever holds a badge can clock in with it.
- A former employee's card is refused at the kiosk.

## What the kiosk does with a scan

| Situation | Result |
|---|---|
| No record today | Clock in: PRESENT, or LATE after the standard start plus the grace period |
| Clocked in, scanned again 5 minutes or more later | Clock out; the regular hours are derived |
| Scanned again within 5 minutes | Refused, so a double scan doesn't clock out |
| Already clocked out today | Refused |
| Today is recorded as LEAVE or ABSENT | Refused: "Please see HR" |
| Payroll for today is already processed or paid | Refused |
| Night shift: clocked in yesterday, still open, at most 16 hours ago | The morning scan clocks out yesterday's shift |
| Forgot to clock out (open for more than 16 hours) | The old day stays open for HR to fix on **Attendance**; the scan clocks in today |

Every scan is in the **Audit Log** (`ATTENDANCE_CLOCK_IN` / `ATTENDANCE_CLOCK_OUT`) under the kiosk login. A badge that won't scan can be typed in the box at the bottom of the kiosk page.

## Why the kiosk must be the server computer

- **Camera:** browsers allow the camera only on `https://` or on `localhost`. The app runs on plain `http://`, so the camera works only at `http://localhost:8080`, on the server computer itself.
- **Location:** the server accepts kiosk scans only from its own computer. The kiosk login used on any other device is refused with "Scans are only accepted at the kiosk computer", even if someone knows the password. So clocking in always means standing at the entrance.

Everything else (HR pages, employees' My Payroll) works from any device as before.

### Using a separate kiosk computer later

1. Give the kiosk computer a fixed IP address on the office network, e.g. `192.168.1.40`.
2. Start Em-Ployger with that address allowed: `$env:KIOSK_ADDRESSES = "192.168.1.40"` before `.\mvnw.cmd spring-boot:run`.
3. On the kiosk computer, in Chrome, open `chrome://flags/#unsafely-treat-insecure-origin-as-secure`, enter `http://<server IP>:8080`, set it to **Enabled** and restart Chrome. This lets that one computer use the camera over plain http. Do this only on the dedicated kiosk computer, never on personal devices.

## Keep in mind

- **Buddy punching:** a badge can be lent to a coworker. Put the kiosk where it can be seen (reception, the guard, or a CCTV camera). The kiosk shows the name in large letters after each scan, so a mismatch is easy to notice.
- **No other sessions on the kiosk:** while the server computer is the kiosk, don't leave an HR or administrator session open in another browser window on it.
- **If the kiosk is down:** use a paper logbook, and HR enters the times on **Attendance**.
- **Field workers** don't use the kiosk: HR enters their hours on **Attendance** from their timesheets.
