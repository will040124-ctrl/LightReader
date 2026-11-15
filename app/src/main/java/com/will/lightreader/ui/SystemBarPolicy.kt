package com.will.lightreader.ui

internal data class SystemBarPolicy(val hideNavigation: Boolean, val hideStatus: Boolean, val keepAwake: Boolean = false)

/** Retain insertion order when an outgoing reader updates during a navigation transition. */
internal class SystemBarOwners {
    private data class Request(val policy: SystemBarPolicy, val active: Boolean)
    private val requests = linkedMapOf<Any, Request>()

    fun update(owner: Any, policy: SystemBarPolicy, active: Boolean) {
        requests[owner] = Request(policy, active)
    }

    fun remove(owner: Any) { requests.remove(owner) }

    val current: SystemBarPolicy?
        get() = requests.values.lastOrNull { it.active }?.policy

    val isEmpty: Boolean get() = requests.isEmpty()
}
