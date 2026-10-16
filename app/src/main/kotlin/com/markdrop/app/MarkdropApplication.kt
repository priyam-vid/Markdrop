package com.markdrop.app

import android.app.Application
import com.markdrop.app.conversion.ConversionEngine
import com.markdrop.app.data.db.MarkdropDatabase
import com.markdrop.app.data.db.ConversionRepository
import com.markdrop.app.data.settings.SettingsRepository
import com.markdrop.app.data.settings.settingsDataStore

class MarkdropApplication : Application() {

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(settingsDataStore)
    }

    val database: MarkdropDatabase by lazy {
        MarkdropDatabase.getInstance(this)
    }

    val conversionRepository: ConversionRepository by lazy {
        ConversionRepository(database.conversionDao())
    }

    val conversionEngine: ConversionEngine by lazy {
        ConversionEngine(this)
    }

    val modelManager: com.markdrop.app.model.ModelManager by lazy {
        com.markdrop.app.model.ModelManager(this)
    }
}
