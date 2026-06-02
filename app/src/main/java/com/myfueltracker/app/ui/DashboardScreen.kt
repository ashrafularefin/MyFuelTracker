package com.myfueltracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*

// --- SHARED SIZES DEFINED AT TOP LEVEL ---
val labelSize = 14.sp
val valueSize = 18.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FuelViewModel,
    navController: NavController,
    onOfflineClick: () -> Unit = {}
) {
    val vehicles by viewModel.allVehicles.collectAsState()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val latestFuel by viewModel.latestFuelEntry.collectAsState(initial = null)
    val latestService by viewModel.latestServiceLog.collectAsState(initial = null)
    val historyItems by viewModel.combinedHistory.collectAsState()
    val totalFuelingCost by viewModel.totalFuelingCost.collectAsState()
    val totalServiceCost by viewModel.totalServiceCost.collectAsState()
    val totalMileage by viewModel.totalMileage.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val distanceUnit by viewModel.distanceUnit.collectAsState()
    val fuelUnit by viewModel.fuelUnit.collectAsState()

    // Efficiency and Distance Traveled calculation
    val (lastEfficiency, lastDistance) = remember(historyItems, selectedVehicleId) {
        val fuelLogs = historyItems.filterIsInstance<HistoryItem.Fuel>()
            .filter { it.entry.vehicleId == selectedVehicleId }
        if (fuelLogs.size >= 2) {
            val current = fuelLogs[0].entry
            val previous = fuelLogs[1].entry
            val distanceTraveled = current.odometer - previous.odometer
            if (distanceTraveled > 0) {
                Pair(
                    String.format("%.1f", distanceTraveled / current.fuelAmount),
                    String.format("%.1f", distanceTraveled)
                )
            } else Pair<String?, String?>(null, null)
        } else Pair<String?, String?>(null, null)
    }

    val totalFuelVolume = remember(historyItems, selectedVehicleId) {
        historyItems.filterIsInstance<HistoryItem.Fuel>()
            .filter { it.entry.vehicleId == selectedVehicleId }
            .sumOf { it.entry.fuelAmount }
    }

    // --- Calculate Monthly Data for Stacked Chart ---
    val monthlyChartData = remember(historyItems, selectedVehicleId) {
        val grouped = mutableMapOf<String, Pair<Double, Double>>()
        val labelFormat = SimpleDateFormat("MMM''yy", Locale.getDefault())
        val sortFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

        historyItems.forEach { item ->
            when (item) {
                is HistoryItem.Fuel -> {
                    if (item.entry.vehicleId == selectedVehicleId) {
                        val dateMs = item.entry.dateTimestamp
                        val cost = item.entry.fuelAmount * (item.entry.pricePerUnit ?: 0.0)
                        val sortKey = sortFormat.format(Date(dateMs.toLong()))
                        val current = grouped[sortKey] ?: Pair(0.0, 0.0)
                        grouped[sortKey] = Pair(current.first + cost, current.second)
                    }
                }
                is HistoryItem.Service -> {
                    if (item.log.vehicleId == selectedVehicleId) {
                        val dateMs = item.log.date
                        val cost = item.log.cost
                        val sortKey = sortFormat.format(Date(dateMs.toLong()))
                        val current = grouped[sortKey] ?: Pair(0.0, 0.0)
                        grouped[sortKey] = Pair(current.first, current.second + cost)
                    }
                }
            }
        }

        grouped.entries
            .sortedBy { it.key }
            .map {
                val date = sortFormat.parse(it.key)
                val label = if (date != null) labelFormat.format(date) else it.key
                Triple(label, it.value.first, it.value.second)
            }
    }

    var expanded by remember { mutableStateOf(false) }
    val grandTotal = (totalFuelingCost ?: 0.0) + (totalServiceCost ?: 0.0)
    val safeMileage = totalMileage ?: 0.0
    val costPerUnit = if (safeMileage > 0.0) grandTotal / safeMileage else 0.0

    LaunchedEffect(vehicles) {
        if (selectedVehicleId == null && vehicles.isNotEmpty()) {
            viewModel.selectVehicle(vehicles.first().id)
        }
    }

    Scaffold(
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedVehicle?.name ?: "Select Vehicle",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Active Vehicle") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = if (selectedVehicle?.vehicleType == "Motorcycle")
                                Icons.Default.TwoWheeler else Icons.Default.DirectionsCar,
                            contentDescription = null
                        )
                    }
                )

                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    vehicles.forEach { vehicle ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(vehicle.name, fontWeight = FontWeight.Bold)
                                    Text(vehicle.registrationNumber, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                viewModel.selectVehicle(vehicle.id)
                                expanded = false
                            },
                            leadingIcon = { Icon(if (vehicle.vehicleType == "Motorcycle") Icons.Default.TwoWheeler else Icons.Default.DirectionsCar, null) }
                        )
                    }
                    if (vehicles.isNotEmpty()) HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Add New Vehicle", color = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            expanded = false
                            navController.navigate(Screen.AddVehicle.route)
                        },
                        leadingIcon = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }
            }

            if (vehicles.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DirectionsCar, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No vehicles found", style = MaterialTheme.typography.titleMedium)
                        Text("Please add a vehicle to start tracking.", color = Color.Gray)
                    }
                }
            } else {
                Text("Fuel Summary", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f).height(80.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.horizontalGradient(listOf(Color(0xFFE1BEE7), Color(0xFFF3E5F5))))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Lifetime Spend", fontSize = labelSize, color = Color(0xFF7B1FA2).copy(alpha = 0.7f))
                            Text("$currency${String.format("%.0f", grandTotal)}", fontSize = valueSize, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f).height(80.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.horizontalGradient(listOf(Color(0xFFEDE7F6), Color(0xFFD1C4E9))))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Cost / $distanceUnit", fontSize = labelSize, color = Color(0xFF512DA8).copy(alpha = 0.7f))
                            Text("$currency${String.format("%.2f", costPerUnit)}", fontSize = valueSize, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        modifier = Modifier.weight(1f).height(80.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.horizontalGradient(listOf(Color(0xFFFFE599), Color(0xFFFFF2CC))))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Total Fueling", fontSize = labelSize, color = Color.Red.copy(0.7f))
                            Text("$currency${String.format("%.2f", totalFuelingCost ?: 0.0)}", fontSize = valueSize, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f).height(80.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.horizontalGradient(listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9))))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Total Service", fontSize = labelSize, color = Color(0xFF2E7D32).copy(0.7f))
                            Text("$currency${String.format("%.2f", totalServiceCost ?: 0.0)}", fontSize = valueSize, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFEE8DD))))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Total Distance Traveled", style = MaterialTheme.typography.labelLarge, color = Color.Black.copy(0.6f))
                                    Text("$safeMileage $distanceUnit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                                Icon(Icons.Default.Timeline, null, modifier = Modifier.size(32.dp), tint = Color(0xFFE57373))
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                MetricDotItem(Color(0xFFFF9800), "Total Fueling:", "${String.format("%.1f", totalFuelVolume)} $fuelUnit")
                                MetricDotItem(Color(0xFF4CAF50), "Total Service Cost:", "$currency${String.format("%.0f", totalServiceCost ?: 0.0)}")
                            }
                        }
                    }
                }

                Text("Recent Activity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = (Color(0xff3d85c6)))

                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickViewCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "Last Fuel",
                        value = latestFuel?.let { "${it.fuelAmount} $fuelUnit" } ?: "No Records",
                        date = latestFuel?.dateTimestamp,
                        efficiency = lastEfficiency?.let { "$it $distanceUnit/$fuelUnit" },
                        distance = lastDistance?.let { "Driven: $it $distanceUnit" },
                        icon = Icons.Default.LocalGasStation,
                        gradientColors = listOf(Color(0xFFffc9d1), Color(0xFFFFF1F0)),
                        iconColor = Color.Red
                    )
                    QuickViewCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "Last Service",
                        value = latestService?.serviceType ?: "No Records",
                        date = latestService?.date,
                        odometer = latestService?.let { "Odo: ${it.odoReading} $distanceUnit" },
                        icon = Icons.Default.Handyman,
                        gradientColors = listOf(Color(0xFFF1F8E9), Color(0xFFb6d7a8)),
                        iconColor = Color(0xFF2E7D32)
                    )
                }

                MonthlyStackedBarChart(
                    data = monthlyChartData,
                    currency = currency
                )
            }
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
fun QuickViewCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    date: Long?,
    efficiency: String? = null,
    distance: String? = null,
    odometer: String? = null,
    location: String? = null,
    icon: ImageVector,
    gradientColors: List<Color>,
    iconColor: Color
) {
    val dateStr = date?.let { SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(it)) } ?: "---"

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(gradientColors))
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, modifier = Modifier.size(14.dp), tint = iconColor)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(title, style = MaterialTheme.typography.labelSmall, color = iconColor)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (efficiency != null) {
                            Text(text = "• $efficiency", style = MaterialTheme.typography.labelSmall, color = iconColor.copy(alpha = 0.7f), fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }

                    if (distance != null) {
                        Text(text = distance, style = MaterialTheme.typography.labelSmall, color = iconColor.copy(alpha = 0.8f), maxLines = 1)
                    }
                    if (odometer != null) {
                        Text(text = odometer, style = MaterialTheme.typography.labelSmall, color = iconColor.copy(alpha = 0.8f), maxLines = 1)
                    }
                    if (location != null) {
                        Text(text = location, style = MaterialTheme.typography.labelSmall, color = iconColor.copy(alpha = 0.8f), maxLines = 1)
                    }

                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun MonthlyStackedBarChart(
    data: List<Triple<String, Double, Double>>,
    currency: String
) {
    if (data.isEmpty()) return

    val maxTotal = data.maxOf { it.second + it.third }.toFloat().coerceAtLeast(1f)

    val fuelGradients = listOf(Color(0xFFFFD700), Color(0xFFF3CB51))
    val serviceGradients = listOf(Color(0xFF4B7DBC), Color(0xFF6495ED))

    val scrollState = rememberScrollState()

    LaunchedEffect(data.size, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Expense History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { (label, fuel, service) ->
                val fHeight = 150.dp * (fuel / maxTotal).toFloat()
                val sHeight = 150.dp * (service / maxTotal).toFloat()

                Column(
                    modifier = Modifier.fillMaxHeight().widthIn(min = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    val total = fuel + service
                    if (total > 0) {
                        Text(
                            text = String.format("%.0f", total),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Column(
                        modifier = Modifier.width(36.dp),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Service on Top
                        if (service > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(sHeight)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 6.dp,
                                            topEnd = 6.dp,
                                            bottomStart = if (fuel == 0.0) 6.dp else 0.dp,
                                            bottomEnd = if (fuel == 0.0) 6.dp else 0.dp
                                        )
                                    )
                                    .background(Brush.verticalGradient(serviceGradients)),
                                // --- Added content alignment to center text in bar ---
                                contentAlignment = Alignment.Center
                            ) {
                                // Only show text if the bar is tall enough to fit it cleanly
                                if (sHeight > 18.dp) {
                                    Text(
                                        text = String.format("%.0f", service),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        // Fuel on Bottom
                        if (fuel > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(fHeight)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = if (service == 0.0) 6.dp else 0.dp,
                                            topEnd = if (service == 0.0) 6.dp else 0.dp,
                                            bottomStart = 6.dp,
                                            bottomEnd = 6.dp
                                        )
                                    )
                                    .background(Brush.verticalGradient(fuelGradients)),
                                // --- Added content alignment to center text in bar ---
                                contentAlignment = Alignment.Center
                            ) {
                                // Only show text if the bar is tall enough to fit it cleanly
                                if (fHeight > 18.dp) {
                                    Text(
                                        text = String.format("%.0f", fuel),
                                        color = Color.Black.copy(alpha = 0.65f), // Darker text for yellow contrast
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            ChartLegendItem("Service", Color(0xFF4B7DBC))
            ChartLegendItem("Fueling", Color(0xFFFFD700))
        }
    }
}

@Composable
fun MetricDotItem(dotColor: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label ",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Black.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.Black.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun ChartLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
