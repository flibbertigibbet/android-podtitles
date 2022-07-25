package dev.banderkat.podtitles.managevosk

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.util.ObjectsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.banderkat.podtitles.databinding.FeedCardBinding
import dev.banderkat.podtitles.models.VoskModel

class VoskModelsAdapter(
    private val onClickListener: OnClickListener,
    private val context: Context
) :
    ListAdapter<VoskModel, VoskModelsAdapter.VoskModelViewHolder>(DiffCallback) {

    class VoskModelViewHolder(
        private val binding: FeedCardBinding,
        private val context: Context
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(model: VoskModel) {
            binding.apply {
                // TODO: set views
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<VoskModel>() {
        override fun areItemsTheSame(
            oldItem: VoskModel,
            newItem: VoskModel
        ): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(
            oldItem: VoskModel,
            newItem: VoskModel
        ): Boolean {
            return ObjectsCompat.equals(oldItem, newItem)
        }

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): VoskModelViewHolder {
        return VoskModelViewHolder(
            FeedCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            context
        )
    }

    override fun onBindViewHolder(holder: VoskModelViewHolder, position: Int) {
        val searchResult = getItem(position)
        holder.itemView.setOnClickListener {
            onClickListener.onClick(searchResult)
        }
        holder.bind(searchResult)
    }

    class OnClickListener(val clickListener: (model: VoskModel) -> Unit) {
        fun onClick(model: VoskModel) = clickListener(model)
    }
}
