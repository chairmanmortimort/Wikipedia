package com.mortimort.zork

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import kotlinx.coroutines.flow.StateFlow
import com.thelightphone.sdk.shared.LightServerData

/**
 * Lifecycle hook. Zork is fully local (the story is embedded), so there is
 * nothing to initialise or restore.
 */
@EntryPoint
object ZorkEntryPoint : LightEntryPoint {

    override suspend fun onToolCreate(serverData: StateFlow<LightServerData?>) {
        serverData.collect { /* ignore — offline tool */ }
    }

    override suspend fun onPushNotification(data: ByteArray) {
        // Deliberately silent.
    }
}
