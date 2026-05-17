package com.zbxapp.di

import android.content.Context
import com.zbxapp.data.api.ZabbixClient
import com.zbxapp.data.repository.ZabbixRepository
import com.zbxapp.data.storage.SecureStorage

class AppContainer(appContext: Context) {
    val storage: SecureStorage = SecureStorage(appContext)
    val client: ZabbixClient = ZabbixClient()
    val repository: ZabbixRepository = ZabbixRepository(client, storage)
}
