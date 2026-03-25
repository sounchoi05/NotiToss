package com.ckchoi.notitoss.data

import android.app.Application
import com.ckchoi.notitoss.service.ForwardDispatcher

class AppContainer(application: Application) {
    private val database = AppDatabase.getInstance(application)
    private val settingsStore = AppSettingsStore(application)
    val forwardDispatcher = ForwardDispatcher(
        context = application,
        settingsStore = settingsStore,
    )

    val repository = NotiTossRepository(
        application = application,
        database = database,
        dispatcher = forwardDispatcher,
        settingsStore = settingsStore,
    )
}
