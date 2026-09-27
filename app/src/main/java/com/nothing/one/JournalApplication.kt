package com.nothing.one

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The bundled Gemma engine is no longer warmed at process start. Preloading
 * loaded ~600 MB of weights the moment the app opened, which was the single
 * biggest source of heat on mid-range phones even when no AI feature was
 * used. The engine now loads lazily on the first real AI request (summary,
 * mood read, tags, assistant turn), and no longer runs while the user is
 * simply writing or dictating.
 */
@HiltAndroidApp
class NothingOneApp : Application()
