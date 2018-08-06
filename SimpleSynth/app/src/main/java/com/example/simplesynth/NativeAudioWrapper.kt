package com.example.simplesynth

import android.media.AudioTrack

class NativeAudioWrapper {

    init {
        System.loadLibrary("SimpleSynth")
    }

    // Native methods
    external fun createEngine(apiLevel: Int)

    external fun createAudioPlayer(frameRate: Int,
                                          framesPerBuffer: Int,
                                          numBuffers: Int,
                                          exclusiveCores: IntArray): AudioTrack

    external fun noteOn()
    external fun noteOff()
    external fun setWorkCycles(workCycles: Int)
    external fun setLoadStabilizationEnabled(isEnabled: Boolean)

}
