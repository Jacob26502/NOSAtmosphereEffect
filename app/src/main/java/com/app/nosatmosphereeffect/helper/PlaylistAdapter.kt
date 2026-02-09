package com.app.nosatmosphereeffect.helper

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.app.nosatmosphereeffect.R
import com.app.nosatmosphereeffect.activity.PlaylistEditorActivity
import java.util.concurrent.Executors

data class PlaylistItem(
    val originalUri: Uri,
    var isEdited: Boolean = false,
    var editedFilePath: String? = null,
    var matrixValues: FloatArray? = null // Store zoom state here
)

class PlaylistAdapter(
    private val context: Context,
    private val items: MutableList<PlaylistEditorActivity.PlaylistItem>,
    private val onItemClick: (Int) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    private val executor = Executors.newFixedThreadPool(4)
    private val handler = Handler(Looper.getMainLooper())

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgThumbnail: ImageView = view.findViewById(R.id.imgThumbnail)
        val btnDelete: FrameLayout = view.findViewById(R.id.btnDelete)
        val iconEdited: View = view.findViewById(R.id.iconEdited)
        val overlayEdited: View = view.findViewById(R.id.overlayEdited)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // Clear previous
        holder.imgThumbnail.setImageBitmap(null)

        // Load the EDITED file if it exists, otherwise original.
        // Because ImageView is centerCrop, it will look correct either way.
        val uriToLoad = if (item.isEdited && item.editedFilePath != null) {
            Uri.parse("file://${item.editedFilePath}")
        } else {
            item.originalUri
        }

        loadThumbnail(uriToLoad, holder.imgThumbnail)

        if (item.isEdited) {
            holder.iconEdited.visibility = View.VISIBLE
            holder.overlayEdited.visibility = View.VISIBLE
        } else {
            holder.iconEdited.visibility = View.GONE
            holder.overlayEdited.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onItemClick(position) }
        holder.btnDelete.setOnClickListener { onDeleteClick(position) }
    }

    private fun loadThumbnail(uri: Uri, imageView: ImageView) {
        executor.execute {
            try {
                // Decode bounds first
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                // Calculate sample size for thumbnail
                options.inSampleSize = calculateInSampleSize(options, 300, 400)
                options.inJustDecodeBounds = false

                val bmp = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                handler.post {
                    if (bmp != null) imageView.setImageBitmap(bmp)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override fun getItemCount() = items.size
}