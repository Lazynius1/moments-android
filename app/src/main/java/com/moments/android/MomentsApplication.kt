package com.moments.android

import android.app.Application
import com.moments.android.services.activity.TimeSpentManager

class MomentsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TimeSpentManager.initialize(this)
    }
}
