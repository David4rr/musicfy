<div align="center">
<img width="1799" height="775" alt="bye" src="https://github.com/user-attachments/assets/2f1c44cf-b060-49b9-a7d8-fc277e663392" />

# Musicfy is now Mochy Music

**[realidkroo/Mochy-Music](https://github.com/realidkroo/Mochy-Music)**

<a href="https://github.com/realidkroo/Mochy-Music/releases/latest">
  <img src="https://shieldcn.dev/github/release/realidkroo/Mochy-Music.svg?mode=dark&radius=999&font=geist&split=true&height=28&fontSize=13&labelColor=1e1e2e&labelTextColor=ffffff&color=313244&valueColor=ffffff&logoColor=cba6f7" alt="Mochy release">

</a>

</div>

---

> [!IMPORTANT]
> This repo only stays so older Musicfy apps can still find their last update, **Musicfy 8.1.2**. That version is the bridge: it packs up your whole library and hands you over to Mochy. Nothing new happens here anymore.

## Why the move?

- The app id `com.example.musicfy` always felt temporary, and its obviously not an example.
- Search for "Musicfy" and you get a pile of unrelated apps and websites. Too many products already use that name.
- We''d rather rename once now than again later. Sorry for the hassle <3

Because the app id changes to `com.mochyy.mochymusic`, Android sees Mochy as a **different app**. It can't update over Musicfy and it can't read Musicfy's data, so your library moves over with an export file instead. It takes a minute.

---

## How to move without losing anything

### 1. Get Musicfy 8.1.2
Open Musicfy and update like always (the in-app updater finds it), or download it from the [releases here](https://github.com/realidkroo/musicfy/releases/latest) and install it over your current Musicfy. Your library stays, like any normal update.

### 2. Export
Open Musicfy 8.1.2. A **"Sorry for the inconvenience!"** page pops up on Home. You can always get back to it from **Settings → Move to Mochy Music**.

Tap the button on **step 1** and pick where to save the `.zip` (Downloads is fine). Wait until it ticks. Big download collections take a little longer.

### 3. Install Mochy
Tap the button on **step 2**. Musicfy downloads the newest Mochy, keeps a copy in your Downloads, and opens the installer. If Android asks, allow "Install unknown apps" for Musicfy.

### 4. Import
Open Mochy. On onboarding, pick **Yes!** → **Import previous data** and choose the `.zip` from step 2. Mochy restarts once and everything is there.

### 5. Clean up
Check that your library looks right in Mochy, then delete Musicfy. You can delete the `.zip` too, or keep it as a backup.

---

## What comes over

| Comes over | Stays behind |
|---|---|
| Your library, liked songs, albums, artists and playlists | Logins: YouTube Music, Spotify, Apple Music, Tidal, Discord, Last.fm, Libre.fm. Sign in again in Mochy |
| Play history, listening time, stats and your monthly recap | The streaming cache (the Library **Cached** tab). It fills up again as you play |
| The Home algorithm, so recommendations feel the same | Music folders you picked yourself. Android doesn't let a new app reuse that access, so add them again in Library → Local |
| Every setting, player style, Audio Lab and EQ profiles | The queue that was playing |
| Downloaded songs, so they still play offline | |
| Local songs. Mochy finds them again if the files are still on your phone | |
| Covers, playlist covers, your profile card and photo | |

---

## Something went wrong?

- **"Mochy isn't out yet"**: the first Mochy release isn't up yet. Check back soon, your export stays valid.
- **The installer doesn't open**: allow "Install unknown apps" for Musicfy in Android settings, then tap step 2 again. You can also install the copy in your Downloads by hand.
- **Mochy says the export is from a newer version**: update Mochy first, then import again.
- **Not enough space**: importing needs about as much free space as the `.zip` takes, on top of the `.zip` itself.
- **Mochy looks empty after importing**: open it once more. It finishes the import on the next start if Android closed it halfway.
- Still stuck? Open an [issue on Mochy](https://github.com/realidkroo/Mochy-Music/issues) and say which step went wrong.

> [!TIP]
> Only download Musicfy and Mochy from these GitHub releases. A random mediafire or telegram link could be a modified app.

---

## License

GPL-3.0, see [LICENSE](LICENSE). Credits to every project Mochy builds on are in the [Mochy repo](https://github.com/realidkroo/Mochy-Music#these-incredible-project-helped-mochy-a-lot-check-them-out-too-3).

<div align="center">
  <sub>thanks for using musicfy, see you on mochy :3</sub>
</div>
