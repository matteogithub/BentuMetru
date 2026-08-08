package com.example.sailingapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SailingUiState(
    val forecastList: List<ForecastItem> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<LocationItem> = emptyList(),
    val hasSearched: Boolean = false,
    val selectedLocation: LocationItem = LocationItem("Cagliari", "Sardegna", "Italia", 39.21, 9.11),
    val errorMessage: String? = null,
    val favorites: List<LocationItem> = emptyList(),
    val activeProfile: SailingProfile = SailingProfile.CROCIERA  // ← NUOVO
)

@OptIn(FlowPreview::class)
class SailingViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SailingUiState())
    val uiState: StateFlow<SailingUiState> = _uiState.asStateFlow()

    private val repository = SailingRepository(application)
    private val prefs = AppPreferences(application)  // ← NUOVO

    private val searchQueryFlow = MutableStateFlow("")

    init {
        _uiState.update { it.copy(favorites = repository.getFavorites()) }

        // ← NUOVO: Carica il profilo salvato
        viewModelScope.launch {
            val savedProfile = prefs.getActiveProfile()
            _uiState.update { it.copy(activeProfile = savedProfile) }
        }

        viewModelScope.launch {
            searchQueryFlow
                .debounce(500L)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isNotBlank()) {
                        executeSearch(query)
                    } else {
                        _uiState.update { it.copy(searchResults = emptyList(), hasSearched = false) }
                    }
                }
        }
        refreshData()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchQueryFlow.value = query
    }

    private suspend fun executeSearch(query: String) {
        _uiState.update { it.copy(hasSearched = true) }
        val results = repository.searchLocation(query)
        _uiState.update { it.copy(searchResults = results) }
    }

    fun selectLocation(location: LocationItem) {
        _uiState.update {
            it.copy(
                selectedLocation = location,
                searchResults = emptyList(),
                searchQuery = "",
                hasSearched = false
            )
        }
        searchQueryFlow.value = ""
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val loc = _uiState.value.selectedLocation
            val result = repository.fetchMeteoData(loc.latitude, loc.longitude)
            if (result != null) {
                // ← MODIFICATO: Ricalcola i flag col profilo attivo
                val profile = _uiState.value.activeProfile
                val recalculated = recalculateFlags(result, profile)
                _uiState.update { it.copy(forecastList = recalculated, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Errore di rete: impossibile recuperare i dati meteo.") }
            }
        }
    }

    fun fetchGpsLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val loc = repository.getCurrentLocation()
            if (loc != null) {
                _uiState.update { it.copy(selectedLocation = loc) }
                refreshData()
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Errore GPS: impossibile determinare la posizione.") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun toggleFavorite() {
        val currentState = _uiState.value
        val currentLocation = currentState.selectedLocation
        val currentFavorites = currentState.favorites.toMutableList()

        val existingIndex = currentFavorites.indexOfFirst {
            it.latitude == currentLocation.latitude && it.longitude == currentLocation.longitude
        }

        if (existingIndex != -1) {
            currentFavorites.removeAt(existingIndex)
        } else {
            currentFavorites.add(currentLocation)
        }

        repository.saveFavorites(currentFavorites)
        _uiState.update { it.copy(favorites = currentFavorites) }
    }

    // ← NUOVO: Cambia profilo e ricalcola istantaneamente
    fun changeProfile(newProfile: SailingProfile) {
        viewModelScope.launch {
            prefs.saveActiveProfile(newProfile)
            _uiState.update { it.copy(activeProfile = newProfile) }

            // Ricalcola i flag sui dati già scaricati (zero chiamate di rete!)
            val recalculated = recalculateFlags(_uiState.value.forecastList, newProfile)
            _uiState.update { it.copy(forecastList = recalculated) }
        }
    }

    // ← NUOVO: Funzione helper per ricalcolare i flag
    private fun recalculateFlags(forecasts: List<ForecastItem>, profile: SailingProfile): List<ForecastItem> {
        return forecasts.map { item ->
            val windDirDegrees = item.windDirDegrees
            val isThunderstorm = false  // Non abbiamo il weatherCode in ForecastItem, quindi assumiamo false
            val (newFlag, newVetoReason) = getSailingFlag(
                item.windSpeed,
                item.windGust,
                item.wave,
                item.wavePeriod,
                item.rainProb,
                isThunderstorm,
                profile.thresholds
            )
            item.copy(flagColor = newFlag, vetoReason = newVetoReason)
        }
    }
}