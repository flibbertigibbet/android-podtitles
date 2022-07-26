package dev.banderkat.podtitles.managevosk

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.util.ObjectsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.VoskModelListItemBinding
import dev.banderkat.podtitles.models.VoskModel

class VoskModelsAdapter(
    private val onClickListener: OnClickListener
) :
    ListAdapter<VoskModel, VoskModelsAdapter.VoskModelViewHolder>(DiffCallback) {

    class VoskModelViewHolder(
        private val binding: VoskModelListItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(model: VoskModel, onClickListener: OnClickListener) {
            binding.apply {
                voskModelItemLanguage.text = model.langText
                voskModelItemName.text = model.name
                voskModelItemSize.text = model.sizeText

                voskModelItemAddDeleteButton.apply {
                    setOnClickListener { onClickListener.onClick(model) }
                    if (model.isDownloaded) {
                        setImageDrawable(context.getDrawable(R.drawable.ic_baseline_delete_24))
                        contentDescription = context.getString(R.string.remove_feed)
                    } else {
                        setImageDrawable(context.getDrawable(R.drawable.ic_baseline_add_circle_24))
                        contentDescription = context.getString(R.string.download_vosk_model)
                    }
                }
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
            VoskModelListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: VoskModelViewHolder, position: Int) {
        val searchResult = getItem(position)
        holder.bind(searchResult, onClickListener)
    }

    class OnClickListener(val clickListener: (model: VoskModel) -> Unit) {
        fun onClick(model: VoskModel) = clickListener(model)
    }
}
