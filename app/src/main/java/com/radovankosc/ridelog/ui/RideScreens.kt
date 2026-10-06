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
import androidx.compose.material.icons.filled.Edit
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
        nav, "rides", "Jazdy",
        fab = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate("newride") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nová jazda") },
            )
        },
    ) { padding ->
        val list = rides ?: return@TabScaffold
        if (list.isEmpty()) {
            EmptyMessage("Zatiaľ žiadne jazdy.\nŤuknite na „Nová jazda“ a zapíšte prvú.", padding)
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
                "Č. ${Fmt.receiptNo(r)} · ${Fmt.date(r.dateEpochDay)} · ${Fmt.trip(r)} · ${Fmt.km(r.totalKm)}",
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
fun NewRideScreen(nav: NavController, editId: Long = 0L) {
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
    // The saved ride being edited; null when logging a new one.
    var original by remember { mutableStateOf<Ride?>(null) }
    var loaded by remember { mutableStateOf(editId == 0L) }

    LaunchedEffect(editId) {
        if (loaded) return@LaunchedEffect
        val r = app.db.rides().get(editId) ?: return@LaunchedEffect
        original = r
        customerId = r.customerId
        query = app.db.customers().get(r.customerId)?.name.orEmpty()
        date = LocalDate.ofEpochDay(r.dateEpochDay)
        from = r.fromAddress
        to = r.toAddress
        roundTrip = r.roundTrip
        kmText = Fmt.plain(r.oneWayKm)
        calculatedFor = r.fromAddress to r.toAddress
        loaded = true
    }

    val customer = customers.firstOrNull { it.id == customerId }
    // An edited receipt keeps the rate it was made with.
    val ratePerKm = original?.ratePerKm ?: settings.ratePerKm
    val startFee = original?.startFee ?: settings.startFee

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
            kmNote = "Najkratšia trasa autom podľa Google Máp"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kmNote = "Vzdialenosť sa nepodarilo zistiť (${e.message}). Km môžete zadať ručne."
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
    val price = totalKm?.let { Pricing.price(it, ratePerKm, startFee) }

    fun save() {
        val name = query.trim()
        error = when {
            name.isEmpty() -> "Zadajte meno zákazníka."
            from.isBlank() || to.isBlank() -> "Zadajte obe adresy."
            oneWayKm == null || oneWayKm <= 0 -> "Zadajte vzdialenosť v km."
            original != null && date.year != original!!.receiptYear ->
                "Dátum musí zostať v roku ${original!!.receiptYear}, aby sedelo číslovanie dokladov."
            else -> null
        }
        if (error != null || oneWayKm == null || totalKm == null || price == null) return
        saving = true
        val editing = original
        scope.launch {
            try {
                val saved = app.db.withTransaction {
                    val existing = customer ?: customers.firstOrNull { searchKey(it.name) == searchKey(name) }
                    val c = existing ?: Customer(name = name, homeAddress = from.trim()).let {
                        it.copy(id = app.db.customers().insert(it))
                    }
                    if (editing != null) {
                        val updated = editing.copy(
                            customerId = c.id,
                            dateEpochDay = date.toEpochDay(),
                            fromAddress = from.trim(),
                            toAddress = to.trim(),
                            roundTrip = roundTrip,
                            oneWayKm = oneWayKm,
                            totalKm = totalKm,
                            price = price,
                        )
                        app.db.rides().update(updated)
                        return@withTransaction updated
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
                        minNumber = settings.minNumber(date.year),
                    )
                }
                if (editing != null) {
                    Toast.makeText(context, "Doklad č. ${Fmt.receiptNo(saved)} upravený", Toast.LENGTH_SHORT).show()
                    nav.popBackStack()
                } else {
                    Toast.makeText(context, "Uložené ako doklad č. ${Fmt.receiptNo(saved)}", Toast.LENGTH_SHORT).show()
                    nav.navigate("ride/${saved.id}") { popUpTo("newride") { inclusive = true } }
                }
            } catch (e: Exception) {
                error = "Nepodarilo sa uložiť: ${e.message}"
                saving = false
            }
        }
    }

    val title = original?.let { "Upraviť doklad č. ${Fmt.receiptNo(it)}" } ?: "Nová jazda"
    DetailScaffold(title, onBack = { nav.popBackStack() }) { padding ->
        if (!loaded) return@DetailScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Zákazník")
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
                        }) { Text("Zmeniť") }
                    }
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Meno zákazníka") },
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
                            "Nový zákazník „${query.trim()}“ sa uloží spolu s jazdou.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle("Jazda")
            DateField("Dátum", date, { date = it }, Modifier.fillMaxWidth())
            AddressField("Odkiaľ (začiatok jazdy)", from, { from = it }, known)
            AddressField("Kam (koniec jazdy)", to, { to = it }, known)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !roundTrip,
                    onClick = { roundTrip = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Jednosmerne") }
                SegmentedButton(
                    selected = roundTrip,
                    onClick = { roundTrip = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Tam a späť") }
            }

            OutlinedTextField(
                value = kmText,
                onValueChange = { kmText = it },
                label = { Text("Vzdialenosť jedným smerom (km)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    if (calculating) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else if (settings.mapsApiKey.isNotBlank()) {
                        IconButton(onClick = { scope.launch { calculateKm() } }) {
                            Icon(Icons.Filled.Refresh, "Vypočítať znova")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            val note = kmNote ?: if (settings.mapsApiKey.isBlank()) {
                "Pridajte kľúč Google Máp v Nastaveniach a km sa vyplnia samé."
            } else {
                null
            }
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Spolu: ${totalKm?.let(Fmt::km) ?: "–"}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "${Fmt.money(startFee)} štartovné + ${Fmt.money(ratePerKm)} za km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Cena: ${price?.let(Fmt::money) ?: "–"}",
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
            ) { Text(if (original != null) "Uložiť zmeny" else "Uložiť jazdu a vytvoriť doklad") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun RideDetailScreen(nav: NavController, id: Long) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val row by remember(id) { app.db.rides().observe(id) }.collectAsState(initial = null)
    var isLatest by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val current = row
    LaunchedEffect(current?.ride?.receiptYear) {
        val r = current?.ride ?: return@LaunchedEffect
        isLatest = app.db.rides().maxNumber(r.receiptYear) == r.receiptNumber
    }

    DetailScaffold(
        title = current?.let { "Doklad č. ${Fmt.receiptNo(it.ride)}" } ?: "Jazda",
        onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { nav.navigate("editride/$id") }) { Icon(Icons.Filled.Edit, "Upraviť doklad") }
            if (isLatest) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Zmazať jazdu") }
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
            InfoRow("Číslo dokladu", Fmt.receiptNo(r))
            InfoRow("Dátum", Fmt.date(r.dateEpochDay))
            InfoRow("Odkiaľ", r.fromAddress)
            InfoRow("Kam", r.toAddress)
            InfoRow("Jazda", Fmt.trip(r))
            InfoRow("Vzdialenosť", Fmt.km(r.totalKm))
            InfoRow("Sadzba", "${Fmt.money(r.startFee)} + ${Fmt.money(r.ratePerKm)}/km")
            InfoRow("Cena", Fmt.money(r.price))
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { PdfMaker.receipt(context, current) }
                        Share.file(
                            context, file, "application/pdf",
                            subject = "Doklad č. ${Fmt.receiptNo(r)}",
                            text = "Doklad č. ${Fmt.receiptNo(r)} – ${current.customerName}, ${Fmt.date(r.dateEpochDay)}",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Zdieľať alebo vytlačiť doklad (PDF)")
            }
            OutlinedButton(
                onClick = { nav.navigate("customer/${r.customerId}") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Otvoriť zákazníka") }
            if (!isLatest) {
                Text(
                    "Zmazať sa dá len posledný doklad v roku, aby v číslovaní neboli medzery.",
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
            title = { Text("Zmazať doklad č. ${Fmt.receiptNo(current.ride)}?") },
            text = { Text("Jazda sa odstráni a ďalšia jazda dostane opäť toto číslo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        app.db.rides().delete(current.ride)
                        nav.popBackStack()
                    }
                }) { Text("Zmazať") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Zrušiť") } },
        )
    }
}
