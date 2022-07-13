package dev.banderkat.podtitles.feeddetails

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentFeedDetailsBinding
import dev.banderkat.podtitles.feedlist.FeedListFragment
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.utils.Utils

class FeedDetailsFragment : Fragment() {
    companion object {
        const val TAG = "FeedDetailsFragment"
    }

    private val args: FeedDetailsFragmentArgs by navArgs()
    private lateinit var feed: PodFeed

    private var _binding: FragmentFeedDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FeedDetailsViewModel by lazy {
        ViewModelProvider(this)[FeedDetailsViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedDetailsBinding.inflate(inflater, container, false)
        feed = args.feed
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.feedDetailsCard.apply {
            feedCardAuthor.text = feed.author
            feedCardTitle.text = feed.title
            feedCardImage.contentDescription = feed.imageTitle.ifEmpty {
                requireContext().getString(R.string.default_logo_description)
            }
            Utils.loadLogo(feed.image, requireContext(), feedCardImage)
        }

        val adapter = EpisodesAdapter(
            EpisodesAdapter.OnClickListener { episode ->
                val action =
                    FeedDetailsFragmentDirections.actionFeedDetailsFragmentToEpisodeFragment(episode)
                findNavController().navigate(action)
            }
        )

        binding.feedDetailsEpisodeRv.adapter = adapter

        viewModel.getEpisodes(feed.url).observe(viewLifecycleOwner) { episodes ->
            Log.d(FeedListFragment.TAG, "Found ${episodes.size} episodes")
            adapter.submitList(episodes)
            adapter.notifyDataSetChanged()
        }
    }
}