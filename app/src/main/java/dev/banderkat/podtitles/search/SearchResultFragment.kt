package dev.banderkat.podtitles.search

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentSearchResultBinding
import dev.banderkat.podtitles.models.GpodderSearchResult
import dev.banderkat.podtitles.utils.AddFeed

class SearchResultFragment : Fragment() {
    companion object {
        const val TAG = "SearchResultFragment"
    }

    private var _binding: FragmentSearchResultBinding? = null
    private val binding get() = _binding!!
    private val args: SearchResultFragmentArgs by navArgs()
    private lateinit var searchResult: GpodderSearchResult


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

        binding.apply {
            searchResultCardTitle.text = searchResult.title
            searchResultCardAuthor.text = searchResult.author
            searchResultCardLink.text = searchResult.website
            searchResultCardDescription.text = searchResult.description

            searchResultCardAddFeedFab.setOnClickListener {
                Log.d(TAG, "clicked to add feed ${searchResult.url}")
                searchResultCardFabProgress.visibility = View.VISIBLE
                searchResultCardAddFeedFab.isEnabled = false
                AddFeed(requireContext(), viewLifecycleOwner, searchResult.url) { itWorked ->
                    searchResultCardFabProgress.visibility = View.INVISIBLE

                    var snackText = ""
                    if (itWorked) {
                        Log.d(TAG, "Feed added successfully! Go to feed details")
                        snackText = getString(R.string.feed_added_success)
                        findNavController().navigate(R.id.action_searchResultFragment_to_feedDetailsFragment)
                    } else {
                        Log.d(TAG, "Feed could not be added. Show an error")
                        snackText = getString(R.string.feed_added_failure)
                        searchResultCardAddFeedFab.isEnabled = true
                    }

                    Snackbar.make(searchResultCard, snackText, Snackbar.LENGTH_SHORT).show()
                }
            }

            if (!searchResult.logoUrl.isNullOrEmpty()) {
                Glide.with(this@SearchResultFragment)
                    .load(searchResult.logoUrl)
                    .placeholder(R.drawable.ic_headphones)
                    .fitCenter()
                    .into(searchResultImage)
            }
        }
    }
}
