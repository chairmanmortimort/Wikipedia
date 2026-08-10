package com.thelightphone.wikipedia

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import kotlinx.coroutines.flow.StateFlow

@EntryPoint
object WikipediaEntryPoint : LightEntryPoint {
    // No special initialization needed for Wikipedia tool
    override suspend fun onToolCreate(
        serverData: StateFlow<LightServerData?>,
    ) {
        // Wikipedia API is public — no credentials needed.
    }

    override suspend fun onPushNotification(
        data: ByteArray,
    ) {
        // No push notifications needed for Wikipedia tool
    }
}
