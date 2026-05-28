package com.example.tv

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException

class TvBrowseFragment : BrowseSupportFragment() {

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val client = OkHttpClient()
    private var serverIp: String = "127.0.0.1"

    companion object {
        fun newInstance(serverIp: String): TvBrowseFragment {
            val fragment = TvBrowseFragment()
            val args = Bundle().apply {
                putString("server_ip", serverIp)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        serverIp = arguments?.getString("server_ip") ?: "127.0.0.1"

        setupUIElements()
        loadRows()
        setupEventListeners()
    }

    private fun setupUIElements() {
        title = "Wi-Fi Video Streamer Client"
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        // Use custom styling matching TV slate palette colors
        brandColor = ContextCompat.getColor(requireContext(), R.color.fastlane_background)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.fastlane_background)

        setOnSearchClickedListener {
            val intent = Intent(requireActivity(), SearchActivity::class.java).apply {
                putExtra("server_ip", serverIp)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        fetchVideos()
    }

    private fun loadRows() {
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter
    }

    private fun fetchVideos() {
        val formattedIp = if (serverIp.contains(":") && !serverIp.startsWith("[")) "[$serverIp]" else serverIp
        val url = "http://$formattedIp:8999/videos"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    showErrorRow("Network failure: Could not reach streaming server ($serverIp:8999). Keep server active and retry.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val jsonArray = JSONArray(body)
                        val videosList = mutableListOf<TvVideo>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            videosList.add(
                                TvVideo(
                                    id = obj.getString("id"),
                                    title = obj.getString("title"),
                                    url = obj.getString("url"),
                                    duration = obj.getString("duration"),
                                    isLocal = obj.getBoolean("isLocal"),
                                    thumbnailUrl = obj.optString("thumbnailUrl", ""),
                                    folder = obj.optString("folder", "Uncategorized"),
                                    watchedPosition = obj.optLong("watchedPosition", 0L),
                                    totalDuration = obj.optLong("totalDuration", 0L)
                                )
                            )
                        }

                        activity?.runOnUiThread {
                            buildRows(videosList)
                        }
                    } catch (e: Exception) {
                        activity?.runOnUiThread {
                            showErrorRow("Payload error: Failed to parse share structure.")
                        }
                    }
                } else {
                    activity?.runOnUiThread {
                        showErrorRow("Server rejected query with code: ${response.code}")
                    }
                }
            }
        })
    }

    private fun showErrorRow(msg: String) {
        rowsAdapter.clear()
        val header = HeaderItem(0, "System Connection Alerts")
        val gridPresenter = CardPresenter()
        val listRowAdapter = ArrayObjectAdapter(gridPresenter)
        
        listRowAdapter.add(TvVideo("err", msg, "", "", false))
        rowsAdapter.add(ListRow(header, listRowAdapter))
    }

    private fun buildRows(videos: List<TvVideo>) {
        rowsAdapter.clear()

        val localVideos = videos.filter { it.isLocal }

        val cardPresenter = CardPresenter()

        if (localVideos.isNotEmpty()) {
            val allHeader = HeaderItem(1, "All Videos")
            val allRowAdapter = ArrayObjectAdapter(cardPresenter)
            for (video in localVideos) {
                allRowAdapter.add(video)
            }
            rowsAdapter.add(ListRow(allHeader, allRowAdapter))

            // Add other rows based on folder
            val groupedByFolder = localVideos.groupBy { it.folder ?: "Videos" }
            var headerId = 2L
            for ((folderName, folderVideos) in groupedByFolder) {
                val header = HeaderItem(headerId++, folderName)
                val rowAdapter = ArrayObjectAdapter(cardPresenter)
                for (video in folderVideos) {
                    rowAdapter.add(video)
                }
                rowsAdapter.add(ListRow(header, rowAdapter))
            }
        }
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is TvVideo) {
                if (item.id != "err" && item.url.isNotEmpty()) {
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra("video", item)
                    }
                    startActivity(intent)
                }
            }
        }
    }
}
