# MPad

**Current release: 1.0.0**

[Download the latest release](https://github.com/rayqwe1234/MPad/releases/latest). APK and EXE files are provided individually without an additional archive.

MPad turns an Android phone into a Windows game controller. It supports:

- LAN companion mode (default): low-latency UDP input plus reliable TCP control and phone rumble.
- Bluetooth companion mode: RFCOMM fallback using the same authenticated protocol.
- Bluetooth HID mode: direct generic gamepad connection without the companion.
- Full Xbox-style touch layout, analog sticks and triggers, six-point multitouch, editable control positions/sizes, deadzone, sensitivity, opacity, and haptic settings.

The Windows companion exposes one virtual Xbox 360/XInput controller through ViGEmBus. Direct HID mode is a generic DirectInput device and will not work in games that only accept XInput.

## Build

All development tools and caches are pinned to `D:\DevTools` as required by this project. The current setup uses Android SDK 36, JDK 17, Gradle 9.4.1, Compose BOM 2026.04.01, and .NET 10.

From PowerShell:

```powershell
Set-Location D:\Codex\MPad
.\scripts\build.ps1
```

Outputs:

- `dist\android\MPad-1.0.0.apk`
- `dist\windows\MPad.Companion-1.0.0.exe`
- `dist\windows\MPad.Tester-1.0.0.exe`
- `dist\windows\ViGEmBus_1.22.0_x64_x86_arm64.exe`
- `dist\windows\MPad-1.0.0\MPad.Companion.exe`
- `dist\windows\MPad-1.0.0\MPad.Tester.exe`
- `dist\SHA256SUMS.txt`

Release artifacts are signed or self-contained APK/EXE files and are not additionally compressed.

The Android project stays on Compose 2026.04.01 because the stable SDK repository available on this machine does not yet expose `platforms;android-37`, which Compose 2026.08 requires.

## First use: LAN companion

1. Run `dist\windows\MPad\drivers\ViGEmBus_1.22.0_x64_x86_arm64.exe` and approve the Windows UAC prompt. This runtime driver must be installed in the Windows Driver Store and cannot be redirected to D:.
2. Start `MPad.Companion.exe`. Allow it through Windows Firewall for **Private networks only**.
3. Install the APK on the phone, or connect an authorized USB-debugging device and run `scripts\install-android.ps1`.
4. Keep phone and PC on the same private LAN. In MPad select “局域网伴侣”, choose the PC, enter the six-digit code shown by the companion, and connect.
5. Subsequent connections reuse the Android Keystore/Windows DPAPI protected token and do not need the code.

## Test the controller

Run `dist\windows\MPad\MPad.Tester.exe` after the companion has created the virtual controller. The tester reads XInput directly, so its display matches what an XInput game receives.

- Press controls on the phone to check every button, both sticks, the D-pad, and analog trigger values.
- Select controller slots 1–4 if another physical or virtual controller already occupies slot 1.
- Use the two motor sliders or “双马达测试” to verify game-rumble return to the phone.
- Closing the tester, changing controller slots, or losing the controller always stops both motors.

For development, `scripts\run-tester.ps1` launches the tester using the SDK and NuGet cache under `D:\DevTools`.

If discovery is blocked by a guest network or router isolation, enter the PC IPv4 address manually. UDP 26760 and TCP 26761 are used.

## Bluetooth modes

Pair the phone and PC in their system Bluetooth settings first.

- “蓝牙伴侣” connects to the companion's MPad RFCOMM service and still produces XInput.
- “蓝牙 HID 直连” registers the phone as a generic HID gamepad. Android 9+ is required. Some phone vendors disable the HID Device Profile; use companion mode if registration fails.

Game rumble is returned to the phone in companion mode. HID direct mode has touch haptics only.

## Verification

```powershell
# Windows protocol tests and companion build
$env:DOTNET_ROOT='D:\DevTools\DotNet'
$env:DOTNET_CLI_HOME='D:\DevTools\DotNetHome'
$env:NUGET_PACKAGES='D:\DevTools\NuGetCache'
D:\DevTools\DotNet\dotnet.exe test .\MPad.slnx
D:\DevTools\DotNet\dotnet.exe build .\windows\MPad.Companion\MPad.Companion.csproj

# Android tests and APK
$env:JAVA_HOME='D:\DevTools\Java\jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='D:\DevTools\Android\Sdk'
$env:GRADLE_USER_HOME='D:\DevTools\GradleCache'
Set-Location .\android
.\gradlew.bat testDebugUnitTest assembleRelease
```

Physical Bluetooth, HID, multitouch, game rumble, and end-to-end latency must be verified on an Android phone and a Bluetooth-capable Windows PC; emulators cannot validate those behaviors.
