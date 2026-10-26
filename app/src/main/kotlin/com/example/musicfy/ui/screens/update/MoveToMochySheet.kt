// MoveToMochySheet.kt

package com.example.musicfy.ui.screens.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.musicfy.R
import com.example.musicfy.ui.component.PopupSheetState
import com.example.musicfy.ui.component.pressBounce
import com.example.musicfy.ui.player.menu.MenuRowSurface
import com.example.musicfy.ui.player.menu.MenuSheetSurface
import com.example.musicfy.ui.screens.Screens
import com.example.musicfy.viewmodels.MoveStep
import com.example.musicfy.viewmodels.MoveToMochyViewModel

private val Soft = Color(0xFFDCDCDC)
private val Muted = Color(0xFFBDBDBD)
private val Faint = Color(0xFF8C8C8C)
private val ButtonFace = Color(0xFFD9D9D9)
private val ButtonInk = Color(0xFF1C1C1C)
private val NoteRed = Color(0xFFFF8A80)

// once per launch is plenty, the page is one tap away in settings after that
private var promptShownThisLaunch = false

/** opens the move page as a popup sheet, from settings and from the version sheet's update card */
fun PopupSheetState.showMoveToMochySheet() {
    show { MoveToMochyContent() }
}

/** pops the move page over home once per launch, so nobody misses that musicfy is moving */
@Composable
fun HomeMoveToMochyPrompt(currentRoute: String?) {
    if (currentRoute != Screens.Home.route) return
    var open by remember { mutableStateOf(!promptShownThisLaunch) }
    if (!open) return
    MenuSheetSurface(
        onDismiss = {
            promptShownThisLaunch = true
            open = false
        },
        wrapHeight = true,
        fullDetent = 0.94f,
    ) { _ -> MoveToMochyContent() }
}

/** the page from the mock: old mark to new mark, why, and the three steps that carry everything over */
@Composable
private fun MoveToMochyContent(vm: MoveToMochyViewModel = hiltViewModel()) {
    val export by vm.export.collectAsState()
    val app by vm.app.collectAsState()
    val installed by vm.mochyInstalled.collectAsState()
    var exportFirstHint by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    // the installer runs outside the app, so look for mochy again whenever we're back
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshInstalled() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 26.dp)
            .padding(top = 28.dp, bottom = 28.dp)
            .animateContentSize(),
    ) {
        BrandSwap()
        Spacer(Modifier.height(24.dp))
        Text(
            "Sorry for the inconvenience!",
            color = Color.White,
            fontSize = 30.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("The Musicfy name will be phased out.", color = Soft, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Musicfy is rebranding into Mochy Music. This changes the app id and the database id, so Mochy " +
                "installs as a new app and your library needs to move over. Here's what you can do:",
            color = Soft,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
        )

        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepCard(
                number = 1,
                text = "Export the whole database here. This won't include any 3rd party logins, integrations etc., so you'll need to log in again!",
                note = exportNote(export),
                noteIsError = export is MoveStep.Failed,
            ) {
                StepButton(icon = R.drawable.backup, step = export) { picker.launch(vm.exportFileName()) }
            }
            StepCard(
                number = 2,
                text = "Download and install the app from here. A copy is saved to your Downloads too. Install it, then delete this app.",
                note = if (exportFirstHint && export !is MoveStep.Done) "Export your library first (step 1), so nothing gets left behind." else appNote(app, installed),
                noteIsError = app is MoveStep.Failed || (exportFirstHint && export !is MoveStep.Done),
            ) {
                StepButton(
                    icon = R.drawable.download,
                    step = if (installed) MoveStep.Done else app,
                    dimmed = export !is MoveStep.Done && !installed,
                ) {
                    when {
                        installed -> vm.openMochy()
                        export !is MoveStep.Done -> exportFirstHint = true
                        else -> vm.getMochy()
                    }
                }
            }
            StepCard(
                number = 3,
                text = "On onboarding, select Import previous data, and you're set!",
                note = if (installed) "Mochy is ready. Open it and pick the .zip you exported." else null,
            ) {
                if (installed) StepButton(icon = R.drawable.arrow_forward, step = MoveStep.Idle) { vm.openMochy() }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Why is this change necessary?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WhyText("The app id already feels outdated (com.example.musicfy), and it's obviously not an example.")
            WhyText(
                "Then there's the reputation: search for Musicfy and you'll find lots of unrelated apps and websites. " +
                    "There are just too many products using Musicfy as their name."
            )
            WhyText("I'm sorry for the change and the inconvenience, but we want to make sure there's no more rebranding after this, hopefully <3")
            WhyText("After this, some bug patches may appear. Sorry!")
            WhyText("Thanks for using Musicfy.")
        }
        Spacer(Modifier.height(22.dp))
        Text("best regards\nmochyy team\nlast changed in 2026", color = Faint, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// the old mark, an arrow, the new mark, like the top of the mock
@Composable
private fun BrandSwap() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_musicfy_mark), contentDescription = "Musicfy", tint = Muted, modifier = Modifier.size(46.dp))
        Spacer(Modifier.width(16.dp))
        Icon(painterResource(R.drawable.arrow_forward), contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Icon(painterResource(R.drawable.ic_mochy_mark), contentDescription = "Mochy Music", tint = Color.White, modifier = Modifier.size(46.dp))
    }
}

