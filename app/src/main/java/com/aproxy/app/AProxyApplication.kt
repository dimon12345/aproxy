package com.aproxy.app

import android.app.Application
import com.aproxy.app.service.ProxyService

class AProxyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProxyService.createNotificationChannel(this)
    }
}
