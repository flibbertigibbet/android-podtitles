package dev.banderkat.podtitles.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import dev.banderkat.podtitles.databinding.FragmentSearchResultBinding
import dev.banderkat.podtitles.models.GpodderSearchResult

class SearchResultFragment: Fragment() {
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
            searchResultCardLink.text = searchResult.url
        }
    }
}
