package com.qtekfun.ultimatefiles.ui.components

import androidx.annotation.StringRes
import com.qtekfun.ultimatefiles.R
import com.qtekfun.ultimatefiles.core.model.SortField

@StringRes
fun SortField.labelRes(): Int = when (this) {
    SortField.NAME -> R.string.sort_name
    SortField.SIZE -> R.string.sort_size
    SortField.DATE -> R.string.sort_date
}
