package com.deckwatch.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.symbols.SymbolLibrary
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.SymbolInfo

/**
 * A searchable, series-grouped symbol grid — the picker of §10.2.
 *
 * Every user-facing string is a parameter: this module carries no `strings.xml`
 * and must stay bilingual-safe (C8).
 *
 * @param symbols the catalogue rows to offer, normally every row of
 *   `docs/SYMBOL_KEYS.md` as loaded from the seed data.
 * @param nameOf picks the label to show for a row — pass the Turkish name when
 *   the app language is Turkish. Search always matches key, English and
 *   Turkish name regardless of what is displayed.
 * @param seriesLabels localised heading per series code (`LSS`, `FES`, …);
 *   a missing entry falls back to the bare code.
 */
@Composable
fun SymbolPickerSheet(
    symbols: List<SymbolInfo>,
    onPick: (String) -> Unit,
    searchLabel: String,
    emptyLabel: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    clearSearchLabel: String? = null,
    seriesLabels: Map<String, String> = emptyMap(),
    selectedKey: String? = null,
    nameOf: (SymbolInfo) -> String = { it.nameEn },
) {
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(symbols, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            symbols
        } else {
            symbols.filter { info ->
                info.key.contains(needle, ignoreCase = true) ||
                    info.nameEn.contains(needle, ignoreCase = true) ||
                    info.nameTr.contains(needle, ignoreCase = true)
            }
        }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { info -> info.series.ifBlank { SymbolLibrary.seriesOf(info.key) } }
    }
    val order = remember(grouped) {
        SymbolLibrary.seriesOrder.filter(grouped::containsKey) +
            grouped.keys.filterNot(SymbolLibrary.seriesOrder::contains).sorted()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    start = Dimens.SpacingL,
                    end = Dimens.SpacingL,
                    top = Dimens.SpacingL,
                    bottom = Dimens.SpacingS,
                ),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(searchLabel) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = clearSearchLabel)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
        )
        if (filtered.isEmpty()) {
            Text(
                text = emptyLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Dimens.SpacingL),
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = CellMinWidth),
            contentPadding = PaddingValues(
                horizontal = Dimens.SpacingM,
                vertical = Dimens.SpacingS,
            ),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            order.forEach { series ->
                val rows = grouped[series].orEmpty()
                if (rows.isEmpty()) return@forEach
                item(
                    key = "header-$series",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    SeriesHeader(label = seriesLabels[series] ?: series)
                }
                items(items = rows, key = { it.key }) { info ->
                    SymbolCell(
                        info = info,
                        label = nameOf(info),
                        selected = info.key == selectedKey,
                        onPick = { onPick(info.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesHeader(label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                top = Dimens.SpacingM,
                bottom = Dimens.SpacingXs,
                start = Dimens.SpacingXs,
            ),
        )
        HorizontalDivider()
    }
}

@Composable
private fun SymbolCell(
    info: SymbolInfo,
    label: String,
    selected: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CellMinHeight)
            .clip(RoundedCornerShape(Dimens.ChipCorner))
            .background(background)
            .clickable(onClick = onPick)
            .padding(vertical = Dimens.SpacingS, horizontal = Dimens.SpacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SymbolTile(
            symbolKey = info.key,
            size = SymbolTileDefaults.PickerSize,
            contentDescription = label,
        )
        Spacer(Modifier.height(Dimens.SpacingXs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = info.key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private val CellMinWidth = 84.dp
private val CellMinHeight = 96.dp

@Preview
@Composable
private fun SymbolPickerSheetPreview() {
    DeckWatchTheme {
        SymbolPickerSheet(
            symbols = listOf(
                SymbolInfo("FES001", "Fire extinguisher", "Yangın söndürücü", "FES", true),
                SymbolInfo("LSS005", "Lifebuoy", "Can simidi", "LSS"),
                SymbolInfo("EES001", "First aid", "İlk yardım", "EES"),
            ),
            onPick = {},
            searchLabel = "Search",
            emptyLabel = "No symbols",
        )
    }
}
