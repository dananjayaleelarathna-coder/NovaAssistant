package com.nova.assistant

import android.app.Application

class NovaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Global one-time init (crash reporting, logging, etc) goes here.
        // AI provider registration lives in ConversationViewModel.init so it stays
        // scoped to when it's actually needed.
    }
}
