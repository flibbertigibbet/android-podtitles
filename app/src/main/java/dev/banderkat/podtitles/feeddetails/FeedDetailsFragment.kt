package dev.banderkat.podtitles.feeddetails

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
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
import androidx.recyclerview.widget.RecyclerView
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentFeedDetailsBinding
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class FeedDetailsFragment : Fragment() {
    companion object {
        const val TAG = "FeedDetailsFragment"
    }

    private val args: FeedDetailsFragmentArgs by navArgs()
    private lateinit var feed: PodFeed
    private var cardDetailsExpanded = false

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
                val action =
                    FeedDetailsFragmentDirections.actionFeedDetailsFragmentToEpisodeFragment(
                        episode,
                        feed
                    )
                findNavController().navigate(action)
            }
        )

        // FIXME: losing scroll state on navigation back from an episode
        // Wait to lay out adapter until items are ready, to preserve scroll state
        // See: https://medium.com/androiddevelopers/restore-recyclerview-scroll-position-a8fbdc9a9334
        adapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        val dividerDecoration = DividerItemDecoration(
            binding.feedDetailsEpisodeRv.context,
            LinearLayoutManager.VERTICAL
        )

        binding.feedDetailsEpisodeRv.addItemDecoration(dividerDecoration)
        binding.feedDetailsEpisodeRv.adapter = adapter

        lifecycleScope.launch {
            viewModel.getEpisodePages(feed.url).collectLatest {
                adapter.submitData(it)
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
            if (cardDetailsExpanded) collapseCardDetails() else expandCardDetails()
            cardDetailsExpanded = !cardDetailsExpanded
        }
    }

    private fun expandCardDetails() {
        binding.feedCardDetailsExpandFab.apply {
            setImageResource(android.R.drawable.arrow_up_float)
            contentDescription = getString(R.string.card_details_collapse_fab_description)
        }

        binding.feedDetailsCard.apply {
            if (feed.category.isNotBlank()) {
                feedCardCategory.text = feed.category
                feedCardCategory.visibility = View.VISIBLE
            }
            if (feed.subCategory.isNotBlank() && feed.subCategory != feed.category) {
                feedCardSubcategory.text = feed.subCategory
                feedCardSubcategory.visibility = View.VISIBLE
            }

            if (feed.language.isNotBlank()) {
                val language = Locale.Builder()
                    .setLanguageTag(feed.language)
                    .build()
                    .displayLanguage
                feedCardLanguage.text = language
                feedCardLanguage.visibility = View.VISIBLE
            }
            if (feed.link.isNotBlank()) {
                feedCardLink.visibility = View.VISIBLE
                feedCardLink.setOnClickListener {
                    val webIntent = Intent(Intent.ACTION_VIEW)
                    webIntent.data = Uri.parse(feed.link)
                    startActivity(webIntent)
                }
            }
            if (feed.description.isNotBlank()) {
                feedCardDescription.text = Html.fromHtml(
                    feed.description,
                    Html.FROM_HTML_MODE_LEGACY
                )
                feedCardDescription.visibility = View.VISIBLE
            }
            if (feed.copyright.isNotBlank()) {
                feedCardCopyright.text = feed.copyright
                feedCardCopyright.visibility = View.VISIBLE
            }
        }
    }

    private fun collapseCardDetails() {
        binding.feedCardDetailsExpandFab.apply {
            setImageResource(android.R.drawable.arrow_down_float)
            contentDescription = getString(R.string.card_details_expand_fab_description)
        }

        binding.feedDetailsCard.apply {
            feedCardCategory.visibility = View.GONE
            feedCardSubcategory.visibility = View.GONE
            feedCardLanguage.visibility = View.GONE
            feedCardLink.visibility = View.GONE
            feedCardDescription.visibility = View.GONE
            feedCardCopyright.visibility = View.GONE
        }
    }
}
