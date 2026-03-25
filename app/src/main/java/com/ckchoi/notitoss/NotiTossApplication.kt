package com.ckchoi.notitoss

import android.app.Application
import com.ckchoi.notitoss.data.AppContainer

class NotiTossApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
