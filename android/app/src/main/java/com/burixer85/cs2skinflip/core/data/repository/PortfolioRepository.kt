package com.burixer85.cs2skinflip.core.data.repository

import com.burixer85.cs2skinflip.core.data.mock.MockData
import com.burixer85.cs2skinflip.core.domain.model.Portfolio
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortfolioRepository @Inject constructor() {

    private var isSteamConnected = false

    fun isConnected(): Boolean = isSteamConnected

    fun connectSteam(): Flow<Portfolio> = flow {
        delay(1200)  // simulate OAuth + API call
        isSteamConnected = true
        emit(MockData.mockPortfolio)
    }

    fun getPortfolio(): Flow<Portfolio?> = flow {
        if (!isSteamConnected) { emit(null); return@flow }
        delay(600)
        emit(MockData.mockPortfolio)
    }

    fun disconnect() {
        isSteamConnected = false
    }
}
