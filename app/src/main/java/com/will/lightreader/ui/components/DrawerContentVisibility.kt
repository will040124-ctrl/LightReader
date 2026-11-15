package com.will.lightreader.ui.components

/** Include an edge drag before it passes the native drawer's open-target threshold. */
internal fun drawerContentVisible(currentOpen: Boolean, targetOpen: Boolean, width: Int, offset: Float): Boolean =
    currentOpen || targetOpen || (width > 0 && offset.isFinite() && offset > -width + .5f)
