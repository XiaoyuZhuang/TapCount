# TapCount · 轻计

**One tap. One small win.**

[简体中文 README](./README.md) · [Download the latest APK](https://github.com/XiaoyuZhuang/TapCount/releases/latest) · [Report an issue](https://github.com/XiaoyuZhuang/TapCount/issues)

TapCount is an open-source, offline-first Android counter for building everyday habits. It does not try to schedule your entire life. Instead, **finish one meaningful small action, then give yourself one point.**

That action could be completing a focused study session, reviewing a set of vocabulary words, doing a workout set, or finally taking the first step on something you have been postponing.

## Why I built it

For me, the difficult part of self-discipline is often not knowing *what* to do. It is getting started, keeping going, and returning after an unproductive day.

Many productivity tools offer schedules, reminders, targets, and rich dashboards. Those can be useful, but sometimes recording progress becomes another chore. I wanted something almost effortless: **do something worthwhile, tap once, and get back to living.**

TapCount is not meant to prove how productive you are. It simply makes everyday effort visible. Even a single point can matter when the alternative is never getting started because the plan feels too ambitious.

## How can a counter help with self-discipline?

The idea is a short feedback loop:

**Define one small action → complete it → tap to count → get immediate feedback → see progress accumulate.**

- **Make starting easier.** Instead of promising yourself three hours of study, define a manageable round of focused work and count it only when it is done.
- **Reward completion with feedback.** A number, vibration, or notification acknowledges an action right away. The feedback is a reminder, not the end goal.
- **Make effort visible.** Compare today with yesterday, inspect points earned by hour, or review previous days to understand when you tend to follow through.
- **Keep tracking lightweight.** A home-screen widget can record a tap without launching the full app.
- **Add optional variety.** Random bonus points can make repetitive routines a little more enjoyable.

**Points are not progress by themselves.** TapCount works best when a point stands for a genuinely completed action, rather than tapping merely to increase a number.

## Getting started

### 1. Install and make your first count

Download the APK from [GitHub Releases](https://github.com/XiaoyuZhuang/TapCount/releases/latest). TapCount supports **Android 8.0+**.

On a fresh installation, tapping the normal app icon **adds one count and opens the dashboard**. Go to **Settings → Entry mode** to choose a different behavior. Updating an existing installation does not override an entry mode you have already saved.

### 2. Define what one point means

Start with one habit. Be precise about when a point is earned:

| Goal | Count one point after… |
| --- | --- |
| Study or work | Finishing a self-defined focused session |
| Language learning | Completing a vocabulary review set or listening exercise |
| Exercise | Completing a workout set or planned session |
| Reading | Reading the pages you committed to |
| Overcoming procrastination | Completing one concrete, verifiable starting step |

What matters is not maximizing the score; **each point should represent something you actually did**. Create separate projects for unrelated habits if that helps.

### 3. Use the no-splash numeric home widget (recommended)

Open **Settings → Numeric home button → Add numeric button** and confirm placement on your Android home screen.

The widget displays your current count and records another tap when pressed, **without starting the management Activity or showing the normal app-launch animation**. It never automatically opens the dashboard. To manage your data, tap the persistent notification or use another configured entry method.

The older pinned numeric shortcut remains available, but because it launches an Activity, it may still show Android's system splash animation. Android requires your confirmation before placing a widget.

### 4. Choose how to enter the dashboard

| Entry mode | What happens when you tap the normal app icon |
| --- | --- |
| Tap icon to open (new-install default) | Counts once and opens management immediately |
| Double tap | First tap counts; second tap within the configured interval opens management without another point |
| Every N taps | All taps count; the Nth tap also opens management |
| Via notification | Icon taps only count; the persistent notification opens Settings |

The no-splash numeric widget **always counts without opening management**, independent of these automatic-entry rules. Keep notification permissions and the ongoing notification enabled when using notification-only entry.

### 5. Review your progress, without obsessing over it

- **Home:** today's and yesterday's points, their difference, an hourly points chart with numbers, and today's activity log.
- **History:** 7-/30-day windows, navigation to older dates, and daily details with hourly activity.
- **Projects:** separate counters and folders for different habits.
- **Settings:** vibration, sound, notifications, optional daily reset, and optional random bonuses.

For optional random rewards, configure a guarantee threshold **N** and bonus amount. Each tap has an approximately **1/N** chance to trigger the reward, and a reward is guaranteed by the Nth consecutive unsuccessful tap. Winning resets the draw progress, **not** previously earned points.

## A practical seven-day experiment

1. Choose one habit and decide what counts as a completed action.
2. Put the numeric widget somewhere convenient on your home screen.
3. **Do the action first, then tap.** Don't add fake points to make up for a slow day.
4. Spend half a minute in the evening reviewing when you managed to follow through.
5. After seven days, adjust the difficulty of the action—not just the score target.

If you only manage one action today, it still counts. The point is not endless productivity; it is finding a way to start, and to start again.

## Data and privacy

- **Local-first:** counters, projects, bonus progress, and history are stored locally in SQLite. No account is needed, and the app does not include analytics tracking services.
- **Internet:** normal counting works offline. The app accesses GitHub Releases when you explicitly check for updates.
- **Backup:** export or import a JSON backup in Settings. Importing replaces current app records.
- **Delete today's activity:** the Delete button on Home removes today's events and points for the current project and resets its current count to zero, while retaining older dates and other projects.
- **Clear all data:** Settings includes a factory reset that permanently deletes local counters, projects, histories, and preferences. Export your data before using it.

> Uninstalling the app or clearing Android app storage can also remove locally saved data. Export important records regularly.

## Download, build, and contribute

- [Latest release](https://github.com/XiaoyuZhuang/TapCount/releases/latest)
- [GitHub Actions builds](https://github.com/XiaoyuZhuang/TapCount/actions)
- [Issues and suggestions](https://github.com/XiaoyuZhuang/TapCount/issues)

TapCount is written in Kotlin and supports Android 8.0+ (API 26). Builds use JDK 17 and Gradle. Pushing to `main` triggers CI. Official APKs are signed with a persistent private signing key that is **never committed to the repository**.

---

**One tap. One small win.**

The purpose is to stop focusing only on the person you wish you already were, and start noticing the small things you actually accomplished today.
