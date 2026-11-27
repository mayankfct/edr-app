package com.forensiccybertech.eagleye.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.forensiccybertech.eagleye.ui.theme.Eagle

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Eagle.colors.chip, unfocusedContainerColor = Eagle.colors.chip,
    focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary, unfocusedBorderColor = Eagle.colors.border,
)

/** Pill-shaped search box that shows a dropdown of matching suggestions (users, hosts, ...) while typing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestField(
    value: String, onValueChange: (String) -> Unit, label: String, suggestions: List<String>,
    onPick: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) {
        if (value.isBlank()) emptyList()
        else suggestions.filter { it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true) }.distinct().take(8)
    }
    val show = expanded && matches.isNotEmpty()
    ExposedDropdownMenuBox(show, { expanded = it }, modifier) {
        OutlinedTextField(
            value, { onValueChange(it); expanded = true }, placeholder = { Text(label) }, singleLine = true,
            shape = RoundedCornerShape(28.dp), colors = fieldColors(),
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (value.isNotEmpty()) ({ IconButton({ onValueChange(""); onSearch() }) { Icon(Icons.Filled.Close, "Clear") } }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { expanded = false; onSearch() }),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(show, { expanded = false }) {
            matches.forEach { s -> DropdownMenuItem({ Text(s) }, { expanded = false; onPick(s) }) }
        }
    }
}

/** Read-only dropdown to pick one option (e.g. a host scope); `null` value means "All". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerField(
    label: String, selectedLabel: String, options: List<Pair<String?, String>>, onSelect: (String?) -> Unit, modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            selectedLabel, {}, readOnly = true, label = { Text(label) }, singleLine = true,
            shape = RoundedCornerShape(12.dp), colors = fieldColors(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.forEach { (value, text) -> DropdownMenuItem({ Text(text) }, { expanded = false; onSelect(value) }) }
        }
    }
}
