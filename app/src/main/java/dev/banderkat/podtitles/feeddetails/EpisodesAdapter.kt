package dev.banderkat.podtitles.feeddetails

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.banderkat.podtitles.databinding.EpisodeListItemBinding
import dev.banderkat.podtitles.models.PodEpisode

class EpisodesAdapter(private val onClickListener: OnClickListener) :
    ListAdapter<PodEpisode, EpisodesAdapter.PodEpisodeViewHolder>(DiffCallback) {

    class PodEpisodeViewHolder(
        private val binding: EpisodeListItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(episode: PodEpisode) {
            binding.apply {
                episodeItemTitle.text = episode.title
                episodeItemSubtitle.text = episode.category
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<PodEpisode>() {
        override fun areItemsTheSame(
            oldItem: PodEpisode,
            newItem: PodEpisode
        ): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(
            oldItem: PodEpisode,
            newItem: PodEpisode
        ): Boolean {
            return oldItem.url == newItem.url
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
        val searchResult = getItem(position)
        holder.itemView.setOnClickListener {
            onClickListener.onClick(searchResult)
        }
        holder.bind(searchResult)
    }

    class OnClickListener(val clickListener: (feed: PodEpisode) -> Unit) {
        fun onClick(feed: PodEpisode) = clickListener(feed)
    }
}
