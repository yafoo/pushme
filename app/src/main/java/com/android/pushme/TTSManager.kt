package com.android.pushme

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import java.util.*
import java.util.concurrent.Executors

class TTSManager private constructor() {
    private val tag = "TTSManager"
    private var tts: TextToSpeech? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var idleTimeoutRunnable: Runnable? = null
    private val idleTimeout = 60_000L // 1分钟

    @Volatile
    private var pendingText: String? = null // 保存最新的待播报文本

    companion object {
        @Volatile
        private var instance: TTSManager? = null

        fun getInstance(): TTSManager =
            instance ?: synchronized(this) {
                instance ?: TTSManager().also { instance = it }
            }
    }

    init {
        setupIdleTimeout()
    }

    /**
     * 直接播报文本，中断当前正在播报的内容
     */
    fun speak(context: Context, text: String) {
        executor.execute {
            pendingText = text // 覆盖之前的文本
            processSpeak(context.applicationContext)
        }
    }

    private fun processSpeak(context: Context) {
        idleTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }

        if (tts == null) {
            initializeTts(context)
        } else {
            speakCurrent()
        }
    }

    private fun initializeTts(context: Context) {
        tts = TextToSpeech(context) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    val locale = Locale.getDefault()
                    val result = tts?.setLanguage(locale)

                    when (result) {
                        TextToSpeech.LANG_MISSING_DATA -> {
                            Log.e(tag, "语言数据缺失：$locale")
                            release()
                        }

                        TextToSpeech.LANG_NOT_SUPPORTED -> {
                            Log.e(tag, "语言不支持：$locale")
                            release()
                        }

                        else -> {
                            Log.d(tag, "TTS 初始化成功，语言：$locale")
                            setupUtteranceListener()
                            speakCurrent()
                        }
                    }
                }
                TextToSpeech.ERROR -> {
                    val availableEngines = tts?.engines
                    Log.d(tag, "可用引擎: ${availableEngines?.map { it.name }}")
                    Log.d(tag, "默认引擎: ${tts?.defaultEngine}")

                    if (availableEngines.isNullOrEmpty()) {
                        Log.e(tag, "⚠️ 没有可用的TTS引擎！")
                        context.showToast("请安装TTS引擎（如 Google文字转语音、讯飞语记等）", Toast.LENGTH_LONG)
                    }

                    release()
                }
                else -> {
                    Log.e(tag, "TTS 初始化失败，状态码：$status")
                    release()
                }
            }
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    setupIdleTimeout() // 播报完成，启动空闲倒计时
                }
            }

            override fun onError(utteranceId: String?) {
                Log.e(tag, "TTS: 播报失败 $utteranceId")
                mainHandler.post {
                    setupIdleTimeout()
                }
            }
        })
    }

    private fun speakCurrent() {
        val text = pendingText ?: return
        val utteranceId = "tts_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_NOTIFICATION)
        }

        // 中断当前正在播放的内容，播放新的
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        Log.d(tag, "TTS: speak - $text")
    }

    private fun setupIdleTimeout() {
        idleTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }

        idleTimeoutRunnable = Runnable {
            if (pendingText == null || !isSpeaking()) {
                releaseTts() // 空闲时释放资源
            }
        }

        mainHandler.postDelayed(idleTimeoutRunnable!!, idleTimeout)
    }

    private fun releaseTts() {
        tts?.shutdown()
        tts = null
        Log.d(tag, "TTS 资源已释放")
    }

    /**
     * 释放所有资源，停止播报
     */
    fun release() {
        executor.execute {
            pendingText = null
            idleTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            idleTimeoutRunnable = null

            tts?.let { textToSpeech ->
                textToSpeech.stop()
                textToSpeech.shutdown()
            }
            tts = null
            Log.d(tag, "TTS 资源手工释放")
        }
    }

    /**
     * 是否正在播报
     */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    /**
     * 清除待播报内容（不会中断当前播放，但后续不再继续）
     */
    fun clearPending() {
        executor.execute {
            pendingText = null
        }
    }
}