@file:OptIn(ExperimentalMaterial3Api::class)

package com.radovankosc.ridelog.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.radovankosc.ridelog.app
import com.radovankosc.ridelog.data.Customer
import com.radovankosc.ridelog.data.searchKey
import kotlinx.coroutines.launch

@Composable
fun CustomersScreen(nav: NavController) {
    val app = LocalContext.current.app
    val customers by remember { app.db.customers().all() }.collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }

    TabScaffold(
        nav, "customers", "Customers",
        fab = {
            FloatingActionButton(onClick = { nav.navigate("customer/0") }) {
                Icon(Icons.Filled.Add, "Add customer")
            }
        },
    ) { padding ->
        val all = customers ?: return@TabScaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search by name") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            val key = searchKey(query)
            val shown = if (key.isEmpty()) all else all.filter { searchKey(it.name).contains(key) }
            if (all.isEmpty()) {
                EmptyMessage("No customers yet. They're added automatically when you log a ride.", PaddingValues())
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { c ->
                    Card(onClick = { nav.navigate("customer/${c.id}") }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            val details = listOf(c.homeAddress, c.phone).filter { it.isNotBlank() }.joinToString(" · ")
                            if (details.isNotEmpty()) {
                                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerEditScreen(nav: NavController, id: Long) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val known by remember { app.db.rides().knownAddresses() }.collectAsState(initial = emptyList())

    var loaded by remember { mutableStateOf(id == 0L) }
    var original by remember { mutableStateOf<Customer?>(null) }
    var rideCount by remember { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var home by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id == 0L || loaded) return@LaunchedEffect
        app.db.customers().get(id)?.let { c ->
            original = c
            name = c.name
            phone = c.phone
            home = c.homeAddress
            destination = c.lastDestination
            note = c.note
        }
        rideCount = app.db.customers().rideCount(id)
        loaded = true
    }

    DetailScaffold(
        title = if (id == 0L) "New customer" else "Customer",
        onBack = { nav.popBackStack() },
        actions = {
            if (original != null && rideCount == 0) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete customer") }
            }
        },
    ) { padding ->
        if (!loaded) return@DetailScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                phone, { phone = it },
                label = { Text("Phone") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            AddressField("Home address", home, { home = it }, known)
            AddressField("Usual destination", destination, { destination = it }, known)
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            if (original != null) {
                Text(
                    "$rideCount ride(s) logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "Enter a name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        val c = (original ?: Customer(name = "")).copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            homeAddress = home.trim(),
                            lastDestination = destination.trim(),
                            note = note.trim(),
                        )
                        if (original == null) app.db.customers().insert(c) else app.db.customers().update(c)
                        nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save customer") }
        }
    }

    val toDelete = original
    if (confirmDelete && toDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${toDelete.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        app.db.customers().delete(toDelete)
                        nav.popBackStack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
