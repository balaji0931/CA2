package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

// Data Models
data class Flight(val id: String, val airline: String, val price: Double)
data class Hotel(val id: String, val name: String, val pricePerNight: Double)
data class CarRental(val id: String, val company: String, val dailyRate: Double)

// UI State for Aggregated Booking Results
data class TravelSearchUiState(
    val isLoading: Boolean = false,
    val flights: List<Flight>? = null,
    val hotels: List<Hotel>? = null,
    val carRentals: List<CarRental>? = null,
    val flightError: String? = null,
    val hotelError: String? = null,
    val carRentalError: String? = null
)

// Simulated External Network APIs
suspend fun fetchFlights(): List<Flight> {
    delay(1000) // Simulate network latency
    return listOf(
        Flight("FL-101", "SkyHigh Airways", 299.99),
        Flight("FL-202", "AeroGlobal", 349.50)
    )
}

suspend fun fetchHotels(fail: Boolean = false): List<Hotel> {
    delay(1200) // Simulate network latency
    if (fail) {
        throw Exception("Hotel Service Unavailable (HTTP 503)")
    }
    return listOf(
        Hotel("HT-01", "Grand Plaza Hotel", 120.00),
        Hotel("HT-02", "Sunset Resort", 180.00)
    )
}

suspend fun fetchCarRentals(fail: Boolean = false): List<CarRental> {
    delay(800) // Simulate network latency
    if (fail) {
        throw Exception("Car Rental API Timeout (HTTP 504)")
    }
    return listOf(
        CarRental("CR-A1", "DriveNow", 45.00),
        CarRental("CR-B2", "SpeedyWheels", 55.00)
    )
}

/**
 * Concurrent Aggregator Function
 * Uses supervisorScope + async + runCatching for fault tolerance.
 * If one service fails, sibling child jobs continue execution and partial results are aggregated.
 */
suspend fun aggregateTripPackages(
    failHotel: Boolean = false,
    failCarRental: Boolean = false
): TravelSearchUiState = supervisorScope {
    // Concurrently trigger independent API calls using async
    val flightsDeferred = async {
        runCatching { fetchFlights() }
    }

    val hotelsDeferred = async {
        runCatching { fetchHotels(fail = failHotel) }
    }

    val carRentalsDeferred = async {
        runCatching { fetchCarRentals(fail = failCarRental) }
    }

    // Await all results independently
    val flightsResult = flightsDeferred.await()
    val hotelsResult = hotelsDeferred.await()
    val carRentalsResult = carRentalsDeferred.await()

    // Aggregate results into final UI state (partial results included)
    TravelSearchUiState(
        isLoading = false,
        flights = flightsResult.getOrNull(),
        hotels = hotelsResult.getOrNull(),
        carRentals = carRentalsResult.getOrNull(),
        flightError = flightsResult.exceptionOrNull()?.message,
        hotelError = hotelsResult.exceptionOrNull()?.message,
        carRentalError = carRentalsResult.exceptionOrNull()?.message
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TravelAggregatorScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun TravelAggregatorScreen(modifier: Modifier = Modifier) {
    var uiState by remember { mutableStateOf(TravelSearchUiState()) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Travel Package Aggregator",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Queries Flights, Hotels, and Car Rentals concurrently with fault tolerance.",
            fontSize = 14.sp,
            color = Color.Gray
        )

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    uiState = TravelSearchUiState(isLoading = true)
                    scope.launch {
                        uiState = aggregateTripPackages(failHotel = false, failCarRental = false)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isLoading
            ) {
                Text("Search All (Success)")
            }

            OutlinedButton(
                onClick = {
                    uiState = TravelSearchUiState(isLoading = true)
                    scope.launch {
                        uiState = aggregateTripPackages(failHotel = true, failCarRental = false)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
            ) {
                Text("Simulate Hotel Failure")
            }
        }

        if (uiState.isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.padding(start = 12.dp))
                Text("Fetching APIs concurrently...")
            }
        } else {
            // Display Aggregated Results or Errors per service

            // 1. Flights Section
            ServiceResultCard(
                title = "✈️ Flights",
                items = uiState.flights?.map { "${it.airline} (${it.id}) - \$${it.price}" },
                error = uiState.flightError
            )

            // 2. Hotels Section
            ServiceResultCard(
                title = "🏨 Hotels",
                items = uiState.hotels?.map { "${it.name} (${it.id}) - \$${it.pricePerNight}/night" },
                error = uiState.hotelError
            )

            // 3. Car Rentals Section
            ServiceResultCard(
                title = "🚗 Car Rentals",
                items = uiState.carRentals?.map { "${it.company} (${it.id}) - \$${it.dailyRate}/day" },
                error = uiState.carRentalError
            )
        }
    }
}

@Composable
fun ServiceResultCard(
    title: String,
    items: List<String>?,
    error: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                error != null -> Color(0xFFFFEBEE)
                items != null -> Color(0xFFE8F5E9)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                error != null -> {
                    Text(
                        text = "❌ Failed: $error",
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Medium
                    )
                }
                items != null -> {
                    items.forEach { item ->
                        Text(text = "• $item", fontSize = 14.sp)
                    }
                }
                else -> {
                    Text(text = "No search performed yet.", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }
    }
}
