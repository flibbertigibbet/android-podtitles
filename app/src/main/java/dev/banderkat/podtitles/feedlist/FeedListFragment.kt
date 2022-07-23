package dev.banderkat.podtitles.feedlist

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentFeedListBinding

class FeedListFragment : Fragment() {
    companion object {
        const val TAG = "FeedListFragment"
    }

    private var _binding: FragmentFeedListBinding? = null
    private val binding get() = _binding!!
    private lateinit var searchView: SearchView
    private val viewModel: FeedListViewModel by lazy {
        ViewModelProvider(this)[FeedListViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()

        val adapter = FeedsAdapter(
            FeedsAdapter.OnClickListener { feed ->
                val action =
                    FeedListFragmentDirections.actionFeedListFragmentToFeedDetailsFragment(feed)
                findNavController().navigate(action)
            },
            requireContext()
        )

        binding.feedListRv.adapter = adapter

        viewModel.feeds.observe(viewLifecycleOwner) { feeds ->
            Log.d(TAG, "Found ${feeds.size} feeds")
            adapter.submitList(feeds)
            adapter.notifyDataSetChanged()

            if (feeds.isEmpty()) {
                binding.feedListRv.visibility = View.GONE
                binding.feedListEmptyWrapper.visibility = View.VISIBLE
            } else {
                binding.feedListEmptyWrapper.visibility = View.GONE
                binding.feedListRv.visibility = View.VISIBLE
            }
        }

        binding.feedListEmptyAddPodcastButton.setOnClickListener {
            searchView.onActionViewExpanded()
        }
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.app_bar_menu, menu)
                // set up collapsed search in toolbar
                val searchItem: MenuItem? = menu.findItem(R.id.action_search_podcasts)
                searchView = searchItem?.actionView as SearchView
                val searchManager =
                    requireActivity().getSystemService(Context.SEARCH_SERVICE) as SearchManager
                searchView.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().componentName))
                searchView.setOnSearchClickListener {
                    findNavController().navigate(R.id.action_feedListFragment_to_searchPodFragment)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.action_manage_language_models -> Log.d(TAG, "TODO: manage language models")
                    R.id.action_delete_files -> Log.d(TAG, "TODO: delete files")
                }
                return true
            }
        }, viewLifecycleOwner)
    }
}
