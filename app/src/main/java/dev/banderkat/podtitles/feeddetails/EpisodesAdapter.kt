package dev.banderkat.podtitles.feeddetails

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.banderkat.podtitles.databinding.EpisodeListItemBinding
import dev.banderkat.podtitles.models.PodEpisode
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class EpisodesAdapter(private val onClickListener: OnClickListener) :
    ListAdapter<PodEpisode, EpisodesAdapter.PodEpisodeViewHolder>(DiffCallback) {


    class PodEpisodeViewHolder(
        private val binding: EpisodeListItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormatter = DateFormat.getDateInstance(DateFormat.SHORT)
        private val pubDateFormat = SimpleDateFormat(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            Locale.getDefault()
        )

        fun bind(episode: PodEpisode) {
            val formattedPubDate = try {
                dateFormatter.format(pubDateFormat.parse(episode.pubDate)!!)
            } catch (ex: Exception) {
                ""
            }
            val duration = try {
                // first try to parse it as seconds (recommended in standard)
                episode.duration.toInt().seconds.toString()
            } catch (ex: Exception) {
                try {
                    // next try to parse it as a duration string
                    val parts = episode.duration.split(":")
                    var seconds = parts.last().toInt()
                    if (parts.size > 1) seconds += parts[parts.size - 2].toInt() * 60
                    if (parts.size == 3) seconds += parts.first().toInt() * 60 * 60
                    seconds.seconds.toString()
                } catch (ex: Exception) {
                    // use it as-is
                    episode.duration
                }
            }
            binding.apply {
                episodeItemTitle.text = episode.title
                episodeItemPubdate.text = formattedPubDate
                episodeItemDuration.text = duration
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
