package com.simsim.island.ui.main

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simsim.island.R
import com.simsim.island.databinding.EditBlockRuleDialogFragmentBinding
import com.simsim.island.model.BlockRule
import com.simsim.island.model.BlockTarget
import com.simsim.island.preferenceKey.PreferenceKey
import com.simsim.island.util.LOG_TAG
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditBlockRuleDialogFragment : DialogFragment(){
    private val viewModel:MainViewModel by activityViewModels()
    private lateinit var binding: EditBlockRuleDialogFragmentBinding
    private lateinit var toolbar: MaterialToolbar
    private lateinit var preferenceKey: PreferenceKey
    private val args:EditBlockRuleDialogFragmentArgs by navArgs()
    private var isSaved=false
    private var blockRuleIndex=0L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.fullscreenDialog)
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding= EditBlockRuleDialogFragmentBinding.inflate(inflater)
        preferenceKey= PreferenceKey(requireContext())
        if (!args.isNewOne){
            //saved before so should update record in DB
            isSaved=true
            blockRuleIndex=args.blockRuleIndex
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupInput()
    }

    private fun setupInput() {
        val nameInput=binding.nameInput
        val ruleInput=binding.ruleInput
        val isRegex=binding.isRegex
        val isNotCaseSensitive=binding.isNotCaseSensitive
        val matchEntire=binding.matchEntire
        val blockTargetTextView=binding.blockTargetTextView
        if (!args.isNewOne){
            lifecycleScope.launch {
                viewModel.getBlockRule(args.blockRuleIndex).let {
                    nameInput.setText(it.name)
                    ruleInput.setText(it.rule)
                    isRegex.isChecked=it.isRegex
                    isNotCaseSensitive.isChecked=it.isNotCaseSensitive
                    matchEntire.isChecked=it.matchEntire
                    blockTargetTextView.setText(it.target.targetName,false)
                }
            }
        }

        val blockTargets=enumValues<BlockTarget>().toList()
        val items= blockTargets.map {
            it.targetName
        }
        blockTargetTextView.setAdapter(ArrayAdapter(requireContext(),R.layout.spinner_viewholder,items))
        blockTargetTextView.setText(BlockTarget.TargetAll.targetName,false)
        toolbar.setOnMenuItemClickListener { menuItem->
            when(menuItem.itemId){
                R.id.new_block_rule_menu_save->{
                    val rule=ruleInput.editableText.toString()
                    if (rule.isBlank()){
                        MaterialAlertDialogBuilder(requireContext())
                            .setMessage("??????????????????")
                            .setNegativeButton("??????",null)
                            .show()
                    }else{
                        var name=nameInput.editableText.toString()
                        if (name.isBlank()){
                            name=rule
                        }
                        val target=blockTargets[items.indexOf(blockTargetTextView.text.toString())]
                        lifecycleScope.launch {
                            val blockRule=BlockRule(
                                index = blockRuleIndex,
                                rule=rule,
                                name=name,
                                isRegex = isRegex.isChecked,
                                isNotCaseSensitive = isNotCaseSensitive.isChecked,
                                matchEntire = matchEntire.isChecked,
                                target = target
                            ).also {
                                Log.e(LOG_TAG,it.toString())
                            }
                            if (isSaved){
                                viewModel.updateBlockRule(
                                    blockRule
                                )
                            }else{
                                blockRuleIndex=viewModel.insertBlockRule(
                                    blockRule
                                )
                                isSaved=true

                            }

                        }
                    }


                    true
                }
                else->{
                    false
                }
            }
        }

    }


    private fun setupToolbar() {
        toolbar = binding.newBlockRuleToolbar
        toolbar.inflateMenu(R.menu.edit_block_rule_menu)
        toolbar.setNavigationIcon(R.drawable.ic_round_arrow_back_24)
        toolbar.setNavigationOnClickListener {
            dismiss()
        }

        toolbar.title = "????????????"
    }
}