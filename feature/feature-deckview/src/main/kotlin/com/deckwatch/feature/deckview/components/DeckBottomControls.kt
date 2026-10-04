package com.deckwatch.feature.deckview.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.feature.deckview.R

/** Compass and the primary action occupy separate measured rows, outside the plan viewport. */
@Composable
internal fun DeckBottomControls(
    yawDeg: () -> Float,
    onTurn: (Float) -> Unit,
    onLevel: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(Dimens.SpacingS)) {
        DeckCompass(yawDeg = yawDeg, onTurn = onTurn, onLevel = onLevel)
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpacingS)
                .heightIn(min = Dimens.TouchTargetPrimary),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.deckview_add_equipment))
        }
    }
}
