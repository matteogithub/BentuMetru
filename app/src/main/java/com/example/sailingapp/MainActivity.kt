package com.example.sailingapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.example.sailingapp.ui.theme.*
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
import com.google.android.gms.maps.GoogleMapOptions
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.AssistChip

// --- PRIVACY: DataStore ---
private val Context.dataStore by preferencesDataStore(name = "settings")
private val PRIVACY_ACCEPTED_KEY = booleanPreferencesKey("privacy_accepted")
private const val PRIVACY_POLICY_URL = "https://open-meteo.com/en/privacy"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BentuMetruTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BentuMetruScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BentuMetruScreen(viewModel: SailingViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) } // Stato per il menu a tendina

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(Unit) {
        val accepted = context.dataStore.data.first()[PRIVACY_ACCEPTED_KEY] ?: false
        if (!accepted) showPrivacyDialog = true
    }

    if (showInfoDialog) InfoDialog(onDismiss = { showInfoDialog = false })

    if (showPrivacyDialog) {
        PrivacyDialog(
            onAccept = {
                coroutineScope.launch { context.dataStore.edit { it[PRIVACY_ACCEPTED_KEY] = true } }
                showPrivacyDialog = false
            },
            onOpenPolicy = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.any { it }) {
            viewModel.fetchGpsLocation()
        } else {
            coroutineScope.launch { snackbarHostState.showSnackbar("Permessi GPS negati.") }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) { // Rimosso il padding globale qui per far arrivare l'header ai bordi

            // --- HEADER STILE WHATSAPP ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // 1. RIGA IN ALTO: Titolo e Tre Puntini
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "BentuMetru",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF003366)
                        )
                        Text(
                            text = "Misura il vento, colora il mare",
                            fontSize = 12.sp,
                            color = Color(0xFF003366).copy(alpha = 0.7f)
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu opzioni"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Info") },
                                onClick = {
                                    showMenu = false
                                    showInfoDialog = true
                                }
                            )
                        }
                    }
                }

                // 2. BARRA DI RICERCA: Stile "Pillola"
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { // Aggiunto padding qui
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Cerca località...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                    )

                    // Tendina dei risultati scrollabile
                    if (uiState.hasSearched) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .padding(top = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            if (uiState.searchResults.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("Nessun risultato trovato", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    items(uiState.searchResults) { location ->
                                        Text(
                                            text = location.displayName,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.selectLocation(location) }
                                                .padding(16.dp)
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- BARRA DEI PREFERITI ---
                if (uiState.favorites.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.favorites) { favorite ->
                            AssistChip(
                                onClick = {
                                    viewModel.onSearchQueryChanged("")
                                    viewModel.selectLocation(favorite)
                                },
                                label = { Text(favorite.name) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 3. RIGA LOCALITÀ: Mostra dove siamo ora
                val currentLoc = uiState.selectedLocation
                val isFavorite = uiState.favorites.any { it.latitude == currentLoc.latitude && it.longitude == currentLoc.longitude }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentLoc.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    if (uiState.forecastList.isNotEmpty()) {
                        IconButton(onClick = { shareCurrentForecast(context, uiState.selectedLocation, uiState.forecastList.firstOrNull()) }) {
                            Icon(Icons.Default.Share, contentDescription = "Condividi", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Preferito",
                            tint = if (isFavorite) Color(0xFFFFC107) else Color.Gray
                        )
                    }
                }
            }


            Spacer(modifier = Modifier.height(16.dp))

            // ZONA DATI CON PULL TO REFRESH
            PullToRefreshBox(isRefreshing = uiState.isLoading, onRefresh = { viewModel.refreshData() }, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedButton(
                        onClick = {
                            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (hasFine || hasCoarse) viewModel.fetchGpsLocation()
                            else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("📍 Usa la mia posizione GPS") }

                    Spacer(modifier = Modifier.height(16.dp))


                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "💡 Trascina verso il basso per aggiornare", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(8.dp))

                    val groupedForecasts = uiState.forecastList.groupBy { it.date }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (uiState.forecastList.isNotEmpty()) {
                            item {
                                // Recupera i dati attuali (il primo slot orario disponibile)
                                val currentForecast = uiState.forecastList.first()

                                // Mostra la Mappa di Overview
                                WeatherMapOverview(
                                    latitude = uiState.selectedLocation.latitude,
                                    longitude = uiState.selectedLocation.longitude,
                                    windKnots = currentForecast.windSpeed,
                                    windDegrees = currentForecast.windDirDegrees,
                                    flagColor = getFlagColor(currentForecast.flagColor)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Sezioni Informative
                                LegendSection()
                                LimitationsSection()
                                DisclaimerSection()
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        groupedForecasts.forEach { (dateStr, items) ->
                            stickyHeader {
                                Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                    Text(text = dateStr, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            items(items) { item -> ForecastCard(item) }
                        }
                    }
                }
            }
        }
    }
}

// --- COMPONENTI UI RIMANENTI ---

fun shareCurrentForecast(context: Context, location: LocationItem, forecast: ForecastItem?) {
    if (forecast == null) return
    val flagEmoji = when (forecast.flagColor) { FlagColor.GREEN -> "🟢"; FlagColor.YELLOW -> "🟡"; FlagColor.ORANGE -> "🟠"; FlagColor.RED -> "🔴" }
    val conditionText = when (forecast.flagColor) { FlagColor.GREEN -> "Condizioni ottimali"; FlagColor.YELLOW -> "Condizioni discrete"; FlagColor.ORANGE -> "Condizioni mediocri"; FlagColor.RED -> "Condizioni sconsigliate" }
    val waveText = buildString { append("${forecast.wave} m"); forecast.wavePeriod?.let { append(" (T ${it.roundToInt()}s)") } }
    val shareText = "⛵ Previsioni Vela\n\n📍 ${location.displayName}\n📅 ${forecast.date}, ore ${forecast.time}\n💨 Vento: ${forecast.windSpeed} / ${forecast.windGust} kn ${forecast.windDir}\n🌊 Onda: $waveText\n🌡️ ${forecast.temperature}°C\n☔ Pioggia: ${forecast.rainProb}%\n\nValutazione: $flagEmoji $conditionText\n\n(Generato con BentuMetru)"
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    context.startActivity(Intent.createChooser(intent, "Condividi previsioni meteo").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
}

@Composable
fun getFlagColor(flag: FlagColor): Color {
    return when (flag) {
        FlagColor.GREEN -> MaterialTheme.colorScheme.flagGreen
        FlagColor.YELLOW -> MaterialTheme.colorScheme.flagYellow
        FlagColor.ORANGE -> MaterialTheme.colorScheme.flagOrange
        FlagColor.RED -> MaterialTheme.colorScheme.flagRed
    }
}

@Composable
fun ForecastCard(item: ForecastItem) {
    val bgColor = getFlagColor(item.flagColor)
    val contentColor = Color(0xFF333333)
    val waveText = buildString {
        append("${item.wave} m")
        item.wavePeriod?.let { append(" · T ${it.roundToInt()}s") }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor,
                    modifier = Modifier.weight(0.3f)
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Navigation,
                        contentDescription = "Vento da ${item.windDir}",
                        modifier = Modifier.size(40.dp).rotate(((item.windDirDegrees + 180) % 360).toFloat()),
                        tint = contentColor
                    )
                    Text(
                        text = item.windDir,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(0.7f)
                ) {
                    WeatherIconRow(Icons.Default.Thermostat, "${item.temperature} °C", contentColor)
                    WeatherIconRow(Icons.Default.Air, "${item.windSpeed} / ${item.windGust} kn", contentColor)
                    WeatherIconRow(Icons.Default.Waves, waveText, contentColor)
                    if (item.rainProb > 0) WeatherIconRow(Icons.Default.Umbrella, "${item.rainProb}%", contentColor)
                }
            }

            // ← NUOVO: Mostra il motivo del veto se presente
            if (item.flagColor == FlagColor.RED && item.vetoReason != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = contentColor.copy(alpha = 0.3f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Motivo veto",
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.vetoReason,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherIconRow(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = tint, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun LegendSection() {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Legenda di navigabilità a vela:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            LegendRow(getFlagColor(FlagColor.GREEN), "Ottimale")
            LegendRow(getFlagColor(FlagColor.YELLOW), "Discreta")
            LegendRow(getFlagColor(FlagColor.ORANGE), "Mediocre")
            LegendRow(getFlagColor(FlagColor.RED), "Sconsigliata")
            //Text("Vento: primo valore = media, secondo = raffica (nodi).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            //Text("T = periodo dell'onda in secondi. Alto = swell dolce; Basso = mare corto.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun LegendRow(color: Color, description: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(16.dp), shape = MaterialTheme.shapes.small, color = color, border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)) {}
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LimitationsSection() {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Dati e Limiti del Modello", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Previsioni elaborate da Open-Meteo con combinazione automatica multi-modello. \nL'algoritmo calcola la qualità della navigabilità a vela, NON valuta la sicurezza globale. Non tiene conto di: imbarcazione, esperienza equipaggio e tipologia uscita.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun DisclaimerSection() {
    Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
        Text("ATTENZIONE! Strumento di supporto che NON sostituisce i bollettini meteomare ufficiali. La responsabilità ricade sul comandante.", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
    }
}

@Composable
fun PrivacyDialog(onAccept: () -> Unit, onOpenPolicy: () -> Unit) {
    AlertDialog(onDismissRequest = { }, icon = { Icon(Icons.Default.Info, contentDescription = null) }, title = { Text("Privacy e posizione") },
        text = { Text("L'app usa il GPS solo per il meteo locale. I dati non sono salvati e sono processati anonimamente da Open-Meteo.") },
        confirmButton = { TextButton(onClick = onAccept) { Text("Ho capito") } },
        dismissButton = { TextButton(onClick = onOpenPolicy) { Text("Privacy Policy") } }
    )
}

@Composable
fun InfoDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Info, contentDescription = null) }, title = { Text("BentuMetru", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Ideazione e Modello", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
                Text("Architettura logica, design e formule matematiche per l'idoneità alla vela ideati e sviluppati da Matteo Fraschini.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                Text("Sviluppo Tecnico", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
                Text("Implementazione Jetpack Compose in Kotlin realizzata con il supporto dell'IA (Gemini), sotto revisione e validazione dell'autore.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                Text("Privacy e Posizione", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
                Text("L'app usa il GPS solo per il meteo locale. I dati non sono salvati e sono processati anonimamente da Open-Meteo.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Dati forniti in tempo reale tramite API Open-Meteo. Previsioni elaborate da Open-Meteo con combinazione automatica multi-modello.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}

@Composable
fun WeatherMapOverview(
    latitude: Double,
    longitude: Double,
    windKnots: Double,
    windDegrees: Int,
    flagColor: Color
) {
    val location = LatLng(latitude, longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(location, 12f)
    }

    LaunchedEffect(location) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(location, 12f)
    }

    // 1. Funzione di trasferimento: Normalizza il vento (es. max 30 nodi) in un coefficiente da 0.0 a 1.0
    val intensityRatio = (windKnots / 30.0).toFloat().coerceIn(0f, 1f)

    // 2. Mappa il coefficiente sull'opacità (da 40% a 100%) e sulla scala (da 0.8x a 1.5x)
    val arrowAlpha = 0.4f + (intensityRatio * 0.6f)
    val arrowScale = 0.8f + (intensityRatio * 0.7f)

    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp)),
        cameraPositionState = cameraPositionState,
        googleMapOptionsFactory = {
            GoogleMapOptions().liteMode(true)
        },
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            scrollGesturesEnabled = false
        )
    ) {
        // Overlay radiale che riflette il Sailing Score
        Circle(
            center = location,
            radius = 2500.0,
            fillColor = flagColor.copy(alpha = 0.35f),
            strokeColor = flagColor.copy(alpha = 0.8f),
            strokeWidth = 3f
        )

        // Vettore dinamico del vento
        MarkerComposable(
            state = MarkerState(position = location),
            anchor = Offset(0.5f, 0.5f)
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = "Direzione Vento",
                tint = Color.Black.copy(alpha = arrowAlpha),
                modifier = Modifier
                    .size(36.dp)
                    .scale(arrowScale)
                    .rotate(windDegrees.toFloat() + 180f)
            )
        }
    }
}