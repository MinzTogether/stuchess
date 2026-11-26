# stuchess (Android)

Android chess application powered by Stockfish via Zero-JNI `ProcessBuilder` UCI execution, supporting both `armeabi-v7a` (32-bit) and `arm64-v8a` (64-bit) devices with adaptive Portrait and Landscape layouts.

## Included Features

- **Game Modes:** Bot vs Human (Elo 1320-3190, color selection, takeback), Bot vs Bot (two independent engines, pause/resume, pace control, take over mid-game), and Human vs Human (pass & play, optional auto-flip, chess clocks with increment, live Eval Bar).
- **Chess.com-style Evaluation & Hints:** Toggleable best-move arrow, real-time animated Eval Bar (live Stockfish streaming + instant checkmate/material fallback), Win% sigmoid move classification (`!!`, `!`, `★`, `✓`, `○`, `?!`, `?`, `??`), destination square badges, and per-player accuracy percentage.
- **Themes & Piece Packs:** Five built-in board palettes (Green, Walnut, Icy Sea, Tournament, Dark Slate), quick custom color pairs, two built-in SVG piece packs (`classic`, `minimal`), and `.zip` piece pack import (`wp..bk` in `.svg`, `.png`, or `.webp`).
- **Bot Source & Archive Importer (`.tar.gz`, `.zip`, `.rar`):**
  - **In-app (`Theme > Add Bot Source`):** Safely extracts `.tar.gz`/`.tgz`, `.zip`, and `.rar` (RAR4) bot archives with Zip Slip protection, detects ARM64/ARMv7 ELF binaries, `.nnue` neural networks (loaded via UCI `EvalFile`), and C/C++ source trees (`Makefile` / `*.cpp`).
  - **Build-time (`scripts/build_stockfish_android.sh` & `bot-sources/`):** Pass any `.tar.gz`, `.zip`, or `.rar` bot source archive directly to the build script or drop it into `bot-sources/` for automated GitHub Actions NDK compilation.
- **Adaptive Layout:** Portrait layout for phones and Landscape two-pane layout (board + eval bar left, clocks + two-column move list + controls right) preserving active game and engine state across rotation.
- **Bundled Stockfish Source:** Included under `stockfish-src/` (GPLv3) so GitHub Actions or local NDK builds compile both `arm64-v8a/libstockfish.so` and `armeabi-v7a/libstockfish.so` automatically.

## Build Test APKs

### GitHub Actions (Recommended)
Push the contents of `stuchess/` to GitHub. `.github/workflows/build-apk.yml` automatically:
1. Compiles `stockfish-src/src` (or an archive in `bot-sources/`) with Android NDK (`COMP=ndk`) for both `arm64-v8a` and `armeabi-v7a`.
2. Runs unit tests (`gradle testDebugUnitTest`).
3. Builds and uploads `app-arm64-v8a-debug.apk`, `app-armeabi-v7a-debug.apk`, and `app-universal-debug.apk` under **stuchess-debug-apks**.

### Local Build
```bash
chmod +x scripts/build_stockfish_android.sh
./scripts/build_stockfish_android.sh stockfish-src/src
gradle testDebugUnitTest assembleDebug
```
