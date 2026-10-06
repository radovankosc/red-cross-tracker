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
        "This month" to (today.withDayOfMonth(1) to today),
        "Last month" to today.minusMonths(1).withDayOfMonth(1).let { it to it.plusMonths(1).minusDays(1) },
        "This year" to (today.withDayOfYear(1) to today),
        "Last year" to today.minusYears(1).withDayOfYear(1).let { it to it.plusYears(1).minusDays(1) },
    )

    TabScaffold(nav, "reports", "Reports") { padding ->
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
                DateField("From", from, { from = it }, Modifier.weight(1f))
                DateField("To", to, { to = it }, Modifier.weight(1f))
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${rows.size} receipt(s)", style = MaterialTheme.typography.titleMedium)
                    Text("Total distance: ${Fmt.km(round1(rows.sumOf { it.ride.totalKm }))}")
                    Text(
                        "Total: ${Fmt.money(round2(rows.sumOf { it.ride.price }))}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Send to e-mail") },
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
                            subject = "Ride report ${Fmt.date(from)} – ${Fmt.date(to)}",
                            text = "Ride report for ${Fmt.date(from)} – ${Fmt.date(to)}: ${snapshot.size} receipt(s), " +
                                "total ${Fmt.money(round2(snapshot.sumOf { it.ride.price }))}.",
                            email = email,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Email, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("E-mail PDF report")
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
                Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
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

    TabScaffold(nav, "settings", "Settings") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Price")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    rate, { rate = it },
                    label = { Text("€ per km") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    fee, { fee = it },
                    label = { Text("Starting fee €") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Changes apply to new rides. Saved receipts keep the price they were made with.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Printed on receipts")
            OutlinedTextField(driver, { driver = it }, label = { Text("Driver's name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(org, { org = it }, label = { Text("Organisation") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                email, { email = it },
                label = { Text("Default e-mail for reports") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Google Maps")
            OutlinedTextField(
                key, { key = it },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                region, { region = it },
                label = { Text("Country for address search (e.g. sk)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val r = Fmt.parse(rate)
                    val f = Fmt.parse(fee)
                    if (r == null || f == null || r < 0 || f < 0) {
                        Toast.makeText(context, "Check the price numbers", Toast.LENGTH_SHORT).show()
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
                        ),
                    )
                    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save settings") }

            SectionTitle("Backup")
            Text(
                "Save a backup file now and then (for example to Google Drive), so nothing is lost if the phone is.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { saveBackup.launch("ridelog-backup-${LocalDate.now()}.json") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save backup file") }
            OutlinedButton(
                onClick = { openBackup.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restore from backup file") }
            Spacer(Modifier.height(24.dp))
        }
    }

    val text = pendingRestore
    if (text != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = { Text("All customers, rides and settings on this phone are replaced by the ones in the backup file.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        try {
                            val restored = Backup.restore(app.db, text)
                            app.settings.save(restored)
                            loadFields(restored)
                            Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}
