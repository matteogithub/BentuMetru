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
    val favorites: List<LocationItem> = emptyList()
)

@OptIn(FlowPreview::class) // Necessario per usare l'operatore debounce
class SailingViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SailingUiState())
    val uiState: StateFlow<SailingUiState> = _uiState.asStateFlow()

    private val repository = SailingRepository(application)

    private val searchQueryFlow = MutableStateFlow("")

    init {
        _uiState.update { it.copy(favorites = repository.getFavorites()) }
        viewModelScope.launch {
            searchQueryFlow
                .debounce(500L) // Attende 500ms dall'ultima digitazione
                .distinctUntilChanged() // Evita di rifare la ricerca se il testo è identico al precedente
                .collect { query ->
                    if (query.isNotBlank()) {
                        executeSearch(query)
                    } else {
                        // Se il testo è vuoto, puliamo i risultati
                        _uiState.update { it.copy(searchResults = emptyList(), hasSearched = false) }
                    }
                }
        }
        refreshData()
    }

    fun onSearchQueryChanged(query: String) {
        // 1. Aggiorniamo immediatamente lo stato della UI affinché la tastiera non lagghi
        _uiState.update { it.copy(searchQuery = query) }
        // 2. Passiamo il nuovo valore al Flow che gestisce il debounce
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
                _uiState.update { it.copy(forecastList = result, isLoading = false) }
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
}

