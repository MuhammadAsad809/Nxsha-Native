package com.nxsha.nativeapp.ui.watchlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.nxsha.nativeapp.data.local.WatchlistDao
import com.nxsha.nativeapp.data.local.WatchHistoryDatabase
import com.nxsha.nativeapp.data.local.WatchlistEntity
import com.nxsha.nativeapp.databinding.FragmentWatchlistBinding
import kotlinx.coroutines.launch

/**
 * Displays all titles the user saved to their watchlist (stored in Room DB).
 * No network call required — fully client-side.
 */
class WatchlistFragment : Fragment() {

    private var _binding: FragmentWatchlistBinding? = null
    private val binding get() = _binding!!

    private lateinit var dao: WatchlistDao
    private lateinit var adapter: WatchlistAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWatchlistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dao = WatchHistoryDatabase.getDatabase(requireContext()).watchlistDao()

        adapter = WatchlistAdapter { item ->
            // Pass data back to host Activity for detail sheet
            (activity as? WatchlistHost)?.openMediaDetails(
                entityId = item.id,
                mediaType = item.mediaType,
                title = item.title,
                posterUrl = item.posterUrl
            )
        }

        binding.rvWatchlist.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = this@WatchlistFragment.adapter
        }

        observeWatchlist()
    }

    private fun observeWatchlist() {
        lifecycleScope.launch {
            dao.observeAll().collect { list ->
                adapter.submitList(list)
                binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.rvWatchlist.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** Implemented by the host Activity to handle item taps */
    interface WatchlistHost {
        fun openMediaDetails(
            entityId: String,
            mediaType: String,
            title: String,
            posterUrl: String
        )
    }
}
