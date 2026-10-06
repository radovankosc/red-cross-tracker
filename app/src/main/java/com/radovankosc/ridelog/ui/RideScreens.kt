@file:OptIn(ExperimentalMaterial3Api::class)

package com.radovankosc.ridelog.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.room.withTransaction
import com.radovankosc.ridelog.app
import com.radovankosc.ridelog.data.Customer
import com.radovankosc.ridelog.data.Fmt
import com.radovankosc.ridelog.data.GoogleMaps
import com.radovankosc.ridelog.data.Pricing
import com.radovankosc.ridelog.data.Ride
import com.radovankosc.ridelog.data.RideRow
import com.radovankosc.ridelog.data.searchKey
import com.radovankosc.ridelog.pdf.PdfMaker
import com.radovankosc.ridelog.pdf.Share
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun RidesScreen(nav: NavController) {
    val app = LocalContext.current.app
    val rides by remember { app.db.rides().recent() }.collectAsState(initial = null)
    TabScaffold(
        nav, "rides", "Rides",
        fab = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate("newride") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New ride") },
            )
        },
    ) { padding ->
        val list = rides ?: return@TabScaffold
        if (list.isEmpty()) {
            EmptyMessage("No rides yet.\nTap “New ride” to log the first one.", padding)
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.ride.id }) { row ->
                    RideCard(row) { nav.navigate("ride/${row.ride.id}") }
                }
            }
        }
    }
}

