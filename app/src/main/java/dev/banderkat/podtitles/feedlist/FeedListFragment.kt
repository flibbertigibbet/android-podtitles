package dev.banderkat.podtitles.feedlist

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentFeedListBinding

class FeedListFragment : Fragment() {
    companion object {
        const val TAG = "FeedListFragment"
    }

    private var _binding: FragmentFeedListBinding? = null
    private val binding get() = _binding!!

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
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.app_bar_menu, menu)
                // this activity handles search
                val searchItem: MenuItem? = menu.findItem(R.id.action_search_podcasts)
                val searchView = searchItem?.actionView as SearchView
                val searchManager =
                    requireActivity().getSystemService(Context.SEARCH_SERVICE) as SearchManager
                searchView.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().componentName))
                searchView.setOnSearchClickListener {
                    findNavController().navigate(R.id.action_feedListFragment_to_searchPodFragment)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return true
            }
        }, viewLifecycleOwner)
    }
}
