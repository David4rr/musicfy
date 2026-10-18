// LibraryLocalImport.kt
//
// Bringing music in from the phone, from the Library's Local tab. With storage access, Musicfy
// lists every song on the phone in the Add music sheet; without it, the system file picker lets
// the user choose files by hand. Either way the import runs in LocalImportService and its progress
// sheet opens straight away.

package com.example.musicfy.ui.screens.library

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.musicfy.R
import com.example.musicfy.importer.DeviceMusicScanner
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.TopToaster
import com.example.musicfy.viewmodels.LibraryImportViewModel

/** Audio types the picker offers: every audio type, plus the common ones named outright. */
private val LocalAudioMimeTypes = arrayOf(
    "audio/*",
    "audio/mpeg",
    "audio/mp4",
    "audio/flac",
    "audio/ogg",
    "audio/wav",
    "audio/x-wav",
    "audio/aac",
)

/** The Local tab's ways in, as one handle. */
@Stable
class LocalImportActions internal constructor(hasStorageAccess: Boolean) {
    /** Musicfy may read the phone's music; until then the tab offers to ask. */
    var hasStorageAccess by mutableStateOf(hasStorageAccess)
        internal set

    internal var pickFiles: () -> Unit = {}
    internal var askForStorage: () -> Unit = {}
    internal var openAddMusic: () -> Unit = {}

    /** The import card: the songs found on the phone when Musicfy may look, the file picker otherwise. */
    fun importFromPhone() = if (hasStorageAccess) openAddMusic() else pickFiles()

    /** The storage card and Re-scan: asks for access first if needed, then lists what's on the phone. */
    fun scanPhone() = if (hasStorageAccess) openAddMusic() else askForStorage()
}

@Composable
fun rememberLocalImportActions(viewModel: LibraryImportViewModel): LocalImportActions {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val actions = remember { LocalImportActions(DeviceMusicScanner.hasAccess(context)) }

    // access can be given or taken away in Settings while the app is in the background
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        actions.hasStorageAccess = DeviceMusicScanner.hasAccess(context)
    }

    val showProgress = { menuState.showTransferSheet(TransferKind.LocalImport, viewModel) }
    val openAddMusic = {
        viewModel.scanDevice()
        menuState.showAddLocalMusicSheet(viewModel, onStarted = showProgress)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        if (!viewModel.importPicked(uris)) {
            TopToaster.show("An import is still running. Try again once it's done.", iconRes = R.drawable.library_music)
        }
        showProgress()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        actions.hasStorageAccess = granted
        if (granted) {
            openAddMusic()
        } else {
            // a second "no" stops Android asking at all, so point to where it can still be turned on
            TopToaster.show(
                text = "Musicfy can't see your music without access",
                iconRes = R.drawable.storage,
                actionLabel = "Settings",
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }
    }

    SideEffect {
        actions.pickFiles = { picker.launch(LocalAudioMimeTypes) }
        actions.askForStorage = { permission.launch(DeviceMusicScanner.permission) }
        actions.openAddMusic = openAddMusic
    }
    return actions
}
