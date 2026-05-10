package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.remote.AlertDto
import com.burixer85.cs2skinflip.core.data.remote.CS2BackendApiService
import com.burixer85.cs2skinflip.core.data.remote.CreateAlertRequest
import com.burixer85.cs2skinflip.core.data.remote.UpdateAlertRequest
import com.burixer85.cs2skinflip.core.domain.model.Alert
import com.burixer85.cs2skinflip.core.domain.model.AlertType
import javax.inject.Inject
import javax.inject.Singleton

data class AlertsState(
    val alerts: List<Alert>,
    val isPremium: Boolean,
    val activeCount: Int,
    val freeLimit: Int?,  // null = unlimited (premium); Int = free tier limit
)

@Singleton
class AlertRepository @Inject constructor(
    private val backendApi: CS2BackendApiService,
) {
    suspend fun fetchAll(): AlertsState {
        val response = backendApi.getAlerts()
        return AlertsState(
            alerts = response.alerts.map { it.toDomain() },
            isPremium = response.meta.isPremium,
            activeCount = response.meta.activeCount,
            freeLimit = response.meta.limit,
        )
    }

    suspend fun create(skinId: String, type: AlertType, targetPrice: Double): Alert =
        backendApi.createAlert(CreateAlertRequest(skinId, type.name, targetPrice)).toDomain()

    suspend fun setActive(id: String, isActive: Boolean): Alert =
        backendApi.updateAlert(id, UpdateAlertRequest(isActive = isActive)).toDomain()

    suspend fun update(id: String, type: AlertType? = null, targetPrice: Double? = null): Alert =
        backendApi.updateAlert(id, UpdateAlertRequest(
            targetPrice = targetPrice,
            type = type?.name,
        )).toDomain()

    suspend fun delete(id: String) {
        backendApi.deleteAlert(id)
    }
}

private fun AlertDto.toDomain(): Alert = Alert(
    id = id,
    skinId = skinId,
    skinName = skinName ?: skinId,
    skinImageUrl = skinImageUrl ?: "",
    type = AlertType.valueOf(type),
    targetPrice = targetPrice,
    currentPrice = currentPrice ?: 0.0,
    isActive = isActive,
    isTriggered = isTriggered,
    createdAt = 0L,
    triggeredAt = null,
)
