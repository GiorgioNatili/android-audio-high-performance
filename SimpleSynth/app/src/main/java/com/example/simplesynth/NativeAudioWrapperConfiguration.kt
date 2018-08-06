package com.example.simplesynth

import android.media.AudioManager
import android.os.Build
import java.lang.Exception

class NativeAudioWrapperConfiguration(private val audioManager: AudioManager) {

    val frameRate: Int
        get() = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE).toInt()

    val framesPerBuffer: Int
        get() = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER).toInt()

    // Obtain CPU cores which are reserved for the foreground app
    val exclusiveCores: IntArray
        get() {

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                throw Exception("Not supported. Only available on API ${Build.VERSION_CODES.N} +")
            } else {
                return android.os.Process.getExclusiveCores()
            }
        }
}
