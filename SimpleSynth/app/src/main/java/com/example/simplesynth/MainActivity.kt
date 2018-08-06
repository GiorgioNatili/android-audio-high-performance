package com.example.simplesynth

import android.annotation.TargetApi
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.deviceInfoText
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mDeviceInfoText: TextView
    private var mWorkCyclesText: TextView? = null
    private var mAudioTrack: AudioTrack? = null
    private var mLoadThread: VariableLoadGenerator? = null
    private var mSettings: SharedPreferences? = null

    lateinit var nativeAudioWrapper: NativeAudioWrapper


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Lock to portrait to avoid onCreate being called more than once
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Load any previously saved values
        mSettings = getPreferences(Context.MODE_PRIVATE)
        workCycles = mSettings!!.getInt(PREFERENCES_KEY_WORK_CYCLES, workCycles)

        initDeviceInfoUI()
        initPerformanceConfigurationUI()

        setSustainedPerformanceMode()

        // Create a synthesizer whose callbacks are affined to the exclusive core(s) (if available)
        nativeAudioWrapper = NativeAudioWrapper()

        val audioConfig =  NativeAudioWrapperConfiguration(
                getSystemService(Context.AUDIO_SERVICE) as AudioManager)

        mDeviceInfoText.append("Exclusive core ids: ")
        for (i in audioConfig.exclusiveCores) {
                mDeviceInfoText.append(i.toString() + " ")
        }

        mDeviceInfoText.append("\n")

        mAudioTrack = createSynth(audioConfig)

        // Update the UI when there are underruns
        initUnderrunUpdater()

        setWorkCycles(workCycles)
    }

    override fun onStop() {
        super.onStop()
        val editor = mSettings!!.edit()
        editor.putInt(PREFERENCES_KEY_WORK_CYCLES, workCycles)
        editor.apply()
    }

    private fun initDeviceInfoUI() {

        mDeviceInfoText = deviceInfoText

        var deviceInfo = ""
        deviceInfo += "API " + Build.VERSION.SDK_INT + "\n"
        deviceInfo += "Build " + Build.ID + " " +
                Build.VERSION.CODENAME + " " +
                Build.VERSION.RELEASE + " " +
                Build.VERSION.INCREMENTAL + "\n"

        val pm = packageManager
        val claimsLowLatencyFeature = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
        deviceInfo += "Hardware flag audio.low_latency: $claimsLowLatencyFeature\n"

        var claimsProFeature = "Not supported. Only available on API " +
                Build.VERSION_CODES.M + "+"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            claimsProFeature = if (pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO))
                "true"
            else
                "false"
        }
        deviceInfo += "Hardware flag audio.pro: $claimsProFeature\n"

        mDeviceInfoText.append(deviceInfo)
    }

    private fun setSustainedPerformanceMode() {

        mDeviceInfoText.append("Sustained performance mode: ")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            window.setSustainedPerformanceMode(true)
            mDeviceInfoText.append("On")
        } else {
            mDeviceInfoText.append("Not supported. Only available on API " +
                    Build.VERSION_CODES.N + "+")
        }
        mDeviceInfoText.append("\n")

    }

    private fun createSynth(config: NativeAudioWrapperConfiguration): AudioTrack {

        nativeAudioWrapper.createEngine(Build.VERSION.SDK_INT)

        return nativeAudioWrapper.createAudioPlayer(config.frameRate,
                config.framesPerBuffer, NUM_BUFFERS, config.exclusiveCores)
    }

    private fun initPerformanceConfigurationUI() {

        val testToneSwitch = findViewById<View>(R.id.testToneSwitch) as Switch
        testToneSwitch.setOnCheckedChangeListener { compoundButton, b ->
            if (b) {
                nativeAudioWrapper.noteOn()
            } else {
                nativeAudioWrapper.noteOff()
            }
        }

        val variableLoadSwitch = findViewById<View>(R.id.variableLoadSwitch) as Switch
        variableLoadSwitch.setOnCheckedChangeListener { compoundButton, b ->
            if (b) {
                if (mLoadThread == null) {
                    mLoadThread = VariableLoadGenerator()
                }
                mLoadThread!!.start()
            } else {
                if (mLoadThread != null) {
                    mLoadThread!!.terminate()
                    mLoadThread = null
                }
            }
        }

        val stabilizedLoadSwitch = findViewById<View>(R.id.stabilizedLoadSwitch) as Switch
        stabilizedLoadSwitch.setOnCheckedChangeListener { compoundButton, b -> nativeAudioWrapper.setLoadStabilizationEnabled(b) }

        mWorkCyclesText = findViewById<View>(R.id.workCyclesText) as TextView?

        val workCyclesSeekBar = findViewById<View>(R.id.workCycles) as SeekBar
        workCyclesSeekBar.progress = (workCycles / WORK_CYCLES_PER_STEP).toInt()

        workCyclesSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                workCycles = (progress * WORK_CYCLES_PER_STEP).toInt()
                setWorkCycles(workCycles)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setWorkCycles(workCycles: Int) {

        nativeAudioWrapper.setWorkCycles(workCycles)

        runOnUiThread { mWorkCyclesText!!.text = "Work cycles $workCycles" }
    }

    private fun initUnderrunUpdater() {

        val mUnderrunCountText = findViewById<View>(R.id.underrunCountText) as TextView

        if (mAudioTrack == null) {
            mUnderrunCountText.text = "Underruns: Feature not supported on API <" +
                    Build.VERSION_CODES.N +
                    " see README for more"
        } else {

            val underrunUpdater = Timer()
            underrunUpdater.schedule(object : TimerTask() {
                @TargetApi(Build.VERSION_CODES.N)
                override fun run() {
                    val underrunCount: Int
                    underrunCount = (mAudioTrack as AudioTrack).underrunCount
                    runOnUiThread { mUnderrunCountText.text = "Underruns: $underrunCount" }
                }
            }, 0, UPDATE_UNDERRUNS_EVERY_MS.toLong())
        }
    }

    private inner class VariableLoadGenerator : Thread() {

        private var isRunning = false

        @Synchronized
        internal fun terminate() {
            isRunning = false
        }

        override fun run() {

            isRunning = true

            try {
                while (isRunning) {

                    val lowCycles = (MainActivity.workCycles * VARIABLE_LOAD_LOW_PERCENTAGE).toInt()
                    this@MainActivity.setWorkCycles(lowCycles)
                    Thread.sleep(VARIABLE_LOAD_LOW_DURATION.toLong())
                    this@MainActivity.setWorkCycles(MainActivity.workCycles)
                    Thread.sleep(VARIABLE_LOAD_HIGH_DURATION.toLong())
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

        }
    }

    companion object {

        private val NUM_BUFFERS = 2
        private val UPDATE_UNDERRUNS_EVERY_MS = 1000
        private val VARIABLE_LOAD_LOW_PERCENTAGE = 0.1f
        private val VARIABLE_LOAD_LOW_DURATION = 2000
        private val VARIABLE_LOAD_HIGH_DURATION = 2000
        val MAXIMUM_WORK_CYCLES = 500000
        private val SEEKBAR_STEPS = 100
        private val WORK_CYCLES_PER_STEP = (MAXIMUM_WORK_CYCLES / SEEKBAR_STEPS).toFloat()
        private val PREFERENCES_KEY_WORK_CYCLES = "work_cycles"

        private var workCycles = 0

        init {
            System.loadLibrary("SimpleSynth")
        }
    }


}

