package com.das.mobile.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class VisitDetailRoute(val id: String)

@Serializable
data class VisitFormRoute(val id: String? = null)

@Serializable
data class VisitPlanFormRoute(val id: String? = null)

@Serializable
data class SettlementDetailRoute(val id: String)

@Serializable
data class SettlementFormRoute(val id: String? = null)
