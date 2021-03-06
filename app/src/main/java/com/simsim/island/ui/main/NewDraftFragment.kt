package com.simsim.island.ui.main

//import com.github.dhaval2404.imagepicker.ImagePicker
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.simsim.island.MainActivity
import com.simsim.island.R
import com.simsim.island.dataStore
import com.simsim.island.databinding.NewDraftFragmentBinding
import com.simsim.island.dp2PxScale
import com.simsim.island.preferenceKey.PreferenceKey
import com.simsim.island.util.LOG_TAG
import com.simsim.island.util.TARGET_SECTION
import com.simsim.island.util.TARGET_THREAD
import com.simsim.island.util.toggleVisibility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.InputStream

@AndroidEntryPoint
class NewDraftFragment : DialogFragment() {
    private val viewModel:MainViewModel by activityViewModels()
    private lateinit var binding:NewDraftFragmentBinding
    private val args:NewDraftFragmentArgs by navArgs()
    private var fId:String=""
    private var postImage: InputStream? = null
    private var imageType:String?=null
    private var imageName:String?=null
    private lateinit var fab:FloatingActionButton
    private lateinit var preferenceKey:PreferenceKey
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.fullscreenDialog)
    }
    private lateinit var emojiList:List<String>


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding= NewDraftFragmentBinding.inflate(inflater, container, false)
        fab=binding.fabSend
        preferenceKey= PreferenceKey(requireContext())
        getEmojiList()
        fId=args.fId
        setupPictureTaking()
        setupInfoFillArea()
        return binding.root
    }





    private fun setupInfoFillArea() {
        lifecycleScope.launch {
            launch {
                insertText(0,args.prefillText)

            }
            launch{
                binding.expandButton.setOnClickListener {
                    binding.emailTitleTextView.toggleVisibility()
                    binding.emailValueEditText.toggleVisibility()
                    binding.nameTitleTextView.toggleVisibility()
                    binding.nameValueEditText.toggleVisibility()
                    binding.titleTitleTextView.toggleVisibility()
                    binding.titleValueEditText.toggleVisibility()
                    if (binding.emailTitleTextView.isVisible) {
                        binding.expandButton.setImageResource(R.drawable.ic_round_keyboard_arrow_up_16)
                    } else {
                        binding.expandButton.setImageResource(R.drawable.ic_round_keyboard_arrow_down_16)
                    }
                }
                val target = binding.targetValueTextView
                binding.targetTitleTextView.text =
                    if (args.target == TARGET_SECTION) "?????????" else "?????????"
                if (args.target == TARGET_SECTION) {
                    val sectionMap = hashMapOf<String, String>()
                    val sectionMapReversed = hashMapOf<String, String>()
                    var sectionArray: Array<String>
                    viewModel.getAllSectionFlow().collectLatest {
                        val list = it.filter { section ->
                            section.group != "?????????"
                        }
                        list.forEach { section ->
                            sectionMap[section.fId] = section.sectionName
                            sectionMapReversed[section.sectionName] = section.fId
                        }
                        sectionArray = list.map { it.sectionName }.toTypedArray()
                        if (fId.isNotBlank()) {
                            target.text = sectionMap[fId]
                                ?: throw IllegalArgumentException("can not find section name in map")
                        } else {
                            target.text = list[0].sectionName
                            fId = sectionMapReversed[list[0].sectionName]
                                ?: throw IllegalArgumentException("can not find section fid in map")
                        }
                        target.setOnClickListener {
                            MaterialAlertDialogBuilder(requireContext())
                                .setItems(sectionArray) { dialog: DialogInterface, position: Int ->
                                    val sectionNameSelected = sectionArray[position]
                                    target.text = sectionNameSelected
                                    fId = sectionMapReversed[sectionNameSelected]
                                        ?: throw IllegalArgumentException("can not find section fid in map")
                                    dialog.dismiss()
                                }
                                .show()
                        }
                    }
                } else {
                    target.text = args.threadId.toString()
                }
            }

        }
    }

    private fun insertText(start: Int=binding.newInputContent.selectionStart, text: String) {
        if (binding.newInputContent.text==null){
            binding.newInputContent.setText("")
        }
        binding.newInputContent.text?.let {
            it.insert(start,text)
        }
    }

    private fun getEmojiList() {
        lifecycleScope.launch {
            viewModel.getAllEmojisFlow().map { emojis ->
                emojis.map { emoji ->
                    emoji.emoji
                }
            }.collectLatest {
                emojiList = it
            }
        }
    }

