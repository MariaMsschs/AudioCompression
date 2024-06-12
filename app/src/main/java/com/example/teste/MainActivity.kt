package com.example.teste
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import com.arthenica.mobileffmpeg.FFmpeg

class MainActivity : AppCompatActivity() {

    private val REQUEST_RECORD_AUDIO_PERMISSION = 200
    private var permissionToRecordAccepted = false
    private val permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private var audioRecord: AudioRecord? = null
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private var isRecording = false
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val recordings = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()

        listView = findViewById(R.id.listView)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, recordings)
        listView.adapter = adapter

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startRecording()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopRecording()
            convertPcmToWav()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val filePath = recordings[position].split(" | ")[0]
            playRecording(filePath)
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val filePath = recordings[position].split(" | ")[0]
            compressAudio(filePath)
            true
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        if (!permissionToRecordAccepted) finish()
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions()
            return
        }

        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)
        audioRecord?.startRecording()
        isRecording = true

        val audioData = ByteArray(bufferSize)
        val outputStream = FileOutputStream(getExternalFilesDir(null)?.absolutePath + "/recording.pcm")

        Thread {
            while (isRecording) {
                val read = audioRecord?.read(audioData, 0, audioData.size) ?: 0
                if (read > 0) {
                    outputStream.write(audioData, 0, read)
                }
            }
            outputStream.close()
        }.start()
    }

    private fun stopRecording() {
        if (isRecording) {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            isRecording = false
        }
    }

    private fun pcmToWav(pcmFile: File, wavFile: File) {
        val sampleRate = 44100
        val channels = 1
        val byteRate = 16 * sampleRate * channels / 8

        val pcmData = pcmFile.readBytes()
        val wavData = ByteArray(44 + pcmData.size)

        // RIFF header
        wavData[0] = 'R'.toByte()
        wavData[1] = 'I'.toByte()
        wavData[2] = 'F'.toByte()
        wavData[3] = 'F'.toByte()

        val chunkSize = 36 + pcmData.size
        wavData[4] = (chunkSize and 0xff).toByte()
        wavData[5] = (chunkSize shr 8 and 0xff).toByte()
        wavData[6] = (chunkSize shr 16 and 0xff).toByte()
        wavData[7] = (chunkSize shr 24 and 0xff).toByte()

        // WAVE header
        wavData[8] = 'W'.toByte()
        wavData[9] = 'A'.toByte()
        wavData[10] = 'V'.toByte()
        wavData[11] = 'E'.toByte()

        // fmt subchunk
        wavData[12] = 'f'.toByte()
        wavData[13] = 'm'.toByte()
        wavData[14] = 't'.toByte()
        wavData[15] = ' '.toByte()

        val subChunk1Size = 16
        wavData[16] = (subChunk1Size and 0xff).toByte()
        wavData[17] = (subChunk1Size shr 8 and 0xff).toByte()
        wavData[18] = (subChunk1Size shr 16 and 0xff).toByte()
        wavData[19] = (subChunk1Size shr 24 and 0xff).toByte()

        val audioFormat = 1
        wavData[20] = (audioFormat and 0xff).toByte()
        wavData[21] = (audioFormat shr 8 and 0xff).toByte()

        wavData[22] = (channels and 0xff).toByte()
        wavData[23] = (channels shr 8 and 0xff).toByte()

        wavData[24] = (sampleRate and 0xff).toByte()
        wavData[25] = (sampleRate shr 8 and 0xff).toByte()
        wavData[26] = (sampleRate shr 16 and 0xff).toByte()
        wavData[27] = (sampleRate shr 24 and 0xff).toByte()

        wavData[28] = (byteRate and 0xff).toByte()
        wavData[29] = (byteRate shr 8 and 0xff).toByte()
        wavData[30] = (byteRate shr 16 and 0xff).toByte()
        wavData[31] = (byteRate shr 24 and 0xff).toByte()

        val blockAlign = (channels * 16) / 8
        wavData[32] = (blockAlign and 0xff).toByte()
        wavData[33] = (blockAlign shr 8 and 0xff).toByte()

        val bitsPerSample = 16
        wavData[34] = (bitsPerSample and 0xff).toByte()
        wavData[35] = (bitsPerSample shr 8 and 0xff).toByte()

        // data subchunk
        wavData[36] = 'd'.toByte()
        wavData[37] = 'a'.toByte()
        wavData[38] = 't'.toByte()
        wavData[39] = 'a'.toByte()

        val subChunk2Size = pcmData.size
        wavData[40] = (subChunk2Size and 0xff).toByte()
        wavData[41] = (subChunk2Size shr 8 and 0xff).toByte()
        wavData[42] = (subChunk2Size shr 16 and 0xff).toByte()
        wavData[43] = (subChunk2Size shr 24 and 0xff).toByte()

        System.arraycopy(pcmData, 0, wavData, 44, pcmData.size)

        wavFile.writeBytes(wavData)
    }

    private fun convertPcmToWav() {
        val pcmFile = File(getExternalFilesDir(null)?.absolutePath + "/recording.pcm")
        val wavFile = File(getExternalFilesDir(null)?.absolutePath + "/recording_${System.currentTimeMillis()}.wav")
        pcmToWav(pcmFile, wavFile)

        val file = File(wavFile.absolutePath)
        val displayText = "$file | ${getFileSize(file)} KB"
        recordings.add(displayText)
        adapter.notifyDataSetChanged()
    }

    private fun getFileSize(file: File): String {
        val sizeInBytes = file.length()
        val sizeInKB = sizeInBytes / 1024
        val sizeInMB = sizeInKB / 1024
        return when {
            sizeInMB > 0 -> "${sizeInMB}MB"
            sizeInKB > 0 -> "${sizeInKB}KB"
            else -> "${sizeInBytes}B"
        }
    }

    //método de compressão
    private fun compressAudio(originalFilePath: String) {
        //recebem o local onde o aúdio deve ser armazenado e o nome dele
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val compressedFileName = "COMPRESSED_${System.currentTimeMillis()}.m4a"
        val compressedFile = File(storageDir, compressedFileName)

        //array com paramêtros da lib
        val command = arrayOf(
            "-i", originalFilePath,
            "-acodec", "aac",
            "-b:a", "64k",
            compressedFile.absolutePath
        )

        FFmpeg.executeAsync(command) { _, returnCode ->
            if (returnCode == 0) {
                runOnUiThread {
                    Toast.makeText(this, "Audio compressed successfully: ${compressedFile.name}", Toast.LENGTH_LONG).show()
                    val file = File(compressedFile.absolutePath)
                    val compressedFileTitle = "$file | ${getFileSize(file)}"
                    recordings.add(compressedFileTitle)
                    adapter.notifyDataSetChanged()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Failed to compress audio", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun playRecording(filePath: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(filePath)
                prepare()
                start()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}