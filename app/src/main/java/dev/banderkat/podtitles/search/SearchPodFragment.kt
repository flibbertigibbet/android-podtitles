package dev.banderkat.podtitles.search

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.work.*
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentSearchPodBinding
import dev.banderkat.podtitles.models.GpodderSearchResult
import dev.banderkat.podtitles.workers.PODCAST_QUERY_PARAM
import dev.banderkat.podtitles.workers.PodcastSearchWorker
import java.util.concurrent.TimeUnit

class SearchPodFragment : Fragment() {
    companion object {
        const val TAG = "SearchPodFragment"
        const val SEARCH_WORK_TAG = "podtitles_search"
        const val SEARCH_DEBOUNCE_MS = 500L
    }

    private var _binding: FragmentSearchPodBinding? = null
    private val binding get() = _binding!!
    private lateinit var workManager: WorkManager
    private var searchResults: List<GpodderSearchResult> = listOf()

    private val viewModel: SearchViewModel by lazy {
        ViewModelProvider(this)[SearchViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchPodBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        workManager = WorkManager.getInstance(requireActivity())

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.app_bar_menu, menu)
                Log.d(TAG, "menu created")
                setupMenu(menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                Log.d(TAG, "menu item ${menuItem.itemId} selected")
                return true
            }
        }, viewLifecycleOwner)

        val adapter = SearchPodAdapter(
            SearchPodAdapter.OnClickListener { result ->
                val action =
                    SearchPodFragmentDirections.actionSearchPodFragmentToSearchResultFragment(result)
                findNavController().navigate(action)
            }
        )

        binding.searchResultsRv.adapter = adapter

        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            binding.searchResultsProgress.visibility = View.GONE
            binding.searchResultsRv.visibility = View.VISIBLE

            adapter.submitList(results)
            adapter.notifyDataSetChanged()
            searchResults = results
        }
    }

    private fun setupMenu(menu: Menu) {
        val searchItem: MenuItem? = menu.findItem(R.id.action_search_podcasts)
        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(p0: MenuItem?): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(p0: MenuItem?): Boolean {
                findNavController().navigateUp()
                return true
            }
        })

        val searchView = searchItem?.actionView as SearchView
        searchView.apply {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    Log.d(TAG, "query text $query submitted")
                    // TODO: hide keyboard?
                    return false
                }

                override fun onQueryTextChange(query: String?): Boolean {
                    Log.d(TAG, "Query text changed to $query")
                    if (query.isNullOrEmpty()) {
                        viewModel.clearSearch()
                    } else {
                        searchPodcasts(query)
                    }
                    return true
                }
            })

            setIconifiedByDefault(false)
            setOnSuggestionListener(object : SearchView.OnSuggestionListener {
                override fun onSuggestionSelect(position: Int): Boolean {
                    Log.d(TAG, "suggestion selected at $position")
                    return false
                }

                override fun onSuggestionClick(position: Int): Boolean {
                    Log.d(TAG, "suggestion clicked at $position")
                    val result = searchResults[position]
                    val action =
                        SearchPodFragmentDirections.actionSearchPodFragmentToSearchResultFragment(
                            result
                        )
                    findNavController().navigate(action)
                    return true
                }
            })
        }
    }

    private fun searchPodcasts(query: String) {
        Log.d(TAG, "Go search for podcasts with $query")
        binding.searchResultsRv.visibility = View.GONE
        binding.searchResultsProgress.visibility = View.VISIBLE

        // cancel any previous searches
        workManager.cancelAllWorkByTag(SEARCH_WORK_TAG)

        val searchRequest = OneTimeWorkRequestBuilder<PodcastSearchWorker>()
            .setInitialDelay(SEARCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS) // debounce
            .setInputData(workDataOf(PODCAST_QUERY_PARAM to query))
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .addTag(SEARCH_WORK_TAG)
            .build()

        workManager.enqueue(searchRequest)
        workManager
            .getWorkInfoByIdLiveData(searchRequest.id)
            .observe(this) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        Log.d(TAG, "Successfully got search results")
                        binding.searchResultsRv.visibility = View.VISIBLE
                        binding.searchResultsProgress.visibility = View.GONE
                    }
                    WorkInfo.State.FAILED -> {
                        Log.e(TAG, "Search worker failed")
                    }
                    else -> {
                        Log.d(TAG, "Search worker moved to state ${workInfo?.state}")
                    }
                }
            }
    }
}
