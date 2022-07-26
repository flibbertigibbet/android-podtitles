package dev.banderkat.podtitles.managevosk

import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dev.banderkat.podtitles.databinding.FragmentManageVoskModelsBinding
import dev.banderkat.podtitles.utils.Utils

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

        val dividerDecoration = DividerItemDecoration(
            binding.downloadedVoskModelsRv.context,
            LinearLayoutManager.VERTICAL
        )

        binding.downloadedVoskModelsRv.addItemDecoration(dividerDecoration)

        val adapter = VoskModelsAdapter(VoskModelsAdapter.OnClickListener { model ->
            Log.d(TAG, "clicked model $model")
            // TODO: show confirmation dialog
            if (model.isDownloaded) {
                binding.downloadedVoskModelsRv.visibility = View.GONE
                binding.voskModelDownloadingProgress.visibility = View.VISIBLE
                Utils.deleteVoskModelDownload(requireContext(), model.name)
                viewModel.deleteVoskModel(model)
                binding.voskModelDownloadingProgress.visibility = View.GONE
                binding.downloadedVoskModelsRv.visibility = View.VISIBLE
            } else {
                viewModel.downloadVoskModel(model.url)
            }
        })

        binding.downloadedVoskModelsRv.adapter = adapter
        viewModel.voskModels.observe(viewLifecycleOwner) { adapter.submitList(it) }
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.downloadedVoskModelsRv.visibility = View.GONE
                binding.voskModelDownloadingProgress.visibility = View.VISIBLE
            } else {
                binding.voskModelDownloadingProgress.visibility = View.GONE
                binding.downloadedVoskModelsRv.visibility = View.VISIBLE
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