@file:OptIn(ExperimentalMaterial3Api::class)

package com.radovankosc.ridelog.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.radovankosc.ridelog.app
import com.radovankosc.ridelog.data.AppSettings
import com.radovankosc.ridelog.data.Backup
import com.radovankosc.ridelog.data.Fmt
import com.radovankosc.ridelog.data.round1
import com.radovankosc.ridelog.data.round2
import com.radovankosc.ridelog.pdf.PdfMaker
import com.radovankosc.ridelog.pdf.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun ReportsScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()
    val today = LocalDate.now()

    var from by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var to by remember { mutableStateOf(today) }
    var email by remember { mutableStateOf(settings.reportEmail) }
    val rows by remember(from, to) { app.db.rides().inRange(from.toEpochDay(), to.toEpochDay()) }
        .collectAsState(initial = emptyList())

    val presets = listOf(
        "Tento mesiac" to (today.withDayOfMonth(1) to today),
        "Minulý mesiac" to today.minusMonths(1).withDayOfMonth(1).let { it to it.plusMonths(1).minusDays(1) },
        "Tento rok" to (today.withDayOfYear(1) to today),
        "Minulý rok" to today.minusYears(1).withDayOfYear(1).let { it to it.plusYears(1).minusDays(1) },
    )

    TabScaffold(nav, "reports", "Prehľady") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (label, range) ->
                    FilterChip(
                        selected = from == range.first && to == range.second,
                        onClick = {
                            from = range.first
                            to = range.second
                        },
                        label = { Text(label) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField("Od", from, { from = it }, Modifier.weight(1f))
                DateField("Do", to, { to = it }, Modifier.weight(1f))
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Počet dokladov: ${rows.size}", style = MaterialTheme.typography.titleMedium)
                    Text("Spolu km: ${Fmt.km(round1(rows.sumOf { it.ride.totalKm }))}")
                    Text(
                        "Suma spolu: ${Fmt.money(round2(rows.sumOf { it.ride.price }))}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Poslať na e-mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = rows.isNotEmpty() && !to.isBefore(from),
                onClick = {
                    if (email.trim() != settings.reportEmail) app.settings.save(settings.copy(reportEmail = email.trim()))
                    val snapshot = rows
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { PdfMaker.report(context, snapshot, from, to, settings) }
                        Share.file(
                            context, file, "application/pdf",
                            subject = "Prehľad jázd ${Fmt.date(from)} – ${Fmt.date(to)}",
                            text = "Prehľad jázd ${Fmt.date(from)} – ${Fmt.date(to)}: počet dokladov ${snapshot.size}, " +
                                "suma spolu ${Fmt.money(round2(snapshot.sumOf { it.ride.price }))}.",
                            email = email,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Email, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Poslať prehľad e-mailom (PDF)")
            }

            rows.forEach { row ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(Fmt.receiptNo(row.ride), Modifier.width(72.dp))
                    Text(Fmt.date(row.ride.dateEpochDay), Modifier.width(96.dp))
                    Text(row.customerName, Modifier.weight(1f))
                    Text(Fmt.money(row.ride.price))
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()

    var rate by remember { mutableStateOf(Fmt.plain(settings.ratePerKm)) }
    var fee by remember { mutableStateOf(Fmt.plain(settings.startFee)) }
    var driver by remember { mutableStateOf(settings.driverName) }
    var org by remember { mutableStateOf(settings.organization) }
    var email by remember { mutableStateOf(settings.reportEmail) }
    var key by remember { mutableStateOf(settings.mapsApiKey) }
    var region by remember { mutableStateOf(settings.regionCode) }
    var showKey by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    val year = LocalDate.now().year
    var maxUsed by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { maxUsed = app.db.rides().maxNumber(year) ?: 0 }
    val currentNext = maxOf(maxUsed + 1, settings.minNumber(year))
    var nextNo by remember(currentNext) { mutableStateOf(currentNext.toString()) }

    fun loadFields(s: AppSettings) {
        rate = Fmt.plain(s.ratePerKm)
        fee = Fmt.plain(s.startFee)
        driver = s.driverName
        org = s.organization
        email = s.reportEmail
        key = s.mapsApiKey
        region = s.regionCode
    }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = Backup.export(app.db, app.settings.state.value)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) }
                }
                Toast.makeText(context, "Záloha uložená", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Zálohovanie zlyhalo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            pendingRestore = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }
            }.getOrNull()
        }
    }

    TabScaffold(nav, "settings", "Nastavenia") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Cena")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    rate, { rate = it },
                    label = { Text("€ za km") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    fee, { fee = it },
                    label = { Text("Štartovné €") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Zmena platí pre nové jazdy. Uložené doklady si ponechajú pôvodnú cenu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Číslovanie dokladov")
            OutlinedTextField(
                nextNo, { nextNo = it.filter(Char::isDigit) },
                label = { Text("Číslo ďalšieho dokladu v roku $year") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Ak ste tento rok už vydali doklady na papieri, zadajte číslo, ktorým má aplikácia pokračovať. " +
                    "Od 1. januára sa číslovanie začne znova od 1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Hlavička prehľadu")
            OutlinedTextField(driver, { driver = it }, label = { Text("Meno vodiča") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(org, { org = it }, label = { Text("Organizácia") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                email, { email = it },
                label = { Text("Predvolený e-mail pre prehľady") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Google Mapy")
            OutlinedTextField(
                key, { key = it },
                label = { Text("API kľúč") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Skryť" else "Zobraziť") } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                region, { region = it },
                label = { Text("Krajina pre hľadanie adries (napr. sk)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val r = Fmt.parse(rate)
                    val f = Fmt.parse(fee)
                    if (r == null || f == null || r < 0 || f < 0) {
                        Toast.makeText(context, "Skontrolujte čísla v cene", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val n = nextNo.toIntOrNull()
                    if (n == null || n < 1) {
                        Toast.makeText(context, "Zadajte číslo ďalšieho dokladu", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (n <= maxUsed) {
                        Toast.makeText(
                            context,
                            "Doklad č. $maxUsed/$year už existuje. Ďalšie číslo musí byť aspoň ${maxUsed + 1}.",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@Button
                    }
                    app.settings.save(
                        AppSettings(
                            ratePerKm = r,
                            startFee = f,
                            driverName = driver.trim(),
                            organization = org.trim(),
                            reportEmail = email.trim(),
                            mapsApiKey = key.trim(),
                            regionCode = region.trim().lowercase(),
                            numberingYear = year,
                            numberingStart = n,
                        ),
                    )
                    Toast.makeText(context, "Nastavenia uložené", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Uložiť nastavenia") }

            SectionTitle("Záloha")
            Text(
                "Občas si uložte zálohu (napríklad na Google Disk), aby sa pri strate telefónu nič nestratilo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { saveBackup.launch("jazdy-zaloha-${LocalDate.now()}.json") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Uložiť zálohu") }
            OutlinedButton(
                onClick = { openBackup.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Obnoviť zo zálohy") }
            Spacer(Modifier.height(24.dp))
        }
    }

    val text = pendingRestore
    if (text != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Obnoviť zálohu?") },
            text = { Text("Všetci zákazníci, jazdy a nastavenia v tomto telefóne sa nahradia údajmi zo zálohy.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        try {
                            val restored = Backup.restore(app.db, text)
                            app.settings.save(restored)
                            loadFields(restored)
                            maxUsed = app.db.rides().maxNumber(year) ?: 0
                            Toast.makeText(context, "Záloha obnovená", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Obnovenie zlyhalo: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Obnoviť") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Zrušiť") } },
        )
    }
}
