package dev.banderkat.podtitles.feeddetails

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentFeedFullDetailsBinding
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.utils.Utils
import java.util.*

class FeedFullDetailsFragment : Fragment() {
    companion object {
        const val TAG = "FeedFullDetailsFragment"
    }

    private val args: FeedDetailsFragmentArgs by navArgs()
    private lateinit var feed: PodFeed

    private var _binding: FragmentFeedFullDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FeedDetailsViewModel by lazy {
        ViewModelProvider(this)[FeedDetailsViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedFullDetailsBinding.inflate(inflater, container, false)
        feed = args.feed
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.feedDeleted.observe(viewLifecycleOwner) {
            if (it == true) goBackToFeedList()
        }

        binding.feedFullCardDetailsCollapseFab.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.feedFullDetailsCard.apply {
            feedCardAuthor.text = feed.author
            feedCardTitle.text = feed.title
            feedCardImage.contentDescription = feed.imageTitle.ifEmpty {
                requireContext().getString(R.string.default_logo_description)
            }
            Utils.loadLogo(feed.image, requireContext(), feedCardImage)

            feedCardDeleteButtonWrapper.visibility = View.VISIBLE
            feedCardDeleteButton.setOnClickListener { confirmDeleteFeed() }

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

    private fun confirmDeleteFeed() {
        val builder: AlertDialog.Builder? = activity?.let {
            AlertDialog.Builder(it)
        }

        builder?.setMessage(R.string.confirm_delete_feed_message)
            ?.setTitle(R.string.confirm_delete_feed_title)
            ?.setNegativeButton(android.R.string.cancel) { _, _ -> /* no-op */ }
            ?.setPositiveButton(android.R.string.ok) { _, _ -> deleteFeed() }
            ?.create()
            ?.show()
    }

    private fun goBackToFeedList() = findNavController().navigate(
        FeedFullDetailsFragmentDirections
            .actionFeedFullDetailsFragmentToFeedListFragment()
    )

    private fun deleteFeed() {
        binding.feedFullDetailsCard.feedCardDeleteProgress.visibility = View.VISIBLE
        binding.feedFullDetailsCard.feedCardDeleteButton.isEnabled = false
        viewModel.deleteFeed(feed)
    }
}