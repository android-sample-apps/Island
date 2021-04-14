package com.simsim.island.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.simsim.island.MainActivity
import com.simsim.island.R
import com.simsim.island.adapter.DrawerRecyclerViewAdapter
import com.simsim.island.adapter.MainRecyclerViewAdapter
import com.simsim.island.databinding.MainFragmentBinding
import com.simsim.island.util.LOG_TAG
import com.simsim.island.util.OnSwipeListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

@AndroidEntryPoint
class MainFragment : Fragment() {
    private lateinit var binding: MainFragmentBinding
    internal lateinit var adapter: MainRecyclerViewAdapter
    private lateinit var drawAdapter: DrawerRecyclerViewAdapter
    internal lateinit var layoutManager: LinearLayoutManager
    private lateinit var mainFlowJob: Job
    private var loadingImageId =R.drawable.ic_blue_ocean1




    private val viewModel: MainViewModel by activityViewModels()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MainFragmentBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        requestPermissions()
        setupFAB()
        randomRefreshImage()
        return binding.root
    }

    private fun randomRefreshImage() {
        lifecycleScope.launch {
            while (true) {
                delay(2000)
                if (isDetached) {
                    break
                }
                loadingImageId = when ((1..12).random()) {
                    1 -> R.drawable.ic_blue_ocean1
                    2 -> R.drawable.ic_blue_ocean2
                    3 -> R.drawable.ic_blue_ocean3
                    4 -> R.drawable.ic_blue_ocean4
                    5 -> R.drawable.ic_blue_ocean5
                    6 -> R.drawable.ic_blue_ocean6
                    7 -> R.drawable.ic_blue_ocean7
                    8 -> R.drawable.ic_blue_ocean8
                    9 -> R.drawable.ic_blue_ocean9
                    10 -> R.drawable.ic_blue_ocean10
                    11 -> R.drawable.ic_blue_ocean11
                    12 -> R.drawable.ic_blue_ocean12
                    else -> R.drawable.image_load_failed
                }
                Log.e("Simsim", "get loading image id :$loadingImageId")
            }
        }
    }

    private fun requestPermissions() {
        (requireActivity() as MainActivity).requestPermission.launch(
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            )
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFAB() {
        binding.fabAdd.setOnTouchListener(OnSwipeListener(
            requireContext(),
            onSwipeBottom = {
                layoutManager.scrollToPosition(0)
            },
            onSwipeLeft = {

                adapter.refresh()
            },
            onSwipeRight = {
                Log.e(LOG_TAG, "swipe left")
                binding.drawerLayout.openDrawer(binding.navigationView)
            },
            onSwipeTop = {}
        ))
        binding.fabAdd.setOnClickListener {
            newThread()
        }
    }

    private fun newThread() {
        val action=MainFragmentDirections.actionGlobalNewDraftFragment(target = "section",keyWord = viewModel.currentSectionName?:"")
        findNavController().navigate(action)
        viewModel.isMainFragment.value = false
    }

    private fun setupDrawerSections() {
        lifecycleScope.launch {
            viewModel.database.sectionDao().getAllSection().map {
                it.map { section ->
                    section.sectionName
                }
            }.distinctUntilChanged()
                .collect { sectionList ->
                    val drawerLayout = binding.drawerLayout
                    val drawer = binding.navigationView
                    val drawerMenu = binding.navigationView.menu
                    sectionList.forEach { sectionName ->
                        drawerMenu.add(sectionName)
                    }
                    drawer.setNavigationItemSelectedListener { menuItem ->
                        drawer.setCheckedItem(menuItem)
                        drawerLayout.close()
                        binding.mainToolbar.title = menuItem.title
                        viewModel.setMainFlow(menuItem.title.toString())
                        observeMainFlow()
                        true
                    }

                }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbar()
        initialMainFlow()
        setupRecyclerView()
        setupSwipeRefresh()
        setupDrawerSections()
        observingDataChange()
        handleLaunchLoading()
        setupChips()

        super.onViewCreated(view, savedInstanceState)
    }

    private fun setupChips() {
        binding.mainFragmentChips.setOnCheckedChangeListener { group, checkedId ->
    //            group.check(checkedId)
            val chip = group.findViewById<Chip>(checkedId)
            Log.e(LOG_TAG, "chip tapped:${chip.text}")

            viewModel.setMainFlow(chip.text.toString())
            observeMainFlow()

        }
    }


    private fun observingDataChange() {

        viewModel.isMainFragment.observe(viewLifecycleOwner) {
            binding.mainRecyclerView.suppressLayout(!it)
        }
    }

    private fun initialMainFlow() {
        lifecycleScope.launch {
            viewModel.database.sectionDao().getAllSection().take(1).collect {
                val sectionName = it[0].sectionName
                Log.e(LOG_TAG, "first sectionName:$sectionName")
                viewModel.setMainFlow(sectionName)
                observeMainFlow()
                binding.mainToolbar.title = sectionName
            }
        }
    }

    private fun observeMainFlow() {
        layoutManager.scrollToPosition(0)
        try {
            mainFlowJob.cancel()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "mainFlowJob:${e.stackTraceToString()}")
        }
        mainFlowJob = lifecycleScope.launch {
            viewModel.mainFlow.collectLatest {
                adapter.submitData(it)
            }
        }
        mainFlowJob.start()
    }

    private fun handleLaunchLoading() {
        lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadStates ->
                if (loadStates.refresh is LoadState.Loading) {
                    binding.mainProgressIndicator.visibility = View.VISIBLE
                    binding.loadingImage.visibility = View.VISIBLE
                    binding.fabAdd.visibility = View.INVISIBLE

                    Glide.with(this@MainFragment).load(loadingImageId).into(binding.loadingImage)

                } else {
                    binding.mainProgressIndicator.visibility = View.GONE
                    binding.indicatorTextview.visibility = View.GONE
                    binding.loadingImage.visibility = View.GONE
//                    binding.chipScrollView.visibility = View.VISIBLE
                    //todo
                    binding.fabAdd.visibility = View.VISIBLE
                }
                if (loadStates.refresh is LoadState.Error) {
                    binding.indicatorTextview.isVisible = true
                    binding.indicatorTextview.text = "错误，点击重试!"
                    binding.indicatorTextview.setOnClickListener {
                        adapter.retry()
                    }
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        val swipeRefreshLayout = binding.swipeFreshLayout
        swipeRefreshLayout.setOnRefreshListener {
            Log.e("Simsim", "main recycler view refresh by swipeRefreshLayout")
            adapter.refresh()
            if (swipeRefreshLayout.isRefreshing) {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun setupDrawerRecyclerView() {


    }

    private fun setupRecyclerView() {
        adapter = MainRecyclerViewAdapter(this, { imageUrl ->
            val action = MainFragmentDirections.actionGlobalImageDetailFragment(imageUrl)
            findNavController().navigate(action)
        }) { poThread ->
            viewModel.currentPoThread = poThread
            val action = MainFragmentDirections.actionMainFragmentToDetailDialogFragment(
                poThread.uid,
                poThread.threadId
            )
            findNavController().navigate(action)
            viewModel.setDetailFlow(poThread)
            viewModel.isMainFragment.value = false
        }
        binding.mainRecyclerView.adapter = adapter
//            .withLoadStateFooter(MainLoadStateAdapter(adapter::retry))
        layoutManager = LinearLayoutManager(context)
        binding.mainRecyclerView.layoutManager = layoutManager
    }


    private fun setupToolbar() {
        val toolbar = binding.mainToolbar
        toolbar.setNavigationIcon(R.drawable.ic_round_menu_24)
        toolbar.setNavigationOnClickListener {
            if (binding.drawerLayout.isDrawerOpen(binding.navigationView)) {
                binding.drawerLayout.closeDrawer(binding.navigationView)
            } else {
                binding.drawerLayout.openDrawer(binding.navigationView)
            }
        }
        binding.navigationView.setNavigationItemSelectedListener {
            it.isChecked = true
            binding.drawerLayout.close()
            true
        }
//        toolbar.title = viewModel.currentSection.value


        toolbar.inflateMenu(R.menu.main_toolbar_menu)
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
//                R.id.menu_item_refresh -> {
////                    adapter.refresh()
////                    layoutManager.scrollToPosition(0)
//                    Log.e("Simsim", "refresh item pressed")
//                    true
//                }
//            R.id.menu_item_search->{}
                else -> false
            }
        }
    }

}