package dev.banderkat.podtitles.feeddetails

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.util.ObjectsCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import dev.banderkat.podtitles.databinding.EpisodeListItemBinding
import dev.banderkat.podtitles.models.PodEpisode
import dev.banderkat.podtitles.utils.Utils

class EpisodesAdapter(private val onClickListener: OnClickListener) :
    PagingDataAdapter<PodEpisode, EpisodesAdapter.PodEpisodeViewHolder>(diffCallback) {

    companion object {
        val diffCallback = object : DiffUtil.ItemCallback<PodEpisode>() {
            override fun getChangePayload(oldItem: PodEpisode, newItem: PodEpisode): Any = Any()

            override fun areItemsTheSame(
                oldItem: PodEpisode,
                newItem: PodEpisode
            ): Boolean {
                return oldItem.guid == newItem.guid
            }

            override fun areContentsTheSame(
                oldItem: PodEpisode,
                newItem: PodEpisode
            ): Boolean {
                return ObjectsCompat.equals(oldItem, newItem)
            }
        }
    }

    class PodEpisodeViewHolder(
        private val binding: EpisodeListItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(episode: PodEpisode) {
            binding.apply {
                episodeItemTitle.text = episode.title
                episodeItemPubdate.text = Utils.getFormattedDate(episode.pubDate)
                episodeItemDuration.text = Utils.getFormattedDuration(episode.duration)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PodEpisodeViewHolder {
        return PodEpisodeViewHolder(
            EpisodeListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: PodEpisodeViewHolder, position: Int) {
        val searchResult = getItem(position) ?: return
        holder.itemView.setOnClickListener {
            onClickListener.onClick(searchResult)
        }
        holder.bind(searchResult)
    }

    class OnClickListener(val clickListener: (feed: PodEpisode) -> Unit) {
        fun onClick(feed: PodEpisode) = clickListener(feed)
    }
}
