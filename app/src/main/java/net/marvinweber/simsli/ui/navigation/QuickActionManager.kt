package net.marvinweber.simsli.ui.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class QuickAction {
    ADD_ITEM
}

/**
 * Coordinates app shortcuts and external quick actions (e.g. launcher shortcut to add item).
 * Uses a buffered replay cache of 1 so actions triggered before UI subscribers attach are preserved.
 */
@Singleton
class QuickActionManager @Inject constructor() {
    private val _actions = MutableSharedFlow<QuickAction>(replay = 1, extraBufferCapacity = 1)
    val actions: SharedFlow<QuickAction> = _actions.asSharedFlow()

    fun trigger(action: QuickAction) {
        _actions.tryEmit(action)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consume() {
        _actions.resetReplayCache()
    }
}
