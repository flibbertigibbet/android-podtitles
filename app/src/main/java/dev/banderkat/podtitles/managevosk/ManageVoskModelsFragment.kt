package dev.banderkat.podtitles.managevosk

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dev.banderkat.podtitles.databinding.FragmentManageVoskModelsBinding

class ManageVoskModelsFragment: Fragment() {
    companion object {
        const val TAG = "ManageVoskFragment"
    }

    private var _binding: FragmentManageVoskModelsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageVoskModelsBinding.inflate(inflater, container, false)
        return binding.root
    }
}