// LibraryManageSheets.kt
//
// "Manage imports" and "Manage local": two big tiles and a row per action, as in the mocks.
// Anything that takes music out of the library asks once more on a sheet pushed over this one.

package com.example.musicfy.ui.screens.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R
import com.example.musicfy.importer.ImportSource
import com.example.musicfy.ui.component.PopupSheetHandle
import com.example.musicfy.ui.component.PopupSheetState
import com.example.musicfy.ui.component.SheetPillButton
import com.example.musicfy.ui.player.menu.MenuRowSurface
import com.example.musicfy.ui.screens.search.SearchColors
import com.example.musicfy.viewmodels.LibraryImportViewModel

/** [sources] are the services that have brought music in, one "Delete ... imports" row each. */
fun PopupSheetState.showManageImportsSheet(sources: List<ImportSource>, viewModel: LibraryImportViewModel) {
    var sheet: PopupSheetHandle? = null
    fun confirmThen(title: String, message: String, action: String, run: () -> Unit) =
        showConfirm(title, message, action) {
            run()
            sheet?.dismiss()
        }
    sheet = showWithHandle {
        ManageLayout(
            first = ManageAction(R.drawable.delete, "Delete all imports") {
                confirmThen(
                    title = "Delete all imports?",
                    message = "Every song, like and playlist you imported leaves your library. Songs that were " +
                        "already in it stay, and so does music YouTube sync keeps up to date.",
                    action = "Delete all imports",
                    run = viewModel::deleteAllImports,
                )
            },
            second = ManageAction(R.drawable.undo, "Undo import session") {
                confirmThen(
                    title = "Undo your last import?",
                    message = "The songs, likes and playlists your last import brought in leave your library. " +
                        "Anything that was there before stays.",
                    action = "Undo import",
                    run = viewModel::undoLatestImport,
                )
            },
            rows = sources.map { source ->
                ManageAction(R.drawable.delete, "Delete ${source.label} imports") {
                    confirmThen(
                        title = "Delete ${source.label} imports?",
                        message = "Everything you imported from ${source.label} leaves your library, playlists included." +
                            if (source == ImportSource.YOUTUBE_MUSIC) " Music YouTube sync keeps up to date stays." else "",
                        action = "Delete",
                        run = { viewModel.deleteImportsFrom(source) },
                    )
                }
            },
        )
    }
}

/** [onRescan] and [onRemoveFromList] open their own sheets, so this one closes first. */
fun PopupSheetState.showManageLocalSheet(
    viewModel: LibraryImportViewModel,
    onRescan: () -> Unit,
    onRemoveFromList: () -> Unit,
) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        ManageLayout(
            first = ManageAction(R.drawable.refresh, "Re-scan local") {
                sheet?.dismiss()
                onRescan()
            },
            second = ManageAction(R.drawable.delete, "Delete from list") {
                sheet?.dismiss()
                onRemoveFromList()
            },
            rows = listOf(
                ManageAction(R.drawable.undo, "Undo import session") {
                    showConfirm(
                        title = "Undo your last local import?",
                        message = "The songs it added leave your library. The files stay on your phone.",
                        action = "Undo import",
                    ) {
                        viewModel.undoLatestLocalImport()
                        sheet?.dismiss()
                    }
                },
            ),
        )
    }
}

/** One more look before music leaves the library, pushed over the sheet that asked. */
private fun PopupSheetState.showConfirm(title: String, message: String, action: String, onConfirm: () -> Unit) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(
        buttonBar = {
            SheetPillButton(
                text = action,
                onClick = {
                    sheet?.dismiss()
                    onConfirm()
                },
            )
        },
    ) {
        Text(text = title, color = SearchColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(text = message, color = SearchColors.Secondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
    }
}

private class ManageAction(@DrawableRes val icon: Int, val label: String, val onClick: () -> Unit)

@Composable
private fun ManageLayout(first: ManageAction, second: ManageAction, rows: List<ManageAction>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ManageTile(first, Modifier.weight(1f))
            ManageTile(second, Modifier.weight(1f))
        }
        rows.forEach { ManageRow(it) }
    }
}

/** A tall tile: its icon in the top corner, the label at the bottom. */
@Composable
private fun ManageTile(action: ManageAction, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 92.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        ManageBadge(action.icon)
        Spacer(Modifier.height(14.dp))
        ManageLabel(action.label)
    }
}

@Composable
private fun ManageRow(action: ManageAction) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        ManageBadge(action.icon)
        Spacer(Modifier.width(12.dp))
        ManageLabel(action.label)
    }
}

/** The light circle with the action's icon, like the round markers in the mock. */
@Composable
private fun ManageBadge(@DrawableRes icon: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.88f)),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.8f),
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun ManageLabel(text: String) {
    Text(
        text = text,
        color = SearchColors.Primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
