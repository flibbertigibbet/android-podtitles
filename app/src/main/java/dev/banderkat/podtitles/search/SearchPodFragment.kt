package dev.banderkat.podtitles.search

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentSearchPodBinding
import dev.banderkat.podtitles.models.GpodderSearchResult

class SearchPodFragment : Fragment() {
    companion object {
        const val TAG = "SearchPodFragment"
    }

    private var _binding: FragmentSearchPodBinding? = null
    private val binding get() = _binding!!
    private var searchResults: List<GpodderSearchResult> = listOf()
    private lateinit var searchView: SearchView

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
        addMenuProvider()

        val adapter = SearchPodAdapter(
            SearchPodAdapter.OnClickListener { result ->
                // remove listener to prevent last search being cleared
                searchView.setOnQueryTextListener(null)
                val action =
                    SearchPodFragmentDirections.actionSearchPodFragmentToSearchResultFragment(result)
                findNavController().navigate(action)
            }
        )

        binding.searchResultsRv.adapter = adapter

        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            // prevent screen flashing stale results
            if (viewModel.searchQuery.value.isNullOrEmpty() && results.isNotEmpty()) return@observe

            binding.searchResultsProgress.visibility = View.GONE
            binding.searchResultsRv.visibility = View.VISIBLE

            adapter.submitList(results)
            adapter.notifyDataSetChanged()
            searchResults = results
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.searchResultsRv.visibility = if (isLoading) View.GONE else View.VISIBLE
            binding.searchResultsProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun addMenuProvider() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.app_bar_menu, menu)
                setupMenu(menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                viewModel.updateSearchQuery("") // clear the search
                searchView.onActionViewCollapsed() // close the search bar
                findNavController().navigateUp() // go back to the feed list
                return true
            }
        }, viewLifecycleOwner)
    }

    private fun setupMenu(menu: Menu) {
        val searchItem: MenuItem? = menu.findItem(R.id.action_search_podcasts)
        searchView = searchItem?.actionView as SearchView
        searchView.apply {
            // restore previous query on navigating back from a result
            val searchQuery = viewModel.searchQuery.value
            onActionViewExpanded()
            if (!searchQuery.isNullOrEmpty()) setQuery(searchQuery, false)
            requestFocus()

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(query: String?): Boolean {
                    viewModel.updateSearchQuery(query ?: "")
                    return true
                }
            })
        }
    }
}
