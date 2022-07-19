package dev.banderkat.podtitles.feedlist

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.util.ObjectsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FeedCardBinding
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.utils.Utils

class FeedsAdapter(private val onClickListener: OnClickListener, private val context: Context) :
    ListAdapter<PodFeed, FeedsAdapter.PodFeedViewHolder>(DiffCallback) {

    class PodFeedViewHolder(
        private val binding: FeedCardBinding,
        private val context: Context
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(feed: PodFeed) {
            binding.apply {
                feedCardTitle.text = feed.title
                feedCardAuthor.text = feed.author
                feedCardImage.contentDescription = feed.imageTitle.ifEmpty {
                    context.getString(R.string.default_logo_description)
                }
                Utils.loadLogo(feed.image, context, feedCardImage)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<PodFeed>() {
        override fun areItemsTheSame(
            oldItem: PodFeed,
            newItem: PodFeed
        ): Boolean {
            return oldItem.url == newItem.url
        }

        override fun areContentsTheSame(
            oldItem: PodFeed,
            newItem: PodFeed
        ): Boolean {
            return ObjectsCompat.equals(oldItem, newItem)
        }

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PodFeedViewHolder {
        return PodFeedViewHolder(
            FeedCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            context
        )
    }

    override fun onBindViewHolder(holder: PodFeedViewHolder, position: Int) {
        val searchResult = getItem(position)
        holder.itemView.setOnClickListener {
            onClickListener.onClick(searchResult)
        }
        holder.bind(searchResult)
    }

    class OnClickListener(val clickListener: (feed: PodFeed) -> Unit) {
        fun onClick(feed: PodFeed) = clickListener(feed)
    }
}