@Composable
fun RideCard(row: RideRow, onClick: () -> Unit) {
    val r = row.ride
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.customerName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(Fmt.money(r.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                "No. ${Fmt.receiptNo(r)} · ${Fmt.date(r.dateEpochDay)} · ${Fmt.trip(r)} · ${Fmt.km(r.totalKm)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${r.fromAddress} → ${r.toAddress}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun NewRideScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()
    val customers by remember { app.db.customers().all() }.collectAsState(initial = emptyList())
    val known by remember { app.db.rides().knownAddresses() }.collectAsState(initial = emptyList())

    var query by rememberSaveable { mutableStateOf("") }
    var customerId by rememberSaveable { mutableStateOf(0L) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var roundTrip by rememberSaveable { mutableStateOf(true) }
    var kmText by rememberSaveable { mutableStateOf("") }
    var kmNote by remember { mutableStateOf<String?>(null) }
    var calculating by remember { mutableStateOf(false) }
    var calculatedFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val customer = customers.firstOrNull { it.id == customerId }

    suspend fun calculateKm() {
        val f = from.trim()
        val t = to.trim()
        if (settings.mapsApiKey.isBlank() || f.isEmpty() || t.isEmpty()) return
        calculating = true
        kmNote = null
        try {
            val km = GoogleMaps.shortestDrivingKm(settings.mapsApiKey, f, t)
            kmText = Fmt.plain(km)
            calculatedFor = f to t
            kmNote = "Shortest car route on Google Maps"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kmNote = "Couldn't get the distance (${e.message}). You can type the km yourself."
        } finally {
            calculating = false
        }
    }

    // Look the distance up shortly after the addresses stop changing.
    LaunchedEffect(from, to, settings.mapsApiKey) {
        if (from.trim().length < 4 || to.trim().length < 4 || calculatedFor == (from.trim() to to.trim())) return@LaunchedEffect
        delay(1200)
        calculateKm()
    }

    fun pick(c: Customer) {
        customerId = c.id
        query = c.name
        from = c.homeAddress
        to = c.lastDestination
        error = null
        scope.launch {
            app.db.rides().lastForCustomer(c.id)?.let { last ->
                roundTrip = last.roundTrip
                if (from.isBlank()) from = last.fromAddress
                if (to.isBlank()) to = last.toAddress
            }
        }
    }

    val oneWayKm = Fmt.parse(kmText)
    val totalKm = oneWayKm?.let { Pricing.totalKm(it, roundTrip) }
    val price = totalKm?.let { Pricing.price(it, settings.ratePerKm, settings.startFee) }

    fun save() {
        val name = query.trim()
        error = when {
            name.isEmpty() -> "Enter the customer's name."
            from.isBlank() || to.isBlank() -> "Enter both addresses."
            oneWayKm == null || oneWayKm <= 0 -> "Enter the distance in km."
            else -> null
        }
        if (error != null || oneWayKm == null || totalKm == null || price == null) return
        saving = true
        scope.launch {
            try {
                val saved = app.db.withTransaction {
                    val existing = customer ?: customers.firstOrNull { searchKey(it.name) == searchKey(name) }
                    val c = existing ?: Customer(name = name, homeAddress = from.trim()).let {
                        it.copy(id = app.db.customers().insert(it))
                    }
                    app.db.customers().update(
                        c.copy(lastDestination = to.trim(), homeAddress = c.homeAddress.ifBlank { from.trim() }),
                    )
                    app.db.rides().insertNumbered(
                        Ride(
                            customerId = c.id,
                            dateEpochDay = date.toEpochDay(),
                            fromAddress = from.trim(),
                            toAddress = to.trim(),
                            roundTrip = roundTrip,
                            oneWayKm = oneWayKm,
                            totalKm = totalKm,
                            ratePerKm = settings.ratePerKm,
                            startFee = settings.startFee,
                            price = price,
                            receiptYear = date.year,
                            receiptNumber = 0,
                        ),
                    )
                }
                Toast.makeText(context, "Saved as receipt ${Fmt.receiptNo(saved)}", Toast.LENGTH_SHORT).show()
                nav.navigate("ride/${saved.id}") { popUpTo("newride") { inclusive = true } }
            } catch (e: Exception) {
                error = "Couldn't save: ${e.message}"
                saving = false
            }
        }
    }

    DetailScaffold("New ride", onBack = { nav.popBackStack() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Customer")
            if (customer != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(customer.name, style = MaterialTheme.typography.titleLarge)
                            if (customer.phone.isNotBlank()) Text(customer.phone)
                        }
                        TextButton(onClick = {
                            customerId = 0L
                            query = ""
                        }) { Text("Change") }
                    }
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Customer name") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val key = searchKey(query)
                if (key.isNotEmpty()) {
                    val matches = customers.filter { searchKey(it.name).contains(key) }.take(5)
                    SuggestionList(matches.map { it.name }, Icons.Filled.Person) { picked ->
                        matches.firstOrNull { it.name == picked }?.let(::pick)
                    }
                    if (matches.none { searchKey(it.name) == key }) {
                        Text(
                            "New customer “${query.trim()}” will be saved with this ride.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle("Trip")
            DateField("Date", date, { date = it }, Modifier.fillMaxWidth())
            AddressField("From (pick-up)", from, { from = it }, known)
            AddressField("To (destination)", to, { to = it }, known)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !roundTrip,
                    onClick = { roundTrip = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("One way") }
                SegmentedButton(
                    selected = roundTrip,
                    onClick = { roundTrip = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Return") }
            }

            OutlinedTextField(
                value = kmText,
                onValueChange = { kmText = it },
                label = { Text("Distance one way (km)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    if (calculating) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else if (settings.mapsApiKey.isNotBlank()) {
                        IconButton(onClick = { scope.launch { calculateKm() } }) {
                            Icon(Icons.Filled.Refresh, "Calculate again")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            val note = kmNote ?: if (settings.mapsApiKey.isBlank()) {
                "Add a Google Maps key in Settings to fill in the km automatically."
            } else {
                null
            }
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Total distance: ${totalKm?.let(Fmt::km) ?: "–"}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "${Fmt.money(settings.startFee)} + ${Fmt.money(settings.ratePerKm)} per km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Price: ${price?.let(Fmt::money) ?: "–"}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { save() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save ride and create receipt") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun RideDetailScreen(nav: NavController, id: Long) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()
    val row by remember(id) { app.db.rides().observe(id) }.collectAsState(initial = null)
    var isLatest by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val current = row
    LaunchedEffect(current?.ride?.receiptYear) {
        val r = current?.ride ?: return@LaunchedEffect
        isLatest = app.db.rides().maxNumber(r.receiptYear) == r.receiptNumber
    }

    DetailScaffold(
        title = current?.let { "Receipt ${Fmt.receiptNo(it.ride)}" } ?: "Ride",
        onBack = { nav.popBackStack() },
        actions = {
            if (isLatest) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete ride") }
            }
        },
    ) { padding ->
        if (current == null) return@DetailScaffold
        val r = current.ride
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(current.customerName, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            InfoRow("Receipt no.", Fmt.receiptNo(r))
            InfoRow("Date", Fmt.date(r.dateEpochDay))
            InfoRow("From", r.fromAddress)
            InfoRow("To", r.toAddress)
            InfoRow("Trip", Fmt.trip(r))
            InfoRow("Distance", Fmt.km(r.totalKm))
            InfoRow("Rate", "${Fmt.money(r.startFee)} + ${Fmt.money(r.ratePerKm)}/km")
            InfoRow("Price", Fmt.money(r.price))
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        val customer = app.db.customers().get(r.customerId)
                        val file = withContext(Dispatchers.IO) { PdfMaker.receipt(context, current, customer, settings) }
                        Share.file(
                            context, file, "application/pdf",
                            subject = "Receipt ${Fmt.receiptNo(r)}",
                            text = "Receipt ${Fmt.receiptNo(r)} – ${current.customerName}, ${Fmt.date(r.dateEpochDay)}",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Share or print receipt (PDF)")
            }
            OutlinedButton(
                onClick = { nav.navigate("customer/${r.customerId}") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open customer") }
            if (!isLatest) {
                Text(
                    "Only the newest receipt of a year can be deleted, so the numbering has no gaps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete receipt ${Fmt.receiptNo(current.ride)}?") },
            text = { Text("The ride is removed and the next ride gets this number again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        app.db.rides().delete(current.ride)
                        nav.popBackStack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
