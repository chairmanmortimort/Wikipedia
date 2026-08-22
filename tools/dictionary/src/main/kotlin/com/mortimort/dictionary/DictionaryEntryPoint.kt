package com.mortimort.dictionary

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import kotlinx.coroutines.flow.StateFlow

@EntryPoint
object DictionaryEntryPoint : LightEntryPoint {

    override suspend fun onToolCreate(serverData: StateFlow<LightServerData?>) {
        serverData.collect { data ->
            // no setup needed
        }
    }

    override suspend fun onPushNotification(data: ByteArray) {
        // no push notifications for a dictionary
    }
}