@Composable
private fun StepCard(
    number: Int,
    text: String,
    note: String?,
    noteIsError: Boolean = false,
    button: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MenuRowSurface)
            .padding(start = 22.dp, end = 18.dp, top = 22.dp, bottom = 22.dp),
    ) {
        Row(modifier = Modifier.weight(1f)) {
            Text("$number.", color = Muted, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(18.dp))
            Column {
                Text(text, color = Muted, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
                if (note != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(note, color = if (noteIsError) NoteRed else Color.White, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        button()
    }
}

// the light square from the mock, it spins while working and ticks when done
@Composable
private fun StepButton(@DrawableRes icon: Int, step: MoveStep, dimmed: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .pressBounce(interaction, pressedScale = 0.94f)
            .graphicsLayer { alpha = if (dimmed) 0.45f else 1f }
            .clip(RoundedCornerShape(16.dp))
            .background(ButtonFace)
            .clickable(interactionSource = interaction, indication = null, enabled = step !is MoveStep.Working, onClick = onClick),
    ) {
        when (step) {
            is MoveStep.Working -> {
                val progress = step.progress
                if (progress == null) {
                    CircularProgressIndicator(color = ButtonInk, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                } else {
                    CircularProgressIndicator(progress = { progress }, color = ButtonInk, trackColor = ButtonInk.copy(alpha = 0.15f), strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                }
            }
            MoveStep.Done -> Icon(painterResource(R.drawable.check), contentDescription = null, tint = ButtonInk, modifier = Modifier.size(26.dp))
            else -> Icon(painterResource(icon), contentDescription = null, tint = ButtonInk, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun WhyText(text: String) {
    Text(text, color = Soft, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
}

private fun exportNote(step: MoveStep): String? = when (step) {
    is MoveStep.Working -> step.progress?.let { "Exporting… ${(it * 100).toInt()}%" } ?: "Exporting…"
    MoveStep.Done -> "Exported! Keep that .zip until Mochy has imported it."
    is MoveStep.Failed -> step.message
    MoveStep.Idle -> null
}

private fun appNote(step: MoveStep, installed: Boolean): String? = when {
    installed -> "Mochy is installed."
    step is MoveStep.Working -> step.progress?.let { "Downloading Mochy… ${(it * 100).toInt()}%" } ?: "Looking for the newest Mochy…"
    step is MoveStep.Failed -> step.message
    else -> null
}
