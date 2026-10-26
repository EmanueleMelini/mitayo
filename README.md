# Mitayo 見たよ

A free Android app that works like "Tinder" for your [MyAnimeList](https://myanimelist.net) list:
pick a category and swipe through the anime one at a time. Website and downloads:
<https://mitayo.emanuelemelini.dev>.

- **right**: completed on MAL, with every episode watched;
- **left**: skip, it never shows up again (search the skipped ones in Profile → Data, also by their
  status in your list, then restore one or give it a status on MAL);
- **up** (or ▶): watching. You enter the episodes watched right away and it goes to MAL as *watching*;
- **undo**: goes back on the last swipes, on MAL too;
- **★**: completed with a MAL score (only when you tap it: swiping right never stops for a score);
- **search** (from the categories): any downloaded anime by title, to set its status without swiping,
  and a search on MyAnimeList (one request, on a tap) for the ones not downloaded;
- **decades** among the categories, **sequels and prequels** in the details with their status, export
  and import of the skipped anime, launcher shortcuts for search and season;
- **with other users** (home): compare two public MAL lists (totals, score distribution, the anime in
  common with both scores, the ones only one has, affinity), and a **friend's deck** with the anime of
  their list you don't have at all. Other users' lists are read on request and kept only while needed.

Decks show only the anime that aren't in your MAL list at all (and that you didn't skip). Your list has
its own decks, one per status (watching, completed, on-hold, dropped, plan to watch), to go through it
again, change status or give scores; there a left swipe only moves on, it doesn't skip. The **⋯** button sets the statuses no swipe gives (on-hold,
dropped, plan to watch). An anime that is still airing can't be completed, so a right swipe asks the
episodes watched and sets it as watching.

The details of an anime (ⓘ, or the poster in search, skipped anime and comparisons) also list its
trailers and videos, opened in YouTube. The app language can follow the system or be Italian or English
(Profile → Appearance).

The app is in Italian and English, with dark and light themes.

## How it works

- **Official MAL API v2 only**, never the MAL website pages (the API agreement forbids scraping). MAL
  has no "anime by genre" endpoint, so the app downloads the 5,000 most popular anime (10 requests of
  500, with their genres) into a local catalog (Room), and every category is a query on it. When a
  deck is running out, more pages are downloaded. *This season* and *Suggested for you* come from MAL
  as they are.
- **Every day** (at the first start after 24 hours) the app goes through every downloaded page again:
  it updates scores and airing status and removes from the phone, posters included, the anime MAL no
  longer returns. The API agreement asks for removed content to go within 24 hours. Skipped ids stay.
- **Login** with OAuth2 and PKCE (MAL supports only `plain`) in a Custom Tab, redirect `mitayo://oauth`.
  Before the login the user accepts the terms of use and the privacy policy. Tokens are encrypted with
  an AES key in the Android Keystore and renewed automatically; the refresh token lasts a month, then a
  new login is needed.
- Every swipe changes the local copy of the list at once and goes into an **outbox** that WorkManager
  sends to MAL as soon as there's a connection, one request at a time.
- **Updates**: release builds check GitHub Releases at most once a day (and on demand from Profile →
  About), show the notes and download and install the APK with PackageInstaller after the user's
  confirmation. After an update the app shows what's new. Debug builds check only on demand: their
  signature can't install a release APK.
- **Notifications**, all in Profile → Notifications:
  - *news* (Firebase Cloud Messaging topics, no server): `news` for everyone, `news-it` / `news-en` by
    app language, `news-debug` for debug builds only. The phone gets no messaging token until the
    privacy question is answered, and loses it when the news are turned off. The release workflow
    announces every version.
  - *local reminders*, all off at install: a new version on GitHub (daily, once per version), the start
    of a season (first two weeks of January, April, July and October, with the anime of the season still
    to swipe) and a weekly one on Sundays. Window alarms (no exact-alarm permission), handed to
    WorkManager when they need the network, scheduled again after a reboot.
- **Statistics and crash reports** (Firebase Analytics and Crashlytics) are off at install (manifest
  flags) and start only if the user agrees in the question after the first login; Profile → Privacy
  changes the choice. Only fixed names of screens, decks and actions are sent (`Telemetry`), never MAL
  data (titles, anime or genre ids, user name). Debug builds never send anything.
- Without `app/google-services.json` the app builds and runs with Firebase off (`BuildConfig.FIREBASE`):
  forks don't need a Firebase project. Signed builds require it.

## Setup

