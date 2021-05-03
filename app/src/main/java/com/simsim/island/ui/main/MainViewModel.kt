package com.simsim.island.ui.main

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import com.bumptech.glide.Glide
import com.simsim.island.database.IslandDatabase
import com.simsim.island.model.*
import com.simsim.island.paging.DetailRemoteMediator
import com.simsim.island.paging.MainRemoteMediator
import com.simsim.island.repository.AislandRepo
import com.simsim.island.service.AislandNetworkService
import com.simsim.island.util.LOG_TAG
import com.simsim.island.util.toSavedPoThread
import com.simsim.island.util.toSavedReplyThread
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.InputStream
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repo: AislandRepo,
    val database: IslandDatabase,
    val networkService: AislandNetworkService
) : AndroidViewModel(application) {
    //    var randomLoadingImage: Int = R.drawable.ic_blue_ocean1
    var currentPoThread: PoThread? = null
    var currentSectionId: String = ""
    var currentSectionName: String? = null
    val savedInstanceState = MutableLiveData<Long>()
    var currentReplyThreads = mutableListOf<ReplyThread>()
    var sectionList = database.sectionDao().getAllSection()
    var mainFlow: Flow<PagingData<PoThread>> = emptyFlow()
    var currentPoThreadList = MutableLiveData<List<PoThread>>()
    var detailFlow: Flow<PagingData<ReplyThread>> = emptyFlow()
    val isMainFragment = MutableLiveData(true)
    var windowHeight = 1
    var actionBarHeight = 1
    private val glide: Glide by lazy { Glide.get(application.applicationContext) }
    val loginCookies = MutableLiveData<Map<String, String>>()
    val loadCookieFromUserSystemSuccess = MutableLiveData<Boolean>()
    private var blockRules: List<BlockRule> = mutableListOf()
    var cameraTakePictureSuccess = MutableLiveData<Boolean>()
    var picturePath = MutableLiveData<String>()
    var pictureUri = MutableLiveData<Uri>()
    val drawPicture = MutableLiveData<Bitmap>()
    var shouldTakePicture = MutableLiveData<String>()
    var successReply = MutableLiveData<Boolean>()
    var successPost = MutableLiveData<Boolean>()
    val QRcodeResult = MutableLiveData<String>()

    init {
        doUpdate()
        viewModelScope.launch {
            blockRules = database.blockRuleDao().getAllBlockRules()
            database.blockRuleDao().getAllBlockRulesFlow().collectLatest {
                blockRules = it
            }
        }

    }

    fun doUpdate() {
        viewModelScope.launch {
            val record = database.recordDao().getRecord()
            if (record == null) {
                getSectionList()
                database.recordDao()
                    .insertRecord(UpdateRecord(lastUpdateTime = LocalDateTime.now()))
            } else {
                if (Duration.between(record.lastUpdateTime, LocalDateTime.now()).toDays() >= 30) {
                    getSectionList()
                }
            }
        }
    }


    private suspend fun getSectionList() {
        val pair = repo.getSectionAndEmojiList()
        val sectionList = pair.first
        val emojiList = pair.second
        database.sectionDao().insertAllSection(sectionList)
        database.emojiDao().insertAllEmojis(emojiList)
    }


    @OptIn(ExperimentalPagingApi::class)
    fun setMainFlow(sectionName: String, sectionUrl: String): Flow<PagingData<PoThread>> {
        mainFlow = Pager(
            PagingConfig(
                pageSize = 20,
                prefetchDistance = 20,
                enablePlaceholders = true,
                maxSize = 9999,
                initialLoadSize = 20,
            ),
            remoteMediator = MainRemoteMediator(
                service = networkService,
                sectionName = sectionName,
                sectionUrl = sectionUrl,
                database = database
            )
        ) {
            database.threadDao().getAllPoThreadsBySection(sectionName)
        }.flow
            .map { pagingData ->
                val list = mutableListOf<PoThread>()
                pagingData.map {
                    list.add(it)
                    currentSectionName = it.section
                    it
                }
                currentPoThreadList.value = list
                pagingData.filter { poThread ->
                    //return false if thread match any rule
                    !blockRules
                        .map { rule ->
                            ifThreadMatchRules(poThread, rule)
                        }
                        .contains(true)
                }
            }
            .cachedIn(viewModelScope)
        return mainFlow
    }

    private fun ifThreadMatchRules(poThread: PoThread, blockRule: BlockRule): Boolean {
        if (!blockRule.isEnable) {
            return false
        }
        val options = mutableSetOf<RegexOption>()
        blockRule.run {
            if (!isRegex) {
                options.add(RegexOption.LITERAL)
            }
            if (isNotCaseSensitive) {
                options.add(RegexOption.IGNORE_CASE)
            }
        }
        val ruleRegex = try {
            Regex(blockRule.rule,options)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "transform rule to regex failed:${e.stackTraceToString()}")
            Regex.fromLiteral(blockRule.rule)
        }

        return when (blockRule.target) {
            BlockTarget.TargetUid -> {
                ifMatchEntire(ruleRegex,poThread.uid,blockRule.matchEntire)
            }
            BlockTarget.TargetContent -> {
                ifMatchEntire(ruleRegex,poThread.content,blockRule.matchEntire)
            }
            BlockTarget.TargetSection -> {
                ifMatchEntire(ruleRegex,poThread.section,blockRule.matchEntire)
            }
            BlockTarget.TargetThreadId -> {
                ifMatchEntire(ruleRegex,poThread.threadId.toString(),blockRule.matchEntire)
            }
            else -> {
                listOf(
                    ifMatchEntire(ruleRegex,poThread.uid,blockRule.matchEntire),
                    ifMatchEntire(ruleRegex,poThread.content,blockRule.matchEntire),
                    ifMatchEntire(ruleRegex,poThread.section,blockRule.matchEntire),
                    ifMatchEntire(ruleRegex,poThread.threadId.toString(),blockRule.matchEntire)
                ).contains(true)

            }
        }
    }
    private fun ifMatchEntire(re:Regex,input:String,matchEntire:Boolean):Boolean=if (matchEntire) re.matches(input) else re.containsMatchIn(input)



    @OptIn(ExperimentalPagingApi::class)
    fun setDetailFlow(poThreadId: Long): Flow<PagingData<ReplyThread>> {
        currentReplyThreads = mutableListOf()
        detailFlow = Pager(
            PagingConfig(
                pageSize = 20,
                prefetchDistance = 20,
                enablePlaceholders = true,
                maxSize = 999999,
                initialLoadSize = 20
            ),
            remoteMediator = DetailRemoteMediator(
                service = networkService,
                poThreadId = poThreadId,
                database = database
            )
        ) {
            database.threadDao().getAllReplyThreadsPagingSource(poThreadId = poThreadId)
        }.flow.map { pagingData ->
            pagingData.map {
                currentReplyThreads.add(it)
                it
            }
        }
            .cachedIn(viewModelScope)
        return detailFlow
    }


    fun starPoThread(poThreadId: Long) {
        viewModelScope.launch {
            val poThread = database.threadDao().getPoThread(poThreadId)
            val replyThreads = database.threadDao().getAllReplyThreads(poThreadId)
            poThread?.let {
                val staredPoThreads = poThread.toSavedPoThread()
                database.threadDao().insertSavedPoThread(staredPoThreads)
                database.threadDao().insertAllSavedReplyThread(
                    replyThreads.map {
                        it.toSavedReplyThread()
                    }
                )
            }
        }
    }

    fun doWhenDestroy() {
        viewModelScope.launch {
            CoroutineScope(Dispatchers.IO).launch {
                Log.e(LOG_TAG, "vm do destroy work")
                glide.clearDiskCache()
//                database.threadDao().clearAllPoThread()
            }
            CoroutineScope(Dispatchers.Main).launch {
                Log.e(LOG_TAG, "vm do destroy work")
                glide.clearMemory()
            }

//            database.keyDao().clearMainKeys()
//            database.keyDao().clearReplyThreadsKeys()
        }
    }

    fun doPost(
        cookie: String,
//        poThreadId: Long,
        content: String,
        image: InputStream?,
        imageType: String?,
        imageName: String?,
        fId: String,
        waterMark: Boolean = false,
        name: String = "",
        email: String = "",
        title: String = ""
    ) {
        viewModelScope.launch {
            successPost.value = networkService.doPost(
                cookie,
//                poThreadId,
                content,
                image,
                imageType,
                imageName,
                fId,
                waterMark,
                name,
                email,
                title,
            )
        }
    }

    fun doReply(
        cookie: String,
        poThreadId: Long,
        content: String,
        image: InputStream?,
        imageType: String?,
        imageName: String?,
        waterMark: Boolean = false,
        name: String = "",
        email: String = "",
        title: String = ""
    ) {
        viewModelScope.launch {
            successReply.value = networkService.doReply(
                cookie,
                poThreadId,
                content,
                image,
                imageType,
                imageName,
                waterMark,
                name,
                email,
                title,
            )
        }
    }

    fun getCookies(cookieMap: Map<String, String>) {
        viewModelScope.launch {
            val cookieList = networkService.getCookies(cookieMap)
            database.cookieDao().insertAllCookies(cookieList)
            loadCookieFromUserSystemSuccess.value = true
        }
    }

    //
//
//    fun setMainFlow(section: String): Flow<PagingData<PoThread>> {
//        mainFlow= Pager(
//        PagingConfig(
//            pageSize = 50,
//            enablePlaceholders = false,
//            maxSize = 300,
//            initialLoadSize = 100
//        )
//    ){
//        MainPaging(networkService,section = section)
//    }.flow.cachedIn(viewModelScope)
//        return mainFlow
//    }
//    fun setDetailFlow(poThread: PoThread): Flow<PagingData<ReplyThread>> {
//        detailFlow = Pager(
//            PagingConfig(
//                pageSize = 100,
//                prefetchDistance = 100,
//                enablePlaceholders = false,
//                maxSize = 300,
//                initialLoadSize = 200
//            )
//        ) {
//            DetailPaging(networkService, poThread)
//        }.flow.cachedIn(viewModelScope)
//        return detailFlow
//    }


}