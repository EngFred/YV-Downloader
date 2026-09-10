package com.engfred.yvd.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.engfred.yvd.domain.model.SearchResult

/**
 * Dropdown list of search suggestions shown below the search bar.
 *
 * Each suggestion is tappable — [onSuggestionClick] is called with the suggestion text,
 * which should trigger a new search for that query.
 */
@Composable
fun SearchSuggestions(
    suggestions: List<String>,
    recentSearches: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit,
    onRecentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayItems = if (suggestions.isNotEmpty()) suggestions else recentSearches
    if (displayItems.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            userScrollEnabled = false
        ) {
            if (suggestions.isEmpty() && recentSearches.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            items(displayItems, key = { it }) { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (suggestions.isNotEmpty()) onSuggestionClick(suggestion)
                            else onRecentClick(suggestion)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (suggestions.isNotEmpty()) Icons.Rounded.Search
                        else Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
