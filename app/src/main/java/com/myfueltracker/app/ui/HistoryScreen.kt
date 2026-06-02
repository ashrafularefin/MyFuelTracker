package com.myfueltracker.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.myfueltracker.app.data.local.FuelEntry
import com.myfueltracker.app.data.local.ServiceLog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: FuelViewModel, navController: NavController) {
    val historyItems by viewModel.combinedHistory.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val distanceUnit by viewModel.distanceUnit.collectAsState()
    val fuelUnit by viewModel.fuelUnit.collectAsState()

    // Get the selected vehicle ID to enable the Add buttons
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsState()

    var itemToDelete by remember { mutableStateOf<HistoryItem?>(null) }

    // State to keep track of which item is selected for the detail view overlay
    var selectedHistoryItem by remember { mutableStateOf<HistoryItem?>(null) }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete this record? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDelete?.let { viewModel.deleteHistoryItem(it) }
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Detail Pop-Up Dialog Overlay
    selectedHistoryItem?.let { item ->
        HistoryDetailDialog(
            item = item,
            currency = currency,
            distanceUnit = distanceUnit,
            fuelUnit = fuelUnit,
            onDismiss = { selectedHistoryItem = null },
            onEditClick = {
                selectedHistoryItem = null
                when (item) {
                    is HistoryItem.Fuel -> {
                        viewModel.setSelectedFuelEntry(item.entry)
                        navController.navigate("${Screen.AddFuel.route}/${item.entry.vehicleId}")
                    }
                    is HistoryItem.Service -> {
                        viewModel.setSelectedServiceLog(item.log)
                        navController.navigate("${Screen.AddService.route}/${item.log.vehicleId}")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            selectedVehicleId?.let { vehicleId ->
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FloatingActionButton(
                        onClick = { navController.navigate("${Screen.AddService.route}/$vehicleId") },
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White,
                        shape = CircleShape
                    ) { Icon(Icons.Default.Handyman, "Add Service") }

                    FloatingActionButton(
                        onClick = { navController.navigate("${Screen.AddFuel.route}/$vehicleId") },
                        containerColor = Color.Red,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) { Icon(Icons.Default.LocalGasStation, "Add Fuel") }
                }
            }
        }
    ) { padding ->
        if (historyItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No records found", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp)
            ) {
                itemsIndexed(
                    items = historyItems,
                    key = { _, item ->
                        when(item) {
                            is HistoryItem.Fuel -> "fuel_${item.entry.id}"
                            is HistoryItem.Service -> "service_${item.log.id}"
                        }
                    }
                ) { index, item ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                itemToDelete = item
                                true
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val color by animateColorAsState(
                                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                                    MaterialTheme.colorScheme.errorContainer else Color.Transparent,
                                label = "delete_bg"
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(color).padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    ) {
                        when (item) {
                            is HistoryItem.Fuel -> {
                                val previousFuelEntry = historyItems
                                    .drop(index + 1)
                                    .filterIsInstance<HistoryItem.Fuel>()
                                    .firstOrNull { it.entry.vehicleId == item.entry.vehicleId }

                                val mileageInfo = if (previousFuelEntry != null) {
                                    val dist = item.entry.odometer - previousFuelEntry.entry.odometer
                                    val efficiency = dist / item.entry.fuelAmount
                                    String.format("%.2f %s/%s", efficiency, distanceUnit, fuelUnit)
                                } else {
                                    "Initial Fill"
                                }

                                HistoryItemCard(
                                    modifier = Modifier.clickable { selectedHistoryItem = item },
                                    title = "${item.entry.fuelAmount} $fuelUnit",
                                    subtitle = "Odo: ${item.entry.odometer} $distanceUnit • $mileageInfo",
                                    amount = "$currency${String.format("%.2f", item.entry.fuelAmount * item.entry.pricePerUnit)}",
                                    date = item.entry.dateTimestamp,
                                    icon = Icons.Default.LocalGasStation,
                                    iconColor = Color.Red,
                                    textColor = MaterialTheme.colorScheme.primary,
                                    onDeleteClick = { itemToDelete = item }
                                )
                            }
                            is HistoryItem.Service -> HistoryItemCard(
                                modifier = Modifier.clickable { selectedHistoryItem = item },
                                title = item.log.serviceType,
                                subtitle = "Odometer: ${item.log.odoReading} $distanceUnit",
                                amount = "$currency${String.format("%.2f", item.log.cost)}",
                                date = item.log.date,
                                icon = Icons.Default.Handyman,
                                iconColor = Color(0xFF2E7D32),
                                textColor = MaterialTheme.colorScheme.tertiary,
                                onDeleteClick = { itemToDelete = item }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    amount: String,
    date: Long,
    icon: ImageVector,
    iconColor: Color,
    textColor: Color,
    onDeleteClick: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(date))

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = iconColor.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(text = dateStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray.copy(alpha = 0.7f))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = amount, style = MaterialTheme.typography.titleMedium, color = textColor, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryDetailDialog(
    item: HistoryItem,
    currency: String,
    distanceUnit: String,
    fuelUnit: String,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {

                // --- Edit Icon Button on Top Right ---
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Log Entry",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val isFuel = item is HistoryItem.Fuel
                    Text(
                        text = if (isFuel) "Refueling Details" else "Service Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 44.dp) // Leave alignment room for top icon button
                    )

                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))

                    when (item) {
                        is HistoryItem.Fuel -> {
                            val entry = item.entry
                            val dateFormatted = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(entry.dateTimestamp))

                            DetailItemRow(label = "Date", value = dateFormatted)

                            // --- Fuel Station field ---
                            // Note: Adjust property name to match your specific FuelEntry schema entity if different (e.g. entry.stationName)
                            if (!entry.stationName.isNullOrBlank()) {
                                DetailItemRow(label = "Fuel Station", value = entry.stationName)
                            }

                            DetailItemRow(label = "Odometer Reading", value = "${entry.odometer} $distanceUnit")
                            DetailItemRow(label = "Amount Pumped", value = "${entry.fuelAmount} $fuelUnit")
                            entry.pricePerUnit?.let {
                                DetailItemRow(label = "Price per Unit", value = "$currency${String.format("%.2f", it)}")
                            }
                            DetailItemRow(label = "Total Expense", value = "$currency${String.format("%.2f", entry.fuelAmount * entry.pricePerUnit)}")
                            if (entry.notes.isNotEmpty()) {
                                DetailItemRow(label = "Notes Logged", value = entry.notes)
                            }
                        }
                        is HistoryItem.Service -> {
                            val log = item.log
                            val dateFormatted = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(log.date))

                            DetailItemRow(label = "Date", value = dateFormatted)

                            if (!log.serviceCenter.isNullOrBlank()) {
                                DetailItemRow(label = "Service Center", value = log.serviceCenter)
                            }
                            DetailItemRow(label = "Service Task Type", value = log.serviceType)
                            DetailItemRow(label = "Odometer Reading", value = "${log.odoReading} $distanceUnit")
                            DetailItemRow(label = "Total Maintenance Cost", value = "$currency${String.format("%.2f", log.cost)}")
                            if (!log.notes.isNullOrEmpty()) {
                                DetailItemRow(label = "Notes Logged", value = log.notes)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItemRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
    }
}
