package dev.banderkat.podtitles.feedlist

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentFeedListBinding
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.utils.FetchFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FeedListFragment : Fragment() {
    companion object {
        const val TAG = "FeedListFragment"
        const val DRAGGING_ALPHA = 0.7F
        const val STILL_ALPHA = 1F
    }

    private var _binding: FragmentFeedListBinding? = null
    private val binding get() = _binding!!
    private lateinit var searchView: SearchView
    private lateinit var adapter: FeedsAdapter
    private val viewModel: FeedListViewModel by lazy {
        ViewModelProvider(this)[FeedListViewModel::class.java]
    }

    private val feedObserver = Observer<List<PodFeed>> { feeds ->
        Log.d(TAG, "updating feed list. first feed is ${feeds.first().title}")
        adapter.submitList(feeds)

        if (feeds.isEmpty()) {
            binding.feedListRv.visibility = View.GONE
            binding.feedListEmptyWrapper.visibility = View.VISIBLE
        } else {
            showFeedList()
        }
    }

    private fun showFeedList() {
        binding.feedListEmptyWrapper.visibility = View.GONE
        binding.feedListRv.visibility = View.VISIBLE
        viewModel.feeds.removeObserver(feedObserver)
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

        adapter = FeedsAdapter(
            FeedsAdapter.OnClickListener { feed ->
                val action =
                    FeedListFragmentDirections.actionFeedListFragmentToFeedDetailsFragment(feed)
                findNavController().navigate(action)
            },
            requireContext()
        )

        binding.feedListRv.adapter = adapter
        setUpDragAndDrop()

        binding.feedListEmptyAddPodcastButton.setOnClickListener {
            searchView.onActionViewExpanded()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.d(TAG, "on attach")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        viewModel.feeds.observe(viewLifecycleOwner, feedObserver)
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
                searchView.setSearchableInfo(
                    searchManager.getSearchableInfo(requireActivity().componentName)
                )
                searchView.setOnSearchClickListener {
                    findNavController().navigate(
                        FeedListFragmentDirections.actionFeedListFragmentToSearchPodFragment()
                    )
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.action_add_podcast_by_url -> promptAddFeedByUrl()
                    R.id.action_manage_language_models -> findNavController().navigate(
                        FeedListFragmentDirections
                            .actionFeedListFragmentToManageVoskModelsFragment()
                    )
                    R.id.action_delete_files -> confirmDeleteFiles()
                }
                return true
            }
        }, viewLifecycleOwner)
    }

    private fun promptAddFeedByUrl() {
        val urlEditText = EditText(requireActivity())
        val builder: AlertDialog.Builder? = activity?.let {
            AlertDialog.Builder(it)
        }

        builder?.setTitle(R.string.add_podcast_by_url)
            ?.setMessage(R.string.add_podcast_by_url_prompt)
            ?.setView(urlEditText)
            ?.setPositiveButton(android.R.string.ok) { _, _ ->
                addFeedByUrl(urlEditText.text.toString())
            }?.setNegativeButton(android.R.string.cancel) { _, _ -> /* no-op */ }

        val dialog: AlertDialog? = builder?.create()
        dialog?.show()
    }

    private fun addFeedByUrl(url: String) {
        Log.d(TAG, "Going to try to add a feed from $url")
        FetchFeed(requireContext(), viewLifecycleOwner, url) { succeeded ->
            if (succeeded) {
                Snackbar.make(
                    binding.root,
                    R.string.feed_added_success,
                    Snackbar.LENGTH_LONG
                ).show()

                goToFeed(url)
            } else {
                Snackbar.make(
                    binding.root,
                    R.string.feed_added_failure,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun goToFeed(url: String) {
        viewModel.getFeedByUrl(url).observe(viewLifecycleOwner) { feed ->
            if (feed == null) return@observe
            val action =
                FeedListFragmentDirections
                    .actionFeedListFragmentToFeedDetailsFragment(feed)
            findNavController().navigate(action)
        }
    }

    private fun confirmDeleteFiles() {
        val builder: AlertDialog.Builder? = activity?.let {
            AlertDialog.Builder(it)
        }

        val confirmMessage = getString(
            R.string.confirm_delete_files_message,
            viewModel.getDownloadCacheSize(),
            viewModel.getTranscriptsSize()
        )

        builder?.setMessage(confirmMessage)
            ?.setTitle(R.string.confirm_delete_files_title)
            ?.setPositiveButton(android.R.string.ok) { _, _ ->
                deleteFiles()
            }
            ?.setNegativeButton(android.R.string.cancel) { _, _ -> /* no-op */ }

        val dialog: AlertDialog? = builder?.create()
        dialog?.show()
    }

    private fun deleteFiles() {
        viewModel.deleteFiles()
    }

    private fun setUpDragAndDrop() {
        val simpleItemTouchCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled(): Boolean = true

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    // drag start
                    viewHolder?.itemView?.alpha = DRAGGING_ALPHA
                }
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {

                adapter.notifyItemMoved(
                    viewHolder.absoluteAdapterPosition,
                    target.absoluteAdapterPosition
                )

                Log.d(TAG, "on move ${viewHolder.absoluteAdapterPosition} to ${target.absoluteAdapterPosition}")

                swapFeedOrders(viewHolder.itemView.tag.toString(), target.itemView.tag.toString())
                return true
            }

            // drag end
            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                Log.d(TAG, "clear view. view holder position is ${viewHolder.absoluteAdapterPosition}")
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = STILL_ALPHA
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                /* no-op */
            }
        }

        ItemTouchHelper(simpleItemTouchCallback).attachToRecyclerView(binding.feedListRv)
    }

    private fun swapFeeds(feedPair: Pair<PodFeed, PodFeed>) {
        val positionOne = feedPair.first.displayOrder
        feedPair.first.displayOrder = feedPair.second.displayOrder
        feedPair.second.displayOrder = positionOne
        viewModel.updateFeedPair(feedPair)
        Log.d(TAG, "swapped feed orders for ${feedPair.first.title} and ${feedPair.second.title}")
    }

    private fun swapFeedOrders(feedOneUrl: String, feedTwoUrl: String) {
        val twoFeeds = viewModel.getTwoFeeds(feedOneUrl, feedTwoUrl)
        twoFeeds.observe(viewLifecycleOwner) { feedPair ->
            if (feedPair == null) return@observe
            twoFeeds.removeObservers(viewLifecycleOwner)
            swapFeeds(feedPair)
        }
    }
}
