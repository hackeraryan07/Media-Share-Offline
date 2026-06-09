package com.example.tv

import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.RowHeaderPresenter

class IconHeaderItemPresenter : RowHeaderPresenter() {
    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val context = parent.context
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()
            setPadding(0, padding, padding, padding)
        }
        val icon = ImageView(context).apply {
            id = android.R.id.icon
            val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
            }
        }
        val text = TextView(context).apply {
            id = android.R.id.title
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        layout.addView(icon)
        layout.addView(text)
        // required to ensure row selection highlighting works correctly
        layout.isFocusable = false
        layout.isFocusableInTouchMode = false
        return ViewHolder(layout)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val headerItem = (item as? androidx.leanback.widget.Row)?.headerItem
        if (headerItem == null) return
        val view = viewHolder.view
        val textView = view.findViewById<TextView>(android.R.id.title)
        val iconView = view.findViewById<ImageView>(android.R.id.icon)
        
        textView.text = headerItem.name
        
        if (headerItem.name?.startsWith("Playlist:") == true) {
            iconView.setImageResource(R.drawable.ic_playlist)
            textView.text = headerItem.name?.removePrefix("Playlist: ")
            iconView.setColorFilter(Color.parseColor("#FF4081")) // Pink for playlist
        } else {
            iconView.setImageResource(R.drawable.ic_folder)
            iconView.setColorFilter(Color.parseColor("#03A9F4")) // Blue for folder
        }
    }
}
