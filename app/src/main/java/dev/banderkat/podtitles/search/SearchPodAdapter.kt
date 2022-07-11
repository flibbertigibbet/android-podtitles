package dev.banderkat.podtitles.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.banderkat.podtitles.databinding.SearchResultItemBinding
import dev.banderkat.podtitles.models.GpodderSearchResult

class SearchPodAdapter(private val onClickListener: OnClickListener) :
    ListAdapter<GpodderSearchResult, SearchPodAdapter.GpodderSearchResultViewHolder>(DiffCallback) {

    class GpodderSearchResultViewHolder(private val binding: SearchResultItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(searchResult: GpodderSearchResult) {
            binding.searchResultTitle.text = searchResult.title
            binding.searchResultsSubtitle.text = searchResult.author
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<GpodderSearchResult>() {
        override fun areItemsTheSame(
            oldItem: GpodderSearchResult,
            newItem: GpodderSearchResult
        ): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(
            oldItem: GpodderSearchResult,
            newItem: GpodderSearchResult
        ): Boolean {
            return oldItem.url == newItem.url && oldItem.title == newItem.title
        }

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): GpodderSearchResultViewHolder {
        return GpodderSearchResultViewHolder(
            SearchResultItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: GpodderSearchResultViewHolder, position: Int) {
        val searchResult = getItem(position)
        holder.itemView.setOnClickListener {
            onClickListener.onClick(searchResult)
        }
        holder.bind(searchResult)
    }

    class OnClickListener(val clickListener: (searchResult: GpodderSearchResult) -> Unit) {
        fun onClick(searchResult: GpodderSearchResult) = clickListener(searchResult)
    }
}