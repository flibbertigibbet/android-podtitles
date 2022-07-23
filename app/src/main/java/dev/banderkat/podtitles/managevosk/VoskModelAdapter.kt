package dev.banderkat.podtitles.managevosk

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.VoskModelItemBinding
import dev.banderkat.podtitles.models.VoskModel

class VoskModelAdapter(context: Context, resource: Int, data: List<VoskModel>) :
    ArrayAdapter<VoskModel>(context, resource, R.id.vosk_model_item_name, data) {

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            VoskModelItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        } else {
            VoskModelItemBinding.bind(convertView)
        }

        val voskModel = getItem(position)
        binding.voskModelItemName.text = voskModel?.name
        binding.voskModelItemLanguage.text = voskModel?.langText
        binding.voskModelItemSize.text = voskModel?.sizeText

        return binding.root
    }
}
