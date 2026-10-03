package com.qtekfun.ultimatefiles.data.system

import com.qtekfun.ultimatefiles.domain.repository.AccountRepository
import com.qtekfun.ultimatefiles.domain.repository.VolumeChangeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Volumes change when the system says so, or when the user adds or removes a network account. */
class CompositeVolumeChangeSource(
    system: VolumeChangeSource,
    accounts: AccountRepository,
) : VolumeChangeSource {
    override val changes: Flow<Unit> = merge(
        system.changes,
        // The first emission is the current list, not a change.
        accounts.accounts.distinctUntilChanged().drop(1).map { },
    )
}
