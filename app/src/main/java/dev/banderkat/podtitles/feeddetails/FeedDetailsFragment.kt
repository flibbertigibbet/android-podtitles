package dev.banderkat.podtitles.feeddetails

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentFeedDetailsBinding
import dev.banderkat.podtitles.feedlist.FeedListFragment
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.utils.Utils
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

        binding.feedDetailsEpisodeRv.adapter = adapter

        viewModel.getEpisodes(feed.url).observe(viewLifecycleOwner) { episodes ->
            Log.d(FeedListFragment.TAG, "Found ${episodes.size} episodes")
            adapter.submitList(episodes)
            adapter.notifyDataSetChanged()
        }

        binding.feedCardDetailsExpandFab.setOnClickListener {
            if (cardDetailsExpanded) collapseCardDetails() else expandCardDetails()
            cardDetailsExpanded = !cardDetailsExpanded
        }
    }

    private fun expandCardDetails() {
        binding.feedCardDetailsExpandFab.apply {
            setImageResource(android.R.drawable.arrow_up_float)
            contentDescription = getString(R.string.feed_card_details_collapse_fab_description)
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
                feedCardDescription.text = feed.description
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
            contentDescription = getString(R.string.feed_card_details_expand_fab_description)
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