//    private fun showEmojiBottomSheet() {
//        val gridView=binding.emojiGridView
//        val gridList:List<String> = if (this::emojiList.isInitialized) emojiList else mutableListOf()
//        gridView.adapter=ArrayAdapter(requireContext(),R.layout.emoji_viewholder,gridList)
//        gridView.numColumns=3
//        gridView.setOnItemClickListener { parent, view, position, id ->
//            view as TextView
//            val emojiString=view.text
//            val input=binding.newInputContent
//            val start= input.selectionStart.coerceAtLeast(0)
//            val end= input.selectionEnd.coerceAtLeast(0)
//            Log.e(LOG_TAG,"start:$start,end:$end")
//            Log.e(LOG_TAG,"inputText:${input.text}")
//            input.text?.let { inputText->
//                inputText.replace(start,end,emojiString)
//            }
//        }
//    }

    override fun onDetach() {
        super.onDetach()
        when(args.target){
            TARGET_THREAD->{viewModel.isMainFragment.value=false}
            TARGET_SECTION->{viewModel.isMainFragment.value=true}
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        viewModel.pictureUri.removeObservers(viewLifecycleOwner)
        viewModel.cameraTakePictureSuccess.removeObservers(viewLifecycleOwner)
        viewModel.pictureUri.value=null
        super.onDismiss(dialog)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupFAB()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFAB() {
        lifecycleScope.launch {
            requireContext().dataStore.data.collectLatest { settings ->
                fab.setOnClickListener {
                    send()
                    Log.e(LOG_TAG, "BlockRuleManageDialogFragment fab clicked")
                }
                settings[booleanPreferencesKey(preferenceKey.enableFabKey)]?.let { enable ->
                    binding.newDraftDialogToolbar.menu.findItem(R.id.draft_menu_send).isVisible =
                        !enable
                    fab.isVisible = enable
                }
                settings[booleanPreferencesKey(preferenceKey.fabDefaultSizeKey)]?.let { setSizeDefault ->
                    if (setSizeDefault) {
                        fab.customSize = FloatingActionButton.NO_CUSTOM_SIZE
                        fab.size = FloatingActionButton.SIZE_AUTO
                    } else {
                        settings[intPreferencesKey(preferenceKey.fabSizeSeekBarKey)]?.let { fabCustomSize ->
                            fab.customSize = (fabCustomSize * requireContext().dp2PxScale()).toInt()
                        }
                    }

                }
                (settings[booleanPreferencesKey(preferenceKey.fabPlaceRightKey)]
                    ?: true).let { placeRight ->
                    val sideMargin =
                        settings[intPreferencesKey(preferenceKey.fabSideMarginKey)] ?: 0
                    val bottomMargin =
                        settings[intPreferencesKey(preferenceKey.fabBottomMarginKey)] ?: 0
                    val layoutParams = fab.layoutParams as CoordinatorLayout.LayoutParams
                    layoutParams.bottomMargin =
                        ((30 + bottomMargin) * requireContext().dp2PxScale()).toInt()
                    if (placeRight) {
                        layoutParams.gravity = Gravity.BOTTOM or Gravity.RIGHT
                        layoutParams.rightMargin =
                            ((30 + sideMargin) * requireContext().dp2PxScale()).toInt()
                        layoutParams.leftMargin = 0
                    } else {
                        layoutParams.gravity = Gravity.BOTTOM or Gravity.LEFT
                        layoutParams.leftMargin =
                            ((30 + sideMargin) * requireContext().dp2PxScale()).toInt()
                        layoutParams.rightMargin = 0
                    }
                    fab.layoutParams = layoutParams
                    fab.requestLayout()
                }
            }

        }

    }

    private fun setupToolbar() {
        val toolbar = binding.newDraftDialogToolbar
        toolbar.setNavigationIcon(R.drawable.ic_round_arrow_back_24)
        toolbar.title = if (args.target== TARGET_THREAD) "??????" else "??????"
        toolbar.setNavigationOnClickListener {
            dismiss()
        }
        toolbar.inflateMenu(R.menu.new_draft_tollbar_menu)
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.draft_menu_send -> {

                    send()
                    true
                }
                R.id.draft_menu_image_pick -> {
                    Log.e(LOG_TAG, "draft_menu_image_pick")
                    MaterialAlertDialogBuilder(requireContext()).setTitle("??????")
                        .setNegativeButton("??????") { dialogInterface: DialogInterface, buttonId: Int ->
                            dialogInterface.dismiss()
                        }.setItems(
                            arrayOf(
                                "??????",
                                "??????",
                                "??????"
                            )
                        ) { dialogInterface: DialogInterface, itemIndex: Int ->
                            dialogInterface.dismiss()
                            when (itemIndex) {
                                0 -> {
                                    val uri=takePictureFromCamera()
//                                    viewModel.cameraTakePictureSuccess.observe(viewLifecycleOwner){ success ->
//                                        if (success){
//                                            postImage=requireActivity().contentResolver.openInputStream(uri)?.buffered()
//                                            imageType=requireActivity().contentResolver.getType(uri)
//                                            imageName=DocumentFile.fromSingleUri(requireContext(),uri)?.name
//                                        }
//                                    }
                                }
                                1 -> {
                                    takePictureFromGallery()
//                                    viewModel.pictureUri.observe(viewLifecycleOwner){uri->
//                                        postImage=requireActivity().contentResolver.openInputStream(uri)?.buffered()
//                                        imageType=requireActivity().contentResolver.getType(uri)
//                                        imageName=DocumentFile.fromSingleUri(requireContext(),uri)?.name
//                                    }
                                }
                                2->{
                                    takePictureFromNewDraw()
//                                    viewModel.pictureUri.observe(viewLifecycleOwner){uri->
//                                        postImage=requireActivity().contentResolver.openInputStream(uri)?.buffered()
//                                        imageType=requireActivity().contentResolver.getType(uri)
//                                        imageName=DocumentFile.fromSingleUri(requireContext(),uri)?.name
//                                    }
                                }
                            }
                        }.show()
//                    ImagePicker.with(this).crop().compress(2048).start()
                    true
                }
                R.id.draft_emoji_pick -> {
                    Log.e(LOG_TAG,"draft_emoji_pick")
                    showEmojiDialog()
                    true
                }
                R.id.draft_menu_hide_tag->{
                    insertText(text = "[h][/h]")
                    binding.newInputContent.apply {
                        setSelection(selectionStart-4)
                    }
                    true
                }
                else -> {
                    false
                }
            }
        }
    }

    private fun send() {
        val title = binding.titleValueEditText.text.toString()
        val email = binding.emailValueEditText.text.toString()
        val name = binding.nameValueEditText.text.toString()
        when (args.target) {
            TARGET_THREAD -> {
                viewModel.doReply(
    //                            cookie = "%D8%A9%AE%99%1BKc%BC%16iDt%94%7B%DDm%86%15%81%AA%8Ct%3E%BB",
                    poThreadId = args.threadId,
                    content = binding.newInputContent.text.toString(),
                    image = postImage,
                    imageType = imageType,
                    imageName = imageName,
                    waterMark = binding.toggleButton.isChecked,
                    title = title,
                    email = email,
                    name = name,
                )
            }
            TARGET_SECTION -> {
                viewModel.doPost(
    //                            cookie = "%D8%A9%AE%99%1BKc%BC%16iDt%94%7B%DDm%86%15%81%AA%8Ct%3E%BB",
    //                            poThreadId = args.threadId,
                    content = binding.newInputContent.text.toString(),
                    image = postImage,
                    imageType = imageType,
                    imageName = imageName,
                    waterMark = binding.toggleButton.isChecked,
                    fId = fId,
                    title = title,
                    email = email,
                    name = name,
                )
            }
        }
        dismiss()
        Log.e(LOG_TAG, "draft_menu_send")
    }


    private fun showEmojiDialog() {
        val gridView=GridView(requireContext())
        val gridList:List<String> = if (this::emojiList.isInitialized) emojiList else mutableListOf()
        gridView.adapter=ArrayAdapter(requireContext(),R.layout.emoji_viewholder,gridList)
        gridView.numColumns=3
        gridView.setOnItemClickListener { parent, view, position, id ->
            view as TextView
            val emojiString=view.text
            val input=binding.newInputContent
            val start= input.selectionStart.coerceAtLeast(0)
            val end= input.selectionEnd.coerceAtLeast(0)
            Log.e(LOG_TAG,"start:$start,end:$end")
            Log.e(LOG_TAG,"inputText:${input.text}")
            input.text?.let { inputText->
                inputText.replace(start,end,emojiString)
            }
        }
        MaterialAlertDialogBuilder(requireContext()).setView(gridView).show()

    }

    private fun setupPictureTaking() {

        viewModel.pictureUri.observe(viewLifecycleOwner){photoUri->
            photoUri?.let {
                postImage=requireActivity().contentResolver.openInputStream(photoUri)?.buffered()
                imageType=requireActivity().contentResolver.getType(photoUri)
                imageName=DocumentFile.fromSingleUri(requireContext(),photoUri)?.name
                Glide.with(this).load(photoUri).into(binding.newImagePosted)

                binding.cancleButton.setOnClickListener {
                    viewModel.pictureUri.value=null
                    binding.postViewLauout.visibility = View.GONE
                    Glide.with(this).clear(binding.newImagePosted)
                }
                binding.newImagePosted.setOnClickListener {
                    val action=NewDraftFragmentDirections.actionGlobalImageDetailFragment(imageUrl = photoUri.toString(),isURI = true)
                    findNavController().navigate(action)
                }
                binding.postViewLauout.isVisible = true
                viewModel.pictureUri.value=null
            }
        }
    }

    private fun takePictureFromGallery() {
        viewModel.shouldTakePicture.value="gallery"
        val activity=requireActivity() as MainActivity
        activity.pickPicture.launch("image/*")
//        binding.postViewLauout.isVisible = true
    }

    private fun takePictureFromCamera()  :Uri{
        viewModel.shouldTakePicture.value="camera"
        val activity=requireActivity() as MainActivity
        val photoUri=activity.createImageFile()
        activity.takePicture.launch(photoUri)
        viewModel.cameraTakePictureSuccess.observe(viewLifecycleOwner){ success ->
            if (success){
                viewModel.pictureUri.value=photoUri
//                binding.postViewLauout.isVisible = true
            }

        }
        return photoUri
    }
    private fun takePictureFromNewDraw(){
        val activity=requireActivity() as MainActivity
        activity.newDraw.launch(Unit)

    }
}