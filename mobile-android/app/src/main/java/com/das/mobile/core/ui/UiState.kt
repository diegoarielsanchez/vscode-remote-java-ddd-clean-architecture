package com.das.mobile.core.ui

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

fun <T> Result<T>.toUiState(): UiState<T> =
    fold({ UiState.Success(it) }, { UiState.Error(it.message ?: "Unexpected error.") })
