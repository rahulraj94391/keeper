package io.github.rahulraj94391.keeper

import android.app.Application
import io.github.rahulraj94391.keeper.di.AppContainer

class KeeperApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
