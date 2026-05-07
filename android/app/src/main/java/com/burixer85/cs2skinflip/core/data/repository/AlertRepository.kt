package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.local.AlertDao
import com.burixer85.cs2skinflip.core.data.local.AlertEntity
import com.burixer85.cs2skinflip.core.domain.model.Alert
import com.burixer85.cs2skinflip.core.domain.model.AlertType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepository @Inject constructor(
    private val dao: AlertDao
) {
    fun getAll(): Flow<List<Alert>> = dao.getAll().map { it.map { e -> e.toDomain() } }

    suspend fun countActive(): Int = dao.countActive()

    suspend fun add(alert: Alert) {
        dao.insert(alert.toEntity())
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    suspend fun setActive(alert: Alert, active: Boolean) {
        dao.update(alert.toEntity().copy(isActive = active))
    }

    private fun AlertEntity.toDomain() = Alert(
        id = id,
        skinId = skinId,
        skinName = skinName,
        skinImageUrl = skinImageUrl,
        type = AlertType.valueOf(type),
        targetPrice = targetPrice,
        currentPrice = currentPrice,
        isActive = isActive,
        isTriggered = isTriggered,
        createdAt = createdAt,
        triggeredAt = triggeredAt
    )

    private fun Alert.toEntity() = AlertEntity(
        id = id,
        skinId = skinId,
        skinName = skinName,
        skinImageUrl = skinImageUrl,
        type = type.name,
        targetPrice = targetPrice,
        currentPrice = currentPrice,
        isActive = isActive,
        isTriggered = isTriggered,
        createdAt = createdAt,
        triggeredAt = triggeredAt
    )
}
