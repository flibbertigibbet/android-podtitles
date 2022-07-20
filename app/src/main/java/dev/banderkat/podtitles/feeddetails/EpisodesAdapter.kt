package dev.banderkat.podtitles.feeddetails

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.util.ObjectsCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import dev.banderkat.podtitles.databinding.EpisodeListItemBinding
import dev.banderkat.podtitles.models.PodEpisodeItem
import dev.banderkat.podtitles.utils.Utils

class EpisodesAdapter(private val onClickListener: OnClickListener) :
    PagingDataAdapter<PodEpisodeItem, EpisodesAdapter.PodEpisodeItemViewHolder>(diffCallback) {

    companion object {
        val diffCallback = object : DiffUtil.ItemCallback<PodEpisodeItem>() {
            override fun areItemsTheSame(
                oldItem: PodEpisodeItem,
                newItem: PodEpisodeItem
            ): Boolean {
                return oldItem.guid == newItem.guid
            }

            override fun areContentsTheSame(
                oldItem: PodEpisodeItem,
                newItem: PodEpisodeItem
            ): Boolean {
                return ObjectsCompat.equals(oldItem, newItem)
            }
        }
    }

    class PodEpisodeItemViewHolder(
        private val binding: EpisodeListItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(episode: PodEpisodeItem) {
            binding.apply {
                episodeItemTitle.text = episode.title
                episodeItemPubdate.text = episode.pubDate
                episodeItemDuration.text = episode.duration
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PodEpisodeItemViewHolder {
        return PodEpisodeItemViewHolder(
            EpisodeListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: PodEpisodeItemViewHolder, position: Int) {
        val searchResult = getItem(position) ?: return
        holder.itemView.setOnClickListener {
            onClickListener.onClick(searchResult)
        }
        holder.bind(searchResult)
    }

    class OnClickListener(val clickListener: (feed: PodEpisodeItem) -> Unit) {
        fun onClick(feed: PodEpisodeItem) = clickListener(feed)
    }
}
