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
    val activeProfile: SailingProfile = SailingProfile.CROCIERA
)

@OptIn(FlowPreview::class)
class SailingViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SailingUiState())
    val uiState: StateFlow<SailingUiState> = _uiState.asStateFlow()

    private val repository = SailingRepository(application)
    private val prefs = AppPreferences(application)

    private val searchQueryFlow = MutableStateFlow("")

    init {
        _uiState.update { it.copy(favorites = repository.getFavorites()) }

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

        viewModelScope.launch {
            val savedProfile = prefs.getActiveProfile()
            _uiState.update { it.copy(activeProfile = savedProfile) }
            refreshData()
        }
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
                val profile = _uiState.value.activeProfile
                val recalculated = recalculateFlags(result, profile)
                val noMarineData = recalculated.isNotEmpty() && recalculated.all { it.wave == null }
                _uiState.update {
                    it.copy(
                        forecastList = recalculated,
                        isLoading = false,
                        errorMessage = if (noMarineData) {
                            "Nessuna previsione mare disponibile per questa località: potrebbe non essere sulla costa."
                        } else null
                    )
                }
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

    fun saveCurrentLocationAsFavorite(customName: String) {
        val current = _uiState.value.selectedLocation
        if (_uiState.value.favorites.any { it.isSameSpot(current) }) return
        val newFavorite = current.copy(name = customName.trim())
        val updated = _uiState.value.favorites + newFavorite
        repository.saveFavorites(updated)
        _uiState.update { it.copy(favorites = updated) }
    }

    fun removeCurrentLocationFromFavorites() {
        val current = _uiState.value.selectedLocation
        val updated = _uiState.value.favorites.filterNot { it.isSameSpot(current) }
        repository.saveFavorites(updated)
        _uiState.update { it.copy(favorites = updated) }
    }

    fun changeProfile(newProfile: SailingProfile) {
        viewModelScope.launch {
            prefs.saveActiveProfile(newProfile)
            _uiState.update { it.copy(activeProfile = newProfile) }

            val recalculated = recalculateFlags(_uiState.value.forecastList, newProfile)
            _uiState.update { it.copy(forecastList = recalculated) }
        }
    }

    private fun recalculateFlags(forecasts: List<ForecastItem>, profile: SailingProfile): List<ForecastItem> {
        return forecasts.map { item ->
            val wave = item.wave
            if (wave == null) {
                item.copy(flagColor = null, vetoReason = null)
            } else {
                val (newFlag, newVetoReason) = getSailingFlag(
                    item.windSpeed,
                    item.windGust,
                    wave,
                    item.wavePeriod,
                    item.rainProb,
                    item.isThunderstorm,
                    profile.thresholds
                )
                item.copy(flagColor = newFlag, vetoReason = newVetoReason)
            }
        }
    }
}
