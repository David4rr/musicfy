// OfflineModeException.kt

package com.music.innertube

/**
 * What a request fails with while offline mode is on: it was never sent. An [java.io.IOException],
 * so callers that already treat a failed connection as "nothing to show" carry on doing so.
 */
class OfflineModeException : java.io.IOException("Offline mode is on")
