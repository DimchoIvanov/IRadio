package com.flexy.iradio

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.util.EventLogger
import com.flexy.iradio.databinding.ActivityMainBinding
//////////////////////////////////////////
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodecList
import android.media.MediaCodecInfo
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory

//////////////////////////////////////////

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var player: ExoPlayer? = null
    private lateinit var extractorsFactory: ExtractorsFactory
    private var recentUrl: String? = null

    private val filePickerLauncher2 = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d("IRadioPicker", "callback entered resultCode=${result.resultCode} data=${result.data}")
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let {
                val grantFlags = result.data?.flags ?: 0
                val hasReadGrant = (grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
                val hasPersistableGrant =
                    (grantFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0

                if (hasReadGrant && hasPersistableGrant) {
                    try {
                        contentResolver.takePersistableUriPermission(
                            it,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: SecurityException) {
                        Log.e("IRadioPicker", "Failed to persist URI permission for $it", e)
                    }
                }

                try {
                    Log.d("IRadioPicker", "Picked uri=$it flags=0x${grantFlags.toString(16)}")

                    contentResolver.openInputStream(it)?.use { stream ->
                        stream.read()
                    } ?: throw IllegalStateException("openInputStream returned null for $it")

                    Log.d("IRadioPicker", "openInputStream OK for $it")

                    binding.etStreamUrl.setText(it.toString())
                    playMediaUri(it)
                } catch (e: Exception) {
                    Log.e("IRadioPicker", "Unable to open picked URI: $it", e)
                    Toast.makeText(this, "Cannot read picked file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.etStreamUrl.setText("http://stream-bg-01.radiotangra.com:8000/Tangra-high")
        // "https://play-radio0.jump.bg:7028"
        // binding.etStreamUrl.setText("https://play-radio0.jump.bg:7049")
        setupPlayer()
        setupListeners()
        ////
        logOutputAudioCapabilities(this)
        logMediaCodecCapabilities()
        logMediaCodecList()
    }

    @UnstableApi
    private fun setupPlayer() {
        extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true) // Example of a benefit/customization
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, extractorsFactory))
            .build().apply {
            addAnalyticsListener(EventLogger())
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val statusText = when (playbackState) {
                        Player.STATE_BUFFERING -> "Status: Buffering..."
                        Player.STATE_READY -> "Status: Playing"
                        Player.STATE_ENDED -> "Status: Ended"
                        Player.STATE_IDLE -> "Status: Idle"
                        else -> "Status: Unknown"
                    }
                    binding.tvStatus.text = statusText
                    binding.btnPlay.text = if (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY) "Pause" else "Play"
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    binding.btnPlay.text = if (isPlaying) "Pause" else "Play"
                }

                override fun onTracksChanged(tracks: Tracks) {
                    Log.d("IRadioPlayer", "Tracks Changed: ${tracks.groups.size} groups")
                    tracks.groups.forEachIndexed { index, group ->
                        val isSupported = group.isSupported
                        val format = group.getTrackFormat(0)
                        Log.d("IRadioPlayer", " Group $index: Supported=$isSupported, Format=$format")
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val cause = error.cause
                    val mediaItem = player?.currentMediaItem
                    val uri = mediaItem?.localConfiguration?.uri
                    val errorMessage = "Error: ${error.message} (Cause: ${cause?.message ?: "Unknown"})"
                    binding.tvStatus.text = errorMessage
                    android.util.Log.e("IRadioPlayer", "Playback Error for URI: $uri", error)
                    android.util.Log.e("IRadioPlayer", "Error Code: ${error.errorCodeName} (${error.errorCode})")
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    private fun setupListeners() {
        binding.btnPlay.setOnClickListener {
            val currentPlayer = player ?: return@setOnClickListener
            if (currentPlayer.isPlaying) {
                currentPlayer.pause()
            } else {
                val url = binding.etStreamUrl.text.toString()
                if (currentPlayer.playbackState == Player.STATE_IDLE || currentPlayer.playbackState == Player.STATE_ENDED
                    || url != recentUrl) {
                    if (url.isNotBlank()) {
                        recentUrl = url
                        playStream(url)
                    } else {
                        Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    currentPlayer.play()
                }
            }
        }

        binding.btnPickFile.setOnClickListener {
            launchDocumentsUiAudioPicker()
        }

        binding.btnPickFile2.setOnClickListener {
            Log.d("IRadioPicker", "btnPickFile2 clicked")
            launchDocumentsUiAudioPicker()
        }
    }

    private fun launchDocumentsUiAudioPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            setPackage("com.android.car.documentsui")
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                Uri.parse("content://com.android.externalstorage.documents/document/primary")
            )
        }

        try {
            filePickerLauncher2.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("IRadioPicker", "Car DocumentsUI picker is not available", e)
            Toast.makeText(this, "Car DocumentsUI not available", Toast.LENGTH_LONG).show()
        }
    }

    private val requestReadPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                playLuxFromMediaStore()
            } else {
                Toast.makeText(this, "Media read permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private fun ensureReadPermissionAndPlay() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            playLuxFromMediaStore()
        } else {
            requestReadPermissionLauncher.launch(permission)
        }
    }

    private fun playLuxFromMediaStore() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME}=?"
        val args = arrayOf("lux_514.wav")

        val base = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        contentResolver.query(base, projection, selection, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val uri = ContentUris.withAppendedId(base, id)
                binding.etStreamUrl.setText(uri.toString())
                playMediaUri(uri)
            } else {
                Toast.makeText(this, "File not found in MediaStore", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "MediaStore query failed", Toast.LENGTH_SHORT).show()
        }
    }
    private fun playStream(url: String) {
        val uri = Uri.parse(url)
        playMediaUri(uri)
    }

    private fun playMediaUri(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    private val tTAG = "AudioCaps"

    fun logOutputAudioCapabilities(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        outputs.forEach { device ->
            Log.d(tTAG, buildString {
                appendLine("Device: ${device.productName} / type=${device.type}")
                appendLine("  id=${device.id}")
                appendLine("  channelCounts=${device.channelCounts.describe()}")
                appendLine("  channelMasks=${if (device.channelMasks.isEmpty()) "<empty>" else device.channelMasks.joinToString { "0x${it.toString(16)}" }}")
                appendLine("  channelIndexMasks=${device.channelIndexMasks.joinToString { "0x${it.toString(16)}" }}")
                appendLine("  encodings=${device.encodings.joinToString { encodingToString(it) }}")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    appendLine("  audioProfiles=${device.audioProfiles.joinToString { profile ->
                        "format=${encodingToString(profile.format)}, rates=${profile.sampleRates.joinToString()}, " +
                                "channelMasks=${profile.channelMasks.joinToString { "0x${it.toString(16)}" }}"
                    }}")
                }
            })
        }
    }

    private fun logMediaCodecCapabilities() {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecInfos = codecList.codecInfos
        Log.d(tTAG, "--- Supported Audio Decoders ---")
        codecInfos.filter { !it.isEncoder && it.supportedTypes.any { type -> type.contains("audio", ignoreCase = true) } }
            .forEach { info ->
                Log.d(tTAG, "Codec: ${info.name} (Types: ${info.supportedTypes.joinToString()})")
            }
    }

    private fun logMediaCodecList() {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (codec in codecList.codecInfos) {
            val kind = if (codec.isEncoder) "encoder" else "decoder"
            Log.d("IRadioCodecs", "Codec: ${codec.name} ($kind)")

            for (type in codec.supportedTypes) {
                Log.d("IRadioCodecs", "  type=$type")

                if (!codec.isEncoder && (
                            type.equals("audio/ac3", ignoreCase = true) ||
                                    type.equals("audio/eac3", ignoreCase = true) ||
                                    type.equals("audio/eac3-joc", ignoreCase = true) ||
                                    type.equals("audio/ac4", ignoreCase = true)
                            )
                ) {
                    try {
                        val caps = codec.getCapabilitiesForType(type)
                        val audioCaps = caps.audioCapabilities
                        Log.d(
                            "IRadioCodecs",
                            "    channels=${audioCaps?.maxInputChannelCount} " +
                                    "sampleRates=${audioCaps?.supportedSampleRates?.joinToString()}"
                        )
                    } catch (e: Exception) {
                        Log.e("IRadioCodecs", "    failed to read capabilities for $type", e)
                    }
                }
            }
        }
    }

    private fun encodingToString(encoding: Int): String = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> "PCM_16BIT"
        AudioFormat.ENCODING_PCM_FLOAT -> "PCM_FLOAT"
        AudioFormat.ENCODING_AC3 -> "AC3"
        AudioFormat.ENCODING_E_AC3 -> "E_AC3"
        AudioFormat.ENCODING_AC4 -> "AC4"
        AudioFormat.ENCODING_DTS -> "DTS"
        AudioFormat.ENCODING_DTS_HD -> "DTS_HD"
        else -> "0x${encoding.toString(16)}"
    }

    private fun IntArray.describe(): String =
        if (isEmpty()) "<empty>" else joinToString()

    private fun <T> Array<T>.describe(): String =
        if (isEmpty()) "<empty>" else joinToString()

    fun describeOutMask(mask: Int): String = when (mask) {
        AudioFormat.CHANNEL_OUT_MONO -> "MONO"
        AudioFormat.CHANNEL_OUT_STEREO -> "STEREO"
        AudioFormat.CHANNEL_OUT_5POINT1 -> "5.1"
        AudioFormat.CHANNEL_OUT_7POINT1_SURROUND -> "7.1"
        else -> "0x${mask.toString(16)}"
    }
}
