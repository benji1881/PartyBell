package com.benji.partybell.ui

import android.content.Context
import android.text.format.DateFormat

internal fun clock(context: Context, time: Long): String = DateFormat.getTimeFormat(context).format(time)
