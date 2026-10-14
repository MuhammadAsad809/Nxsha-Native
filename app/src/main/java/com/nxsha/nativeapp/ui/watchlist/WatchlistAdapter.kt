package com.nxsha.nativeapp.ui.watchlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nxsha.nativeapp.R
import com.nxsha.nativeapp.data.local.WatchlistEntity
import com.nxsha.nativeapp.databinding.ItemMediaCardBinding

/**
 * RecyclerView adapter for watchlist items, backed by DiffUtil for efficient updates.
 * Reuses [ItemMediaCardBinding] — same card layout used on the home screen carousels.
 */
class WatchlistAdapter(
    private val onItemClick: (WatchlistEntity) -> Unit
) : ListAdapter<WatchlistEntity, WatchlistAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(
        private val binding: ItemMediaCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WatchlistEntity) {
            binding.tvTitle.text = item.title

            val posterUrl = when {
                item.posterUrl.isBlank() -> null
                item.posterUrl.startsWith("http") -> item.posterUrl
                else -> "https://image.tmdb.org/t/p/w342${item.posterUrl}"
            }

            Glide.with(binding.ivPoster)
                .load(posterUrl)
                .placeholder(R.color.surface_elevated)
                .error(R.color.surface_elevated)
                .centerCrop()
                .into(binding.ivPoster)

            // Badge: show media type
            binding.tvBadge.text = if (item.mediaType == "movie") "MOVIE" else "TV"
            binding.tvBadge.visibility = android.view.View.VISIBLE

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMediaCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<WatchlistEntity>() {
            override fun areItemsTheSame(a: WatchlistEntity, b: WatchlistEntity) =
                a.id == b.id

            override fun areContentsTheSame(a: WatchlistEntity, b: WatchlistEntity) = a == b
        }
    }
}
