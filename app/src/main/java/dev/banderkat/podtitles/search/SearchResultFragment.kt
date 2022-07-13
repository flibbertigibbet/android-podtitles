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
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
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
        const val GLIDE_LOADER_STROKE_WIDTH = 5f
        const val GLIDE_LOADER_CENTER_RADIUS = 30f
    }

    private var _binding: FragmentSearchResultBinding? = null
    private val binding get() = _binding!!
    private val args: SearchResultFragmentArgs by navArgs()
    private lateinit var searchResult: GpodderSearchResult
    private val viewModel: SearchViewModel by lazy {
        ViewModelProvider(this)[SearchViewModel::class.java]
    }
    private var podFeed: PodFeed? = null

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
            loadImage(searchResult.logoUrl)

            searchResultCardAddFeedFab.setOnClickListener {
                if (podFeed != null) viewModel.removeFeed(podFeed!!) else addFeed(httpsFeedUri)
            }
        }

        viewModel.getFeed(httpsFeedUri).observe(viewLifecycleOwner) { feed ->
            Log.d(TAG, "Existing feed for this URL is $feed")
            podFeed = feed
            if (feed != null) {
                // already subscribed; change button to remove feed instead of add it
                // TODO: also change color?
                binding.searchResultCardAddFeedFab.setImageDrawable(
                    resources.getDrawable(
                        com.google.android.material.R.drawable.ic_m3_chip_close,
                        requireContext().theme
                    )
                )
            } else {
                binding.searchResultCardAddFeedFab.setImageDrawable(
                    resources.getDrawable(
                        android.R.drawable.ic_input_add,
                        requireContext().theme
                    )
                )
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

                var snackText = ""
                if (itWorked) {
                    Log.d(TAG, "Feed added successfully! Go to feed details")
                    snackText = getString(R.string.feed_added_success)
                    findNavController().navigate(
                        R.id.action_searchResultFragment_to_feedDetailsFragment
                    )
                } else {
                    Log.d(TAG, "Feed could not be added. Show an error")
                    snackText = getString(R.string.feed_added_failure)
                    searchResultCardAddFeedFab.isEnabled = true
                }

                Snackbar.make(searchResultCard, snackText, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadImage(logoUrl: String?) {
        if (logoUrl.isNullOrEmpty()) {
            binding.searchResultImage.setImageResource(R.drawable.ic_headphones)
        } else {
            // ensure URL is HTTPS before attempting to load it
            val httpsLogoUri = Utils.convertToHttps(logoUrl)

            val circularProgressDrawable = CircularProgressDrawable(requireContext())
            circularProgressDrawable.strokeWidth = GLIDE_LOADER_STROKE_WIDTH
            circularProgressDrawable.centerRadius = GLIDE_LOADER_CENTER_RADIUS
            circularProgressDrawable.start()

            Glide.with(this@SearchResultFragment)
                .load(httpsLogoUri)
                .placeholder(circularProgressDrawable)
                .fitCenter()
                .error(R.drawable.ic_headphones)
                .into(binding.searchResultImage)
        }
    }
}
