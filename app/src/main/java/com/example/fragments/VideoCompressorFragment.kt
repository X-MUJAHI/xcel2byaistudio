package com.example.fragments

import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import java.io.File
import kotlin.concurrent.thread

data class VideoItem(val id: Long, val name: String, val path: String, val size: Long)

class VideoAdapter(
    private val videos: List<VideoItem>,
    private val onVideoClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_video_name)
        val tvFolder: TextView = view.findViewById(R.id.tv_video_folder)
        val tvSize: TextView = view.findViewById(R.id.tv_video_size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = videos[position]
        holder.tvName.text = video.name
        holder.tvFolder.text = video.path
        holder.tvSize.text = "Size: \${(video.size / (1024 * 1024))} MB"
        holder.itemView.setOnClickListener { onVideoClick(video) }
    }

    override fun getItemCount() = videos.size
}

class VideoCompressorFragment : Fragment() {

    private lateinit var rvVideos: RecyclerView
    private lateinit var llControls: LinearLayout
    private lateinit var tvSelectedVideo: TextView
    private lateinit var spinnerQuality: Spinner
    private lateinit var btnCompress: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    
    private var selectedVideo: VideoItem? = null
    private val qualities = arrayOf("High Quality (Slow)", "Medium Quality (Fast)", "Low Quality (Fastest)")
    private val bitrates = arrayOf("2000k", "1000k", "500k")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_video_compressor, container, false)
        
        rvVideos = view.findViewById(R.id.rv_videos)
        llControls = view.findViewById(R.id.ll_compression_controls)
        tvSelectedVideo = view.findViewById(R.id.tv_selected_video)
        spinnerQuality = view.findViewById(R.id.spinner_quality)
        btnCompress = view.findViewById(R.id.btn_compress)
        progressBar = view.findViewById(R.id.progress_bar)
        tvProgress = view.findViewById(R.id.tv_progress)
        
        rvVideos.layoutManager = LinearLayoutManager(context)
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, qualities)
        spinnerQuality.adapter = adapter
        
        loadVideos()
        
        btnCompress.setOnClickListener {
            selectedVideo?.let { compressVideo(it) }
        }
        
        return view
    }
    
    private fun loadVideos() {
        thread {
            val videoList = mutableListOf<VideoItem>()
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE
            )
            
            val sortOrder = "\${MediaStore.Video.Media.DATE_ADDED} DESC"
            
            requireContext().contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                
                while (cursor.moveToNext()) {
                    videoList.add(
                        VideoItem(
                            cursor.getLong(idCol),
                            cursor.getString(nameCol) ?: "Unknown",
                            cursor.getString(dataCol) ?: "",
                            cursor.getLong(sizeCol)
                        )
                    )
                }
            }
            
            requireActivity().runOnUiThread {
                rvVideos.adapter = VideoAdapter(videoList) { video ->
                    selectedVideo = video
                    llControls.visibility = View.VISIBLE
                    tvSelectedVideo.text = "Selected Video: \${video.name}"
                }
            }
        }
    }
    
    private fun compressVideo(video: VideoItem) {
        val outDir = File(requireContext().getExternalFilesDir(null), "CompressedVideos")
        if (!outDir.exists()) outDir.mkdirs()
        
        val outFile = File(outDir, "compressed_\${video.name}")
        val qualityIndex = spinnerQuality.selectedItemPosition
        
        // Strategy values based on selection
        // High Quality: approx 0.8 * original bitrate or just using 720p fallback
        // Medium: 480p fallback
        // Low: 360p fallback
        val videoStrategy = when (qualityIndex) {
            0 -> DefaultVideoStrategy.Builder().bitRate(2000000L).build()
            1 -> DefaultVideoStrategy.Builder().bitRate(1000000L).build()
            else -> DefaultVideoStrategy.Builder().bitRate(500000L).build()
        }
        
        btnCompress.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvProgress.visibility = View.VISIBLE
        tvProgress.text = "Starting compression..."
        progressBar.progress = 0
        progressBar.max = 100
        
        Transcoder.into(outFile.absolutePath)
            .addDataSource(video.path)
            .setVideoTrackStrategy(videoStrategy)
            .setAudioTrackStrategy(DefaultAudioStrategy.builder().channels(1).sampleRate(44100).build())
            .setListener(object : TranscoderListener {
                override fun onTranscodeProgress(progress: Double) {
                    requireActivity().runOnUiThread {
                        val pct = (progress * 100).toInt()
                        progressBar.progress = pct
                        tvProgress.text = "Compressing: \$pct%"
                    }
                }
                override fun onTranscodeCompleted(successCode: Int) {
                    requireActivity().runOnUiThread {
                        btnCompress.isEnabled = true
                        progressBar.visibility = View.GONE
                        tvProgress.text = "Success! Saved to:\n\${outFile.absolutePath}"
                        Toast.makeText(requireContext(), "Video Compressed Successfully!", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onTranscodeCanceled() {
                    requireActivity().runOnUiThread {
                        btnCompress.isEnabled = true
                        tvProgress.text = "Compression Canceled."
                    }
                }
                override fun onTranscodeFailed(exception: Throwable) {
                    requireActivity().runOnUiThread {
                        btnCompress.isEnabled = true
                        tvProgress.text = "Compression Failed: \${exception.message}"
                        Toast.makeText(requireContext(), "Failed to compress video", Toast.LENGTH_LONG).show()
                        exception.printStackTrace()
                    }
                }
            }).transcode()
    }
}