1. Create a client on [myanimelist.net/apiconfig](https://myanimelist.net/apiconfig):
   - App Type: `android` (no client secret)
   - App Redirect URL: `mitayo://oauth` (it must match `MalAuth.REDIRECT_URI` and the manifest)
   - Homepage URL: `https://<your-site>`
   - Privacy Policy URL: `https://<your-site>/privacy`
   - Terms of Use URL: `https://<your-site>/terms`
   - Commercial / Non-Commercial: `non-commercial`
2. Put the Client ID in `local.properties` (never committed):

   ```properties
   mal.client.id=<YOUR_CLIENT_ID>
   ```

   Without it a debug build still runs and the login screen says it's missing; a signed build stops.
3. Optional, for news, statistics and crash reports: create a Firebase project, add an Android app
   with the application id (`applicationId` in `app/build.gradle.kts`, `<your.application.id>` in a fork), download
   `google-services.json` into `app/` (never committed). In the Firebase console:
   - Analytics → Data settings → Data retention: **2 months** (what the privacy policy says);
   - in the Google Cloud console, restrict the API key of the file to the Android app (package and
     SHA-1 of the release and debug keys): the key ends up in the APK anyway.
4. Build with JDK 21: `./gradlew assembleDebug`.

For a signed release build on your machine, add to `local.properties`:

```properties
mitayo.keystore.path=<path/to/release.jks>
mitayo.keystore.password=<KEYSTORE_PASSWORD>
mitayo.key.alias=<KEY_ALIAS>
# Only if it differs from the keystore password
mitayo.key.password=<KEY_PASSWORD>
```

In a fork, also change the site host (`mitayo.site.host` in `gradle.properties`), the repository URL
(`AppLinks.REPOSITORY` in `ui/AppLinks.kt`) and the links in `site/`.

## Release

Releases are built by GitHub Actions (`.github/workflows/release.yml`): for every `vX.Y.Z` tag the
workflow builds and signs the APK and publishes it in the tag's GitHub Release as `Mitayo-X.Y.Z.apk`.
The site's `/download` page reads the releases by itself.

1. In `app/build.gradle.kts`: `versionCode` + 1 and `versionName` = `X.Y.Z` (the workflow stops if the
   tag doesn't match).
2. Notes in `app/src/main/assets/release-notes/X.Y.Z.md`: a `- ` list in English (shown on GitHub)
   and the Italian version in a hidden `<!-- mitayo-notes:it ... -->` block. The same file is the
   GitHub release body, the in-app "What's new" and the site's download notes; a unit test fails when
   the current version has none.
3. `git tag -a vX.Y.Z -m "Mitayo X.Y.Z"` and `git push origin vX.Y.Z`.

Repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `MITAYO_KEYSTORE_BASE64` | the keystore in base64 |
| `MITAYO_KEYSTORE_PASSWORD` | keystore password |
| `MITAYO_KEY_ALIAS` | key alias |
| `MITAYO_KEY_PASSWORD` | key password, only if it differs from the keystore one |
| `MITAYO_MAL_CLIENT_ID` | the MAL Client ID |
| `MITAYO_GOOGLE_SERVICES_JSON` | the content of `app/google-services.json` (raw JSON, not base64) |

With the GitHub CLI (`gh` asks for the value without showing it):

```bash
base64 -i <path/to/release.jks> | gh secret set MITAYO_KEYSTORE_BASE64 -R <owner>/<repo>
```

```bash
gh secret set MITAYO_KEYSTORE_PASSWORD -R <owner>/<repo>
```

```bash
gh secret set MITAYO_GOOGLE_SERVICES_JSON -R <owner>/<repo> < app/google-services.json
```

**Version push**: after publishing the release the workflow sends a notification to `news-it` and
`news-en` with the texts of the "New version" reminder (`reminder_update_title` /
`reminder_update_text`, read by `.github/scripts/notify_update.py`); tapping it opens the update
dialog. To skip it, e.g. for a small fix, put `[no-push]` in the tag message:
`git tag -a vX.Y.Z -m "Mitayo X.Y.Z [no-push]"`. The **Notify update** workflow (Actions → Run
workflow, from the main branch) sends it by hand: `debug` to `news-debug` to try it, `users` to
everyone. Preview: `python3 .github/scripts/notify_update.py X.Y.Z --dry-run`.

Google authentication has no keys (**Workload Identity Federation**): GitHub trades its OIDC token for
a token of a service account that has only the *Firebase Cloud Messaging API Admin* role. Set up once
in Google Cloud (project of the Firebase app):

```bash
gcloud services enable iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com fcm.googleapis.com --project <FIREBASE_PROJECT_ID>
```

```bash
gcloud iam service-accounts create github-fcm --project <FIREBASE_PROJECT_ID>
```

```bash
gcloud projects add-iam-policy-binding <FIREBASE_PROJECT_ID> --member "serviceAccount:github-fcm@<FIREBASE_PROJECT_ID>.iam.gserviceaccount.com" --role roles/firebasecloudmessaging.admin
```

```bash
gcloud iam workload-identity-pools create github --project <FIREBASE_PROJECT_ID> --location global
```

```bash
gcloud iam workload-identity-pools providers create-oidc mitayo --project <FIREBASE_PROJECT_ID> --location global --workload-identity-pool github --issuer-uri https://token.actions.githubusercontent.com --attribute-mapping "google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.ref=assertion.ref" --attribute-condition "assertion.repository_id == '<REPOSITORY_ID>' && (assertion.ref.startsWith('refs/tags/v') || assertion.ref == 'refs/heads/main')"
```

```bash
gcloud iam service-accounts add-iam-policy-binding github-fcm@<FIREBASE_PROJECT_ID>.iam.gserviceaccount.com --project <FIREBASE_PROJECT_ID> --role roles/iam.workloadIdentityUser --member "principalSet://iam.googleapis.com/projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/github/attribute.repository_id/<REPOSITORY_ID>"
```

`<REPOSITORY_ID>` is the numeric id of the repository (`gh api repos/<owner>/<repo> --jq .id`): it stays
the same if the repository is renamed. Then the repository variables (Settings → Secrets and variables →
Actions → Variables; they're ids, not credentials):

| Variable | Value |
|---|---|
| `MITAYO_FIREBASE_PROJECT_ID` | `<FIREBASE_PROJECT_ID>` |
| `MITAYO_WIF_PROVIDER` | `projects/<PROJECT_NUMBER>/locations/global/workloadIdentityPools/github/providers/mitayo` |
| `MITAYO_FCM_SERVICE_ACCOUNT` | `github-fcm@<FIREBASE_PROJECT_ID>.iam.gserviceaccount.com` |

On a public repository GitHub Actions minutes are free; a private one gets 2,000 minutes a month, but
its releases can't be downloaded from the site. The signing key must never change: Android refuses an
update signed with a different key (the SHA-256 fingerprint is in the summary of every run).

**MAL obligations** (API agreement 4(b)): at the first public release, and at every major update (new
features, more load on their servers, ads or commercial parts), notify MAL through the
[Customer Support Form](https://myanimelist.net/about.php?go=support) and let them review the app.

## Website

The `site/` folder is published by Cloudflare Pages on the host set in `gradle.properties`
(`mitayo.site.host`, also used by the app for its links): a Pages project connected to the
repository, no build command, output folder `site`, so every push to the main branch publishes it.
Static HTML in Italian and English (language from the browser, IT/EN switch in `site.js`), no cookies.

- **`/`** (`index.html`): what the app does and how to get it.
- **`/download`** (`download.html`, `download.js`): reads the releases from the public GitHub API (from
  the browser, no token, 60 requests an hour per address, 10 minutes of cache) and shows the latest
  version with its notes and APK, then the older ones.
- **`/terms`** and **`/privacy`** (`terms.html`, `privacy.html`): terms of use and privacy policy,
  accepted together before the login and linked from Profile → About and Profile → Privacy. If either
  changes in substance, raise `POLICIES_VERSION` in `ui/AppLinks.kt`: the app asks every user to accept
  them again. The privacy policy lists every event and property sent to Firebase and the retention set
  in the console: update it when they change.
- `_headers` sets the CSP (scripts, styles, images and fonts from the site only, requests only to
  `api.github.com`).

## Project structure

| Folder | Contents |
|---|---|
| `data/auth` | MAL OAuth, PKCE, encrypted tokens, session (login/logout) |
| `data/mal` | REST client of the MAL API v2 and JSON models |
| `data/db` | Room: catalog, genres, local copy of the MAL list, skipped, outbox |
| `data` | repositories, deck queries, sync, outbox and its worker |
| `ui/categories` | categories: special lists, genres, themes, demographics |
| `ui/deck` | the deck to swipe: gestures, card, episodes watched, details |
| `ui/profile` | profile, stats, settings pages (appearance, content, notifications, data, privacy, about), logout |
| `ui/skipped` | the skipped anime: search, filter by list status, restore one, set its status |
| `ui/search` | search of the local catalog from the categories, to set a status without swiping |
| `ui/compare`, `data/ListComparison.kt`, `data/OtherUsers.kt` | comparison of two lists and the deck of another user's list |
| `data/reminders` | local reminders: types, schedule, alarms, worker, notifications |
| `data/push`, `data/telemetry` | Firebase: news topics and messaging service, statistics and crash reports |
| `ui/privacy` | the statistics question after the first login |
| `site/` | website: presentation, downloads, terms, privacy |
| `.github/` | release workflow, manual version push, `notify_update.py` |
| `data/update`, `ui/update` | update check, download and install, "What's new" |
| `app/src/main/assets/release-notes/` | notes of every version: release body and in-app "What's new" |

Stack: Kotlin, Jetpack Compose (Material 3), Room, DataStore, OkHttp + kotlinx.serialization, Coil 3,
WorkManager, Custom Tabs, Firebase (Analytics, Crashlytics, Cloud Messaging). Manual DI (`AppContainer`), a single module, minSdk 26.

Titles and buttons (in the app and on the site) use M PLUS Rounded 1c from Google Fonts, subset to
Latin, punctuation and kana (`res/font/`, `site/fonts/`), SIL Open Font License in
`licenses/M_PLUS_Rounded_1c-OFL.txt`.

Bugs and abuse reports: [GitHub Issues](https://github.com/EmanueleMelini/mitayo/issues).
Mitayo uses the official MyAnimeList API and isn't affiliated with MyAnimeList.

## License

Mitayo is free software under the [GNU General Public License v3.0](LICENSE): you can use, study, change
and share it, and whoever distributes a modified version must do so under the same license, with its
source code. The M PLUS Rounded 1c font keeps its own SIL Open Font License
(`licenses/M_PLUS_Rounded_1c-OFL.txt`).
