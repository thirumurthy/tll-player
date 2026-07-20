# Future OwnTV Merge Guide

A repeatable process to cherry-pick upstream OwnTV fixes into TLL.

## Setup (one-time)

```bash
# Already done — the OwTV remote is configured:
git remote -v
# → owntv  C:\Thiru\work\androidRep\OwnTv\OwnTV (fetch)
```

Update the remote when OwnTV's local clone has new commits:
```bash
cd C:\Thiru\work\androidRep\OwnTv\OwnTV
git fetch origin
```

## The Process

### Step 1: Record the baseline

Before starting, note the current commit hashes:

```bash
cd F:\Thiru\TV\my-tv-1
git log --oneline -1           # TLL current tip
cd C:\Thiru\work\androidRep\OwnTv\OwnTV
git log --oneline origin/main -1    # OwnTV latest
```

### Step 2: Identify new OwnTV commits

```bash
cd C:\Thiru\work\androidRep\OwnTv\OwnTV
# Replace LAST_MERGE with the OwnTV commit we merged up to last time
git log --oneline LAST_MERGE..origin/main -- app/src/main/java/
# Count and review
git diff --stat LAST_MERGE..origin/main -- app/src/main/java/
```

**Last merge point**: The OwnTV base we merged up to was commit `d3ffbac` (from `ca02d2c`). For next time, use `d3ffbac` as the starting point.

### Step 3: Categorize changes

Run the categorizer:

```bash
cd F:\Thiru\TV\my-tv-1
bash scripts/categorize-owntv-changes.sh LAST_MERGE..origin/main
```

This groups files into:
- **NEW** — Files that don't exist in TLL. Decide if you need them.
- **SAFE** — Files that are identical after package rename (just `cp + sed`)
- **CONFLICT** — Files where TLL has customizations (need manual merge)

### Step 4: Apply new files (Phase 1)

```bash
bash scripts/merge-owntv.sh 1
```

This copies new OwnTV files with automatic package rename (`tv.own.owntv` → `com.thirutricks.tllplayer`).

### Step 5: Apply safe file replacements

For files where TLL didn't customize (just package rename):

```bash
# For each SAFE file from Step 3:
cd C:\Thiru\work\androidRep\OwnTv\OwnTV
git show origin/main:app/src/main/java/tv/own/owntv/PATH/File.kt | sed \
  -e 's/package tv\.own\.owntv/package com.thirutricks.tllplayer/g' \
  -e 's/import tv\.own\.owntv/import com.thirutricks.tllplayer/g' \
  > F:/Thiru/TV/my-tv-1/app/src/main/java/com/thirutricks/tllplayer/PATH/File.kt
```

### Step 6: Handle conflicting files

For files where both OwnTV and TLL made changes:

```bash
bash scripts/resolve-conflicts.sh
```

This tries a 3-way merge. If it fails, you'll need to manually resolve by:
1. Open the file in an editor
2. Find `<<<<<<<` conflict markers  
3. Merge the two versions, keeping TLL customizations

**Files that are ALWAYS customized by TLL** (will always conflict):
| File | TLL Customization |
|---|---|
| `SettingsRepository.kt` | Custom settings keys, metadataConfig |
| `SyncManager.kt` | Custom sync logic (TLL source) |
| `AppModule.kt / DataModule.kt` | DefaultProfileGuard, SecurityInterceptor |
| `OwnTVShell.kt / ShellViewModel.kt` | Navigation, channel list |
| `PlayerHud.kt / OwnTVPlayer.kt` | MiniPlayer, preview |
| `ProfilesViewModel.kt / ProfileComponents.kt` | Default non-deletable profile |
| `ManageProfilesScreen.kt` | canDelete guard |
| `Enums.kt` | TLL + STALKER source types |

### Step 7: Fix compilation

```bash
./gradlew :app:compileStandardDebugKotlin 2>&1 | grep "e:"
```

Common fixes needed:
| Error | Fix |
|---|---|
| `Unresolved reference 'XXX'` | Add missing enum value, method, or import |
| `'when' expression must be exhaustive` | Add STALKER/TLL branch |
| `Unresolved reference 'tv.own.owntv'` | Replace with `com.thirutricks.tllplayer` |
| `Missing MetadataConfig` | Add to SettingsRepository |
| `Missing font/subtitle/weather files` | Remove the dependent file or re-add |

### Step 8: Commit

```bash
git add -A
git commit -m "chore: merge OwnTV fixes up to <commit-hash>"
```

## Files TLL Will NEVER Need (skip these)

- `core/stalker/` — Stalker portal
- `core/subtitles/` — OpenSubtitles  
- `core/weather/` — Weather feature
- `core/sync/work/` — WorkManager-based sync workers
- `core/metadata/MetadataRepository.kt` — TMDB enrichment orchestrator
- `core/database/dao/SubtitleDao.kt` — Subtitle entities
- `core/database/dao/MetadataDao.kt` — Metadata entities
- `core/companion/CompanionController.kt` (remove if font breaks)
- `core/companion/CompanionLink.kt, CompanionServerState.kt`
- `player/ExoStreamStats.kt, FpsSample.kt, etc.` — New player features
- `ui/theme/PopupTheme.kt` — Lora font
- `ui/components/NumberInputDialog.kt` — Popup font dependency
- All `features/subtitles/` — Subtitle UI
- `features/settings/ChNavSettingsScreen.kt` — Channel nav
- `features/settings/HomeSettingsScreen.kt` — Home config
- `features/settings/MetadataSettingsScreen.kt` — TMDB settings
- `features/settings/NavMenuSettingsScreen.kt` — Menu config
- `features/settings/NetworkSettingsScreen.kt` — Proxy settings
- `features/settings/OpenSubtitlesAccountScreen.kt`
- `features/settings/WeatherSettingsScreen.kt`
- `features/settings/DeleteSubtitlesScreen.kt`

## Key Reference

| Item | Value |
|---|---|
| **TLL package** | `com.thirutricks.tllplayer` |
| **OwnTV package** | `tv.own.owntv` |
| **TLL default profile** | `"TLL"` (non-deletable, see `DefaultProfileGuard.kt`) |
| **TLL source URL** | `https://tllapp.dpdns.org/tvnexa/v1/admin/channel-pllayer` |
| **Last merged OwnTV commit** | `d3ffbac` |
| **TLL remote** | `git@github.com:thirumurthy/tll-player.git` |
| **OwnTV remote** | `git@github.com:ahXN00/OwnTV.git` |

## Automation Scripts (in `scripts/`)

| Script | Purpose |
|---|---|
| `merge-owntv.sh` | Copy new files with package rename |
| `merge-owntv-phase3-v2.sh` | 3-way merge for existing files |
| `resolve-conflicts.sh` | Auto-resolve conflict markers |
| `apply-critical-fixes.sh` | Targeted patching for critical files |
