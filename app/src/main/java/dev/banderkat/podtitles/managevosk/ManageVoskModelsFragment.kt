package dev.banderkat.podtitles.managevosk

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentManageVoskModelsBinding

class ManageVoskModelsFragment : Fragment() {
    companion object {
        const val TAG = "ManageVoskFragment"
    }

    private var _binding: FragmentManageVoskModelsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ManageVoskViewModel by lazy {
        ViewModelProvider(this)[ManageVoskViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageVoskModelsBinding.inflate(inflater, container, false)
        viewModel.fetchVoskModels()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.downloadableVoskModels.observe(viewLifecycleOwner) { voskModels ->
            val voskModelAdapter = VoskModelAdapter(
                requireContext(),
                R.layout.vosk_model_item,
                voskModels
            )
            binding.downloadVoskModelSpinner.adapter = voskModelAdapter

            if (voskModels.isNotEmpty()) {
                binding.downloadVoskModelPrompt.visibility = View.VISIBLE
                binding.downloadVoskModelSpinner.visibility = View.VISIBLE
                binding.downloadVoskModelButton.visibility = View.VISIBLE
            }
        }
    }

    class Factory(val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ManageVoskViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ManageVoskViewModel(app) as T
            }
            throw IllegalArgumentException("Unable to construct view model")
        }
    }
}