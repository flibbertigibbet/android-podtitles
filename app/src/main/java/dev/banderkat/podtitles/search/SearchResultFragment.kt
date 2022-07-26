package dev.banderkat.podtitles.search

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentSearchResultBinding
import dev.banderkat.podtitles.models.GpodderSearchResult
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.utils.AddFeed
import dev.banderkat.podtitles.utils.Utils

class SearchResultFragment : Fragment() {
    companion object {
        const val TAG = "SearchResultFragment"
    }

    private var _binding: FragmentSearchResultBinding? = null
    private val binding get() = _binding!!
    private val args: SearchResultFragmentArgs by navArgs()
    private lateinit var searchResult: GpodderSearchResult
    private val viewModel: SearchResultViewModel by lazy {
        ViewModelProvider(this)[SearchResultViewModel::class.java]
    }
    private var podFeed: PodFeed? = null
    private var feedAdded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchResultBinding.inflate(inflater, container, false)

        searchResult = args.searchResult
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val httpsFeedUri = Utils.convertToHttps(searchResult.url)

        binding.apply {
            searchResultCardTitle.text = searchResult.title
            searchResultCardAuthor.text = searchResult.author
            searchResultCardLink.text = searchResult.website
            searchResultCardDescription.text = searchResult.description
            searchResultImage.contentDescription = getString(R.string.default_logo_description)

            val logoUrl = searchResult.logoUrl
            if (!logoUrl.isNullOrEmpty()) Utils.loadLogo(
                Utils.convertToHttps(logoUrl), requireContext(), searchResultImage
            )

            searchResultCardAddFeedFab.setOnClickListener {
                if (podFeed != null) viewModel.removeFeed(podFeed!!) else addFeed(httpsFeedUri)
            }
        }

        viewModel.getFeed(httpsFeedUri).observe(viewLifecycleOwner) { feed ->
            Log.d(TAG, "Existing feed for this URL is $feed")
            podFeed = feed

            // go to feed details after adding new feed
            if (feedAdded && podFeed != null) {
                val action = SearchResultFragmentDirections
                    .actionSearchResultFragmentToFeedDetailsFragment(podFeed!!)
                findNavController().navigate(action)
            }

            changeFab(feed != null)
        }
    }

    private fun changeFab(alreadySubscribed: Boolean) {
        if (alreadySubscribed) {
            // change button to remove feed instead of add it
            // TODO: also change color?
            binding.searchResultCardAddFeedFab.apply {
                setImageDrawable(
                    resources.getDrawable(
                        com.google.android.material.R.drawable.ic_m3_chip_close,
                        requireContext().theme
                    )
                )
                contentDescription = getString(R.string.remove_feed)
            }
        } else {
            // add feed button
            binding.searchResultCardAddFeedFab.apply {
                setImageDrawable(
                    resources.getDrawable(
                        android.R.drawable.ic_input_add,
                        requireContext().theme
                    )
                )
                contentDescription = getString(R.string.fab_add_feed)
            }
        }
    }

    private fun addFeed(httpsFeedUri: String) {
        Log.d(TAG, "clicked to add feed $httpsFeedUri")
        binding.apply {
            searchResultCardFabProgress.visibility = View.VISIBLE
            searchResultCardAddFeedFab.isEnabled = false
            AddFeed(requireContext(), viewLifecycleOwner, httpsFeedUri) { itWorked ->
                searchResultCardFabProgress.visibility = View.INVISIBLE

                val snackText: String
                if (itWorked) {
                    Log.d(TAG, "Feed added successfully! Go to feed details")
                    snackText = getString(R.string.feed_added_success)
                    feedAdded = true
                } else {
                    Log.d(TAG, "Feed could not be added. Show an error")
                    snackText = getString(R.string.feed_added_failure)
                    searchResultCardAddFeedFab.isEnabled = true
                }

                Snackbar.make(searchResultCard, snackText, Snackbar.LENGTH_SHORT).show()
            }
        }
    }
}
