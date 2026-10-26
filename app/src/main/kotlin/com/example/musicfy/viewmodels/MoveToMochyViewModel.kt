// MoveToMochyViewModel.kt

package com.example.musicfy.viewmodels

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.core.updater.apkFileFor
import com.example.musicfy.core.updater.downloadApk
import com.example.musicfy.core.updater.getLatestRelease
import com.example.musicfy.core.updater.installApk
import com.example.musicfy.core.updater.isNewerThanInstalled
import com.example.musicfy.importer.transfer.TransferException
import com.example.musicfy.importer.transfer.TransferExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** where one step of the move stands, progress is null while there's no number to show yet */
sealed interface MoveStep {
    data object Idle : MoveStep
    data class Working(val progress: Float?) : MoveStep
    data object Done : MoveStep
    data class Failed(val message: String) : MoveStep
}

/** the move to mochy page: export everything, then fetch mochy itself and hand it to the installer */
@HiltViewModel
class MoveToMochyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: TransferExporter,
) : ViewModel() {
    private val _export = MutableStateFlow<MoveStep>(
        if (TransferExporter.lastExportedAt(context) > 0L) MoveStep.Done else MoveStep.Idle
    )
    val export: StateFlow<MoveStep> = _export.asStateFlow()

    private val _app = MutableStateFlow<MoveStep>(MoveStep.Idle)
    val app: StateFlow<MoveStep> = _app.asStateFlow()

    private val _mochyInstalled = MutableStateFlow(installedMochy() != null)
    val mochyInstalled: StateFlow<Boolean> = _mochyInstalled.asStateFlow()

    /** the name the export suggests, so it's easy to spot in downloads later */
    fun exportFileName(): String = "musicfy-export-${LocalDate.now()}.zip"

    fun exportTo(target: Uri) {
        if (_export.value is MoveStep.Working) return
        _export.value = MoveStep.Working(0f)
        viewModelScope.launch {
            exporter.export(target) { _export.value = MoveStep.Working(it) }
                .onSuccess { _export.value = MoveStep.Done }
                .onFailure {
                    Timber.e(it, "Export for Mochy failed")
                    _export.value = MoveStep.Failed(it.message ?: "Couldn't export. Try again.")
                }
        }
    }

    /** downloads the newest mochy, keeps a copy in downloads and opens the installer */
    fun getMochy() {
        if (_app.value is MoveStep.Working) return
        if (_mochyInstalled.value) {
            _app.value = MoveStep.Done
            return
        }
        _app.value = MoveStep.Working(null)
        viewModelScope.launch {
            runCatching {
                // mochy-music still holds the old musicfy releases, so only something newer than this bridge is mochy
                val release = getLatestRelease(force = true).getOrThrow()?.takeIf { isNewerThanInstalled(it.version) }
                    ?: throw TransferException("Mochy isn't out yet. Check back soon!")
                if (release.apkUrl == null) throw TransferException("This Mochy release has no app attached yet. Check back soon!")
                val apk = downloadApk(context, release) { _app.value = MoveStep.Working(it) }.getOrThrow()
                withContext(Dispatchers.IO) { saveToDownloads(apk, release.apkName ?: apkFileFor(context, release).name) }
                apk
            }.onSuccess { apk ->
                _app.value = if (installApk(context, apk)) {
                    MoveStep.Idle
                } else {
                    MoveStep.Failed("Allow \"Install unknown apps\" for Musicfy, then tap the button again.")
                }
            }.onFailure {
                Timber.e(it, "Getting Mochy failed")
                _app.value = MoveStep.Failed(
                    (it as? TransferException)?.message ?: "Couldn't download Mochy. Check your connection and try again."
                )
            }
        }
    }

    /** looks again for mochy, called whenever the page comes back from the installer */
    fun refreshInstalled() {
        val installed = installedMochy() != null
        _mochyInstalled.value = installed
        if (installed) _app.value = MoveStep.Done
    }

    /** opens mochy, false when it isn't there to open */
    fun openMochy(): Boolean {
        val pkg = installedMochy() ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }

    private fun installedMochy(): String? = MochyPackages.firstOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    // the mock promises a copy in downloads, so it can be installed again later without this app
    private fun saveToDownloads(apk: File, name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching { copyIntoDownloads(apk, name) }.onFailure { Timber.w(it, "Couldn't keep a copy of Mochy in Downloads") }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyIntoDownloads(apk: File, name: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out -> apk.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    }

    companion object {
        /** mochy's app id, and its debug build so a test phone finds it too */
        val MochyPackages = listOf("com.mochyy.mochymusic", "com.mochyy.mochymusic.debug")
    }
}
