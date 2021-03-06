package kr.co.seoft.write_post_with_items.ui.wirte

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.recyclerview.widget.RecyclerView
import kr.co.seoft.write_post_with_items.ui.wirte.WriteData.Content
import kr.co.seoft.write_post_with_items.util.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class WriteViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val TOP_TEXT_ID = 0
    }

    val isPublic = SafetyLiveData<Boolean>().apply {
        set(true)
    }

    val editTextsFocusOff = SafetyLiveData<Boolean>()
    val editTextsFocusOffAndStartShuffle = SafetyLiveData<Boolean>()
    val editContent = SafetyLiveData<Content>()

    fun setEditTextsFocusOffAndStartShuffle() {
        editTextsFocusOffAndStartShuffle.set(true)
    }

    val random by lazy { Random(SystemClock.currentThreadTimeMillis()) }

    private val contents = SafetyLiveData<List<Content>>().apply {
        set(listOf(Content.Text(TOP_TEXT_ID, String.EMPTY))) // default : empty text 아이템
    }

    val resultList: LiveData<List<Content>> = Transformations.map(contents) {

        // item 중간중간에 조건에 따라 blank 배치
        val list = it.toMutableList()
        if (list.firstOrNull()?.isShuffle == false) {
            var index = 0
            while (list.size - 1 > index) {
                if (list[index] !is Content.Text && list[index + 1] !is Content.Text) {
                    list.add(index + 1, Content.Blank(random.nextInt(), list[index]))
                    index++
                }
                index++
            }
            if (it.last() !is Content.Text) list.add(Content.Blank(random.nextInt(), list[index]))
        }
        list
    }

    val dragItem = SafetyLiveData<RecyclerView.ViewHolder>()

    val onSetDragItem = fun(vh: RecyclerView.ViewHolder) {
        dragItem.set(vh)
    }

    var isAddedItemToLast = AtomicBoolean(false)

    val isShuffleMode = SafetyLiveData<Boolean>().apply {
        set(false)
    }

    fun getContents() = contents.value ?: emptyList()

    fun togglePublic(view: View) {
        isPublic.set(!(isPublic.value ?: false))
    }

    fun swapList(from: Int, to: Int) {
        contents.set(getContents().toMutableList().apply {
            swap(from, to)
        })
    }

    fun setShuffleMode() {
        contents.set(getContents().map {
            it.setShuffle(true)
        })
        isShuffleMode.set(true)
    }

    fun unsetShuffleMode() {
        contents.set(mergeTextIfExist(getContents().map {
            it.setShuffle(false)
        }))
        isShuffleMode.set(false)
    }

    fun addImageAfterResize(file: File) {
        val uploadFile = ImageUtil.resizeImageToCacheDir(getApplication() as Context, file, SC.MAX_UPLOAD_IMAGE_SIZE)
        contents.set(getContents() + Content.Image(random.nextInt(), uploadFile))
    }

    fun addItemToLast(content: Content) {
        isAddedItemToLast.set(true)
        contents.set(getContents() + content)
    }

    fun updateItem(content: Content) {
        contents.set(getContents().map {
            if (it.id == content.id) {
                content
            } else {
                it
            }
        })
    }

    fun addTextItemInsteadBlank(previousContent: Content, newContent: Content) {
        contents.set(getContents().toMutableList().apply { add(getContents().indexOf(previousContent) + 1, newContent) })
    }

    fun setTextItem(item: Content.Text) {
        contents.set((contents.value ?: emptyList()).map {
            if (it is Content.Text && it.id == item.id) item.copy(isShuffle = it.isShuffle)
            else it
        })
    }

    fun removeItem(removeContent: Content) {
        val removedList = getContents().filter { it != removeContent }
        contents.set(mergeTextIfExist(removedList))
    }

    // 연속 Text아이템 일 경우 중간에 개행을 두고 하나로 합치는 로직
    private tailrec fun mergeTextIfExist(curContents: List<Content>): List<Content> {
        for (i in 0 until curContents.size - 1) {
            if (curContents[i] is Content.Text && curContents[i + 1] is Content.Text) {
                return mergeTextIfExist(curContents
                    .mapIndexed { index, content ->
                        if (index == i) {
                            val previousText = content as Content.Text
                            val nextText = curContents[i + 1] as Content.Text
                            previousText.copy(text = "${previousText.text}\n${nextText.text}")
                        } else {
                            content
                        }
                    }
                    .filterIndexed { index, _ -> index != i + 1 })
            }
        }
        return curContents
    }

    fun updateContentTextItem(view: View, hasFocus: Boolean, contentText: Content.Text) {
        if (view !is EditText) return
        if (!hasFocus) {
            // 첫 index 의 Content.Text 아이템 경우 제외될때 사이드 이펙트 방지로 예외처리
            if (view.text.toString().isEmpty() && contentText.id != TOP_TEXT_ID) removeItem(contentText)
            else setTextItem(contentText.copy(text = view.text.toString()))
        }
    }

    fun updateContentBlankToContentText(view: View, hasFocus: Boolean, contentBlank: Content.Blank) {
        if (view !is EditText) return
        if (hasFocus) {
            view.layoutParams.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
        } else if (!hasFocus && !view.text.toString().isBlank()) {
            addTextItemInsteadBlank(
                contentBlank.previousContent,
                Content.Text(random.nextInt(), view.text.toString())
            )
        } else {
            view.layoutParams.height = 30.dpToPx()
        }
        view.requestLayout()
    }

}