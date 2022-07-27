package dev.banderkat.podtitles.feeddetails

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentFeedDetailsBinding
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.utils.FetchFeed
import dev.banderkat.podtitles.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    override fun onResume() {
        super.onResume()
        if (viewModel.feedUpdated.value == false) {
            viewModel.setFeedUpdated(true)
            FetchFeed(requireContext(), viewLifecycleOwner, feed.url, feed.displayOrder) { itWorked ->
                Log.d(TAG, "Feed updated. Successful? $itWorked")
                // TODO: message user if update failed
            }
        }
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
                if (episode == null) return@OnClickListener
                val action =
                    FeedDetailsFragmentDirections.actionFeedDetailsFragmentToEpisodeFragment(
                        episode.guid,
                        feed
                    )
                exitTransition = null
                reenterTransition = null
                findNavController().navigate(action)
            }
        )

        val dividerDecoration = DividerItemDecoration(
            binding.feedDetailsEpisodeRv.context,
            LinearLayoutManager.VERTICAL
        )

        binding.feedDetailsEpisodeRv.addItemDecoration(dividerDecoration)
        binding.feedDetailsEpisodeRv.adapter = adapter

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                viewModel.getEpisodePages(feed.url).collectLatest {
                    adapter.submitData(lifecycle, it)
                }
            }
        }

        lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadStates ->
                if (loadStates.refresh is LoadState.Loading) {
                    binding.feedDetailsEpisodeListProgress.visibility = View.VISIBLE
                    binding.feedDetailsEpisodeRv.visibility = View.GONE
                } else {
                    binding.feedDetailsEpisodeListProgress.visibility = View.GONE
                    binding.feedDetailsEpisodeRv.visibility = View.VISIBLE
                }
            }
        }

        binding.feedCardDetailsExpandFab.setOnClickListener {
            val action = FeedDetailsFragmentDirections
                .actionFeedDetailsFragmentToFeedFullDetailsFragment(feed)
            findNavController().navigate(action)
        }
    }
}
