package com.qtekfun.fexplo.ui.components

import androidx.annotation.StringRes
import com.qtekfun.fexplo.R
import com.qtekfun.fexplo.core.model.SortField

@StringRes
fun SortField.labelRes(): Int = when (this) {
    SortField.NAME -> R.string.sort_name
    SortField.SIZE -> R.string.sort_size
    SortField.DATE -> R.string.sort_date
}
