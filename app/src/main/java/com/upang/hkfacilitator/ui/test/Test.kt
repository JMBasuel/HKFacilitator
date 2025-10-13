package com.upang.hkfacilitator.ui.test

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.work.*
import com.upang.hkfacilitator.databinding.TestBinding
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.utils.*

@SuppressLint("SetTextI18n")
class Test : Fragment() {

    private lateinit var binding: TestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializations()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = TestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.back.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnTest.setOnDebouncedClickListener {
            runTest()
        }
    }

    private fun initializations() {

    }

    private fun runTest() {
//        binding.tvResult.text = null
        binding.tvResult.text = "NO TESTS ADDED"
        if (binding.tvResult.text.isNullOrEmpty()) test()
    }

    private fun test() {
        val workRequest = OneTimeWorkRequestBuilder<NotificationCheckWorker>().build()
        WorkManager.getInstance(requireContext()).enqueue(workRequest)
        WorkManager.getInstance(requireContext())
            .getWorkInfoByIdLiveData(workRequest.id)
            .observe(viewLifecycleOwner) { workInfo ->
                if (workInfo != null) when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> binding.tvResult.text = "TEST: SUCCESS"
                    WorkInfo.State.FAILED -> binding.tvResult.text = "TEST: FAILED"
                    WorkInfo.State.RUNNING -> binding.tvResult.text = "TEST: RUNNING"
                    else -> binding.tvResult.text = "TEST: CANCELLED"
                }
            }
    }
}