package com.photoselectortoolbox.ui.selector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoselectortoolbox.domain.interaction.FilingAction
import com.photoselectortoolbox.domain.interaction.GestureRow
import com.photoselectortoolbox.domain.interaction.SelectorGestures
import com.photoselectortoolbox.ui.theme.Zinc400
import com.photoselectortoolbox.ui.theme.Zinc50
import com.photoselectortoolbox.ui.theme.Zinc700
import com.photoselectortoolbox.ui.theme.Zinc800

/**
 * Every shortcut and gesture, on demand.
 *
 * Generated from [SelectorGestures], which is the same object the bindings read.
 * A hand-written list of shortcuts drifts from the code the first time a key
 * changes and then quietly lies for the rest of the product's life — which is
 * precisely what happened to the fullscreen gesture card this replaces the
 * habits of.
 *
 * Reachable by `?`, by the keyboard glyph in the control block, and from the
 * overflow menu. On demand rather than permanent: a legend that is always on
 * screen is chrome, and chrome here costs frame height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutSheet(
    filingAction: FilingAction,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Zinc800,
        modifier = Modifier.testTag("shortcut_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionHeading("Keyboard")
            SelectorGestures.selectorShortcutRows(filingAction).forEach { RowLine(it) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .height(1.dp)
                    .background(Zinc700),
            )

            SectionHeading("Fullscreen gestures")
            SelectorGestures.fullscreenHintRows().forEach { RowLine(it) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .height(1.dp)
                    .background(Zinc700),
            )

            SectionHeading("On the frames")
            listOf(
                GestureRow("tap a neighbour", "go to that frame"),
                GestureRow("tap the current frame", "open fullscreen"),
                GestureRow("tap the ⛱ badge", "fill the screen with that frame"),
                GestureRow("long-press a frame", "context menu"),
            ).forEach { RowLine(it) }

            Text(
                text = "No gesture moves, copies or deletes a photo. Those are buttons, " +
                    "the keys above, and the context menu — all of which say what they do.",
                fontSize = 12.sp,
                color = Zinc400,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = Zinc400,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun RowLine(row: GestureRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.input,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = Zinc50,
            modifier = Modifier.width(120.dp),
        )
        Text(text = row.effect, fontSize = 13.sp, color = Zinc400)
    }
}
