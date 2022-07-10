package dev.banderkat.podtitles.feedlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dev.banderkat.podtitles.databinding.FragmentFeedListBinding

class FeedListFragment: Fragment() {

    private var _binding: FragmentFeedListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedListBinding.inflate(inflater, container, false)
        return binding.root
    }
}