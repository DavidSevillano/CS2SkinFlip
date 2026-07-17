package com.burixer85.cs2skinflip.core.data.remote

import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SteamApiServiceTest {

    /**
     * A malformed method annotation (e.g. a bad "Name: value" pair in @Headers)
     * only surfaces when Retrofit parses the method on its first call — which in
     * SkinRepository.getSteamPrice happens inside runCatching, so it would be
     * swallowed and render as a permanent "not listed" with no error anywhere.
     * validateEagerly parses every method at creation time, failing this test
     * instead.
     */
    @Test
    fun `all service method annotations are well-formed`() {
        Retrofit.Builder()
            .baseUrl("https://api.steampowered.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .validateEagerly(true)
            .build()
            .create(SteamApiService::class.java)
    }
}
