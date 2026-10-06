@file:OptIn(ExperimentalMaterial3Api::class)

package com.radovankosc.ridelog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.radovankosc.ridelog.app
import com.radovankosc.ridelog.data.Fmt
import com.radovankosc.ridelog.data.GoogleMaps
import com.radovankosc.ridelog.data.searchKey
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val colors = lightColorScheme(
    primary = Color(0xFFB71C1C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF410002),
)

@Composable
fun RideLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "rides") {
        composable("rides") { RidesScreen(nav) }
        composable("customers") { CustomersScreen(nav) }
        composable("reports") { ReportsScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("newride") { NewRideScreen(nav) }
        composable("ride/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            RideDetailScreen(nav, it.arguments!!.getLong("id"))
        }
        composable("customer/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            CustomerEditScreen(nav, it.arguments!!.getLong("id"))
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("rides", "Jazdy", Icons.Filled.Home),
    Tab("customers", "Zákazníci", Icons.Filled.Person),
    Tab("reports", "Prehľady", Icons.Filled.DateRange),
    Tab("settings", "Nastavenia", Icons.Filled.Settings),
)

private val barColors
    @Composable get() = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onPrimary,
        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
    )

/** A main screen: title bar on top, the four sections at the bottom. */
@Composable
fun TabScaffold(
    nav: NavController,
    current: String,
    title: String,
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, colors = barColors) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = t.route == current,
                        onClick = {
                            if (t.route != current) {
                                nav.navigate(t.route) {
                                    popUpTo("rides") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = fab,
        content = content,
    )
}

/** A screen opened from another one, with a back arrow. */
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Späť") }
                },
                actions = { actions() },
                colors = barColors,
            )
        },
        content = content,
    )
}

@Composable
fun EmptyMessage(text: String, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, Modifier.width(120.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

/** A row of tappable suggestions shown under a text field. */
@Composable
fun SuggestionList(items: List<String>, icon: ImageVector, onPick: (String) -> Unit) {
    if (items.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { onPick(s) }.padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(s)
            }
        }
    }
}

/**
 * Address input that suggests addresses used before and, with an API key in Settings,
 * addresses from Google as you type.
 */
@Composable
fun AddressField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    knownAddresses: List<String>,
    modifier: Modifier = Modifier,
) {
    val settings by LocalContext.current.app.settings.state.collectAsState()
    var typing by remember { mutableStateOf(false) }
    var google by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(value, typing) {
        google = emptyList()
        if (!typing || value.trim().length < 3 || settings.mapsApiKey.isBlank()) return@LaunchedEffect
        delay(400)
        google = runCatching {
            GoogleMaps.suggestAddresses(settings.mapsApiKey, value.trim(), settings.regionCode)
        }.getOrDefault(emptyList())
    }

    val key = searchKey(value)
    val local = if (!typing || key.length < 2) emptyList() else {
        knownAddresses.filter { it != value && searchKey(it).contains(key) }.take(3)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                typing = true
                onValueChange(it)
            },
            label = { Text(label) },
            maxLines = 3,
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = {
                        typing = true
                        onValueChange("")
                    }) { Icon(Icons.Filled.Clear, "Vymazať") }
                }
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) typing = false },
        )
        if (typing) {
            SuggestionList((local + google).distinct().take(6), Icons.Filled.Place) {
                typing = false
                onValueChange(it)
            }
        }
    }
}

@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = Fmt.date(date),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Covers the field so a tap anywhere on it opens the calendar.
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Zrušiť") } },
        ) {
            DatePicker(state)
        }
    }
}

