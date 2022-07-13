package dev.banderkat.podtitles.feeddetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dev.banderkat.podtitles.databinding.FragmentFeedDetailsBinding

class FeedDetailsFragment: Fragment() {
    private var _binding: FragmentFeedDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

}