package com.qtekfun.ultimatefiles.domain.repository

import kotlinx.coroutines.flow.Flow

/** Emits whenever a storage volume may have appeared or disappeared (USB plugged, SD ejected…). */
interface VolumeChangeSource {
    val changes: Flow<Unit>
}
