package com.vmloft.develop.app.template.ui.post

import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager

import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter

import com.drakeet.multitype.MultiTypeAdapter

import com.vmloft.develop.app.template.R
import com.vmloft.develop.app.template.common.Constants
import com.vmloft.develop.library.data.common.SignManager
import com.vmloft.develop.app.template.databinding.ActivityPostDetailsBinding
import com.vmloft.develop.library.data.bean.Comment
import com.vmloft.develop.library.data.bean.Post
import com.vmloft.develop.library.data.bean.User
import com.vmloft.develop.library.data.viewmodel.PostViewModel
import com.vmloft.develop.app.template.router.AppRouter
import com.vmloft.develop.app.template.ui.widget.ContentDislikeDialog
import com.vmloft.develop.library.base.BItemDelegate
import com.vmloft.develop.library.base.BVMActivity
import com.vmloft.develop.library.base.BViewModel
import com.vmloft.develop.library.base.common.CConstants
import com.vmloft.develop.library.base.common.CSPManager
import com.vmloft.develop.library.base.event.LDEventBus
import com.vmloft.develop.library.base.router.CRouter
import com.vmloft.develop.library.base.widget.CommonDialog
import com.vmloft.develop.library.request.RPaging
import com.vmloft.develop.library.tools.utils.VMDimen
import com.vmloft.develop.library.tools.utils.VMStr
import com.vmloft.develop.library.tools.utils.VMSystem
import com.vmloft.develop.library.tools.widget.guide.GuideItem
import com.vmloft.develop.library.tools.widget.guide.VMGuide
import com.vmloft.develop.library.tools.widget.guide.VMGuideView
import com.vmloft.develop.library.tools.widget.guide.VMShape
import org.koin.androidx.viewmodel.ext.android.getViewModel


/**
 * Create by lzan13 on 2021/05/20 22:56
 * 描述：帖子详情界面
 */
@Route(path = AppRouter.appPostDetails)
class PostDetailsActivity : BVMActivity<ActivityPostDetailsBinding, PostViewModel>() {

    private lateinit var user: User

    private var page = CConstants.defaultPage

    @Autowired(name = CRouter.paramsObj0)
    lateinit var post: Post

    // 长按弹出菜单
    private var currComment: Comment? = null
    private var currPosition = -1

    // 适配器
    private val mAdapter by lazy(LazyThreadSafetyMode.NONE) { MultiTypeAdapter() }
    private val mItems = ArrayList<Any>()

    override fun initVB() = ActivityPostDetailsBinding.inflate(layoutInflater)

    override fun initVM(): PostViewModel = getViewModel()

    override fun initUI() {
        super.initUI()

        setTopTitle(R.string.post_detail_title)

        mBinding.commentIV.setOnClickListener {
            if (user.avatar.isNullOrEmpty() || user.nickname.isNullOrEmpty()) {
                CRouter.go(AppRouter.appPersonalInfoGuide)
            } else {
                CRouter.go(AppRouter.appPostComment, str0 = post.id)
            }
        }

        initRecyclerView()

        // 监听评论信息添加
        LDEventBus.observe(this, Constants.Event.createComment, Comment::class.java) {
            mItems.add(1, it)
            mAdapter.notifyItemInserted(1)
        }
    }

    override fun initData() {
        ARouter.getInstance().inject(this)

        user = SignManager.getCurrUser()

        mItems.add(post)
        mAdapter.notifyDataSetChanged()

        mViewModel.postInfo(post.id)
    }

    /**
     * 初始化列表
     */
    private fun initRecyclerView() {
        mAdapter.register(ItemPostDetailsHeaderDelegate(object : ItemPostDetailsHeaderDelegate.PostItemListener {
            override fun onClick(v: View, data: Post, position: Int) {}
            override fun onLikeClick(item: Post, position: Int) {
                likePost()
            }

            override fun onReportClick(item: Post, position: Int) {
                if (user.id == post.owner.id) {
                    removePostDialog()
                } else {
                    reportPostDialog()
                }
            }
        }, object : BItemDelegate.BItemLongListener<Post> {
            override fun onLongClick(v: View, event: MotionEvent, data: Post, position: Int): Boolean {
                if (user.id != post.owner.id) {
                    reportPostDialog()
                }
                return true
            }
        }))
        mAdapter.register(ItemPostDetailsCommentDelegate(object : ItemPostDetailsCommentDelegate.CommentItemListener {
            override fun onClick(v: View, data: Comment, position: Int) {
                CRouter.go(AppRouter.appPostComment, str0 = post.id, obj0 = data)
            }

            override fun onLikeClick(item: Comment, position: Int) {
                likeComment(item, position)
            }

        }, object : BItemDelegate.BItemLongListener<Comment> {
            override fun onLongClick(v: View, event: MotionEvent, data: Comment, position: Int): Boolean {
                currComment = data
                currPosition = position
                if (user.id == currComment?.owner?.id) {
                    // 自己评论弹出删除
                    removeCommentDialog()
                } else {
                    // 别人的评论弹出举报
                    reportCommentDialog()
                }
                return true
            }
        }))

        mAdapter.items = mItems

        mBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        mBinding.recyclerView.adapter = mAdapter
        // 设置下拉刷新
        mBinding.refreshLayout.setOnRefreshListener {
            mBinding.refreshLayout.setNoMoreData(false)
            page = CConstants.defaultPage
            mViewModel.commentList(post.id)
        }
        mBinding.refreshLayout.setOnLoadMoreListener {
            mViewModel.commentList(post.id, page++)
        }
    }

    override fun onModelRefresh(model: BViewModel.UIModel) {
        if (model.type == "postInfo") {
            post = model.data as Post

            refreshPost()

            mViewModel.commentList(post.id)
        } else if (model.type == "commentList") {
            refreshComment(model.data as RPaging<Comment>)
        } else if (model.type == "shieldPost") {
            LDEventBus.post(Constants.Event.shieldPost, post)
            VMSystem.runInUIThread({ finish() }, CConstants.timeSecond)
        } else if (model.type == "deletePost") {
            removePost()
        } else if (model.type == "deleteComment") {
            removeComment()
        }
    }

    /**
     * 检查引导
     */
    private fun checkGuide() {
        if (!CSPManager.isNeedGuide(this@PostDetailsActivity::class.java.simpleName)) return

        mBinding.recyclerView.post {
            val list = mutableListOf<GuideItem>()
            val likeIV = mBinding.recyclerView.getChildAt(0).findViewById<View>(R.id.likeIV)
            val reportTV = mBinding.recyclerView.getChildAt(0).findViewById<View>(R.id.reportTV)
            val guideView = mBinding.recyclerView.getChildAt(0).findViewById<View>(R.id.itemGuideView)
            list.add(GuideItem(likeIV, VMStr.byRes(R.string.guide_post_like), shape = VMShape.guideShapeCircle, offX = VMDimen.dp2px(128), offY = VMDimen.dp2px(8)))
            list.add(GuideItem(reportTV, VMStr.byRes(R.string.guide_post_report), shape = VMShape.guideShapeCircle, offX = VMDimen.dp2px(32)))
            list.add(GuideItem(guideView, VMStr.byRes(R.string.guide_post_long_report), shape = VMShape.guideShapeCircle, offY = VMDimen.dp2px(8)))
            list.add(GuideItem(mBinding.commentIV, VMStr.byRes(R.string.guide_post_comment), shape = VMShape.guideShapeCircle, offX = VMDimen.dp2px(96), offY = VMDimen.dp2px(16)))
            VMGuide.Builder(this).setOneByOne(true).setGuideViews(list).setGuideListener(object : VMGuideView.GuideListener {
                override fun onFinish() {
                    CSPManager.setNeedGuide(this@PostDetailsActivity::class.java.simpleName, false)
                }

                override fun onNext(index: Int) {}
            }).build().show()
        }
    }

    /**
     * -------------------- 操作 Post --------------------
     */

    /**
     * 刷新帖子内容
     */
    private fun refreshPost() {
        mItems.removeAt(0)
        mItems.add(0, post)
        mAdapter.notifyItemChanged(0)

        checkGuide()
    }

    /**
     * 删除帖子
     */
    private fun removePost() {
        finish()
    }

    /**
     * 处理点击喜欢帖子事件
     */
    private fun likePost() {
        post.isLike = !post.isLike
        if (post.isLike) {
            post.likeCount++
            mViewModel.like(post.id)
        } else {
            post.likeCount--
            mViewModel.cancelLike(post.id)
        }
        mAdapter.notifyItemChanged(0)
    }

    /**
     * 删除评论弹窗
     */
    private fun removePostDialog() {
        mDialog = CommonDialog(this)
        (mDialog as CommonDialog).let { dialog ->
            dialog.setContent(R.string.content_delete_tips)
            dialog.setPositive { mViewModel.deletePost(post.id) }
            dialog.show()
        }
    }

    /**
     * 弹出屏蔽举报菜单
     */
    private fun reportPostDialog() {
        mDialog = ContentDislikeDialog(this)
        (mDialog as ContentDislikeDialog).let { dialog ->
            dialog.setShieldListener { type -> shieldPost(type) }
            dialog.setReportListener { type -> reportPost(type) }
            dialog.show(Gravity.BOTTOM)
        }
    }


    /**
     * 屏蔽 Post
     */
    private fun shieldPost(type: Int) {
        mDialog?.dismiss()

        if (type == 0) {
            // TODO 屏蔽内容
        } else if (type == 1) {
            // TODO 屏蔽用户
        }
        post.isShielded = true
        mViewModel.shieldPost(post)
    }

    /**
     * 举报 Post
     * 0-意见建议 1-广告引流 2-政治敏感 3-违法违规 4-色情低俗 5-血腥暴力 6-诱导信息 7-谩骂攻击 8-涉嫌诈骗 9-引人不适 10-其他
     */
    private fun reportPost(type: Int) {
        mDialog?.dismiss()

        CRouter.go(AppRouter.appFeedback, what = type, obj0 = post)
    }

    /**
     * -------------------- 操作 Comment --------------------
     */

    /**
     * 刷新评论
     */
    private fun refreshComment(paging: RPaging<Comment>) {
        val position = mItems.size
        val count = paging.data.size
        mItems.addAll(paging.data)
        mAdapter.notifyItemRangeInserted(position, count)
        if (paging.currentCount + paging.page * paging.limit >= paging.totalCount) {
            mBinding.refreshLayout.setNoMoreData(true)
        }
    }

    /**
     * 移除评论
     */
    private fun removeComment() {
        mItems.remove(currPosition)
        mAdapter.notifyItemRemoved(currPosition)
    }

    /**
     * 处理点击喜欢帖子事件
     */
    private fun likeComment(comment: Comment, position: Int) {
        comment.isLike = !comment.isLike
        if (comment.isLike) {
            comment.likeCount++
            mViewModel.like(comment.id, 2)
        } else {
            comment.likeCount--
            mViewModel.cancelLike(comment.id, 2)
        }
        mAdapter.notifyItemChanged(position)
    }

    /**
     * 删除评论弹窗
     */
    private fun removeCommentDialog() {
        mDialog = CommonDialog(this)
        (mDialog as CommonDialog).let { dialog ->
            dialog.setContent(R.string.content_delete_tips)
            dialog.setPositive { mViewModel.deleteComment(currComment?.id ?: "") }
            dialog.show()
        }
    }

    /**
     * 弹出屏蔽举报菜单
     */
    private fun reportCommentDialog() {
        mDialog = ContentDislikeDialog(this)
        (mDialog as ContentDislikeDialog).let { dialog ->
//            dialog.setShieldListener { type -> shieldPost(type) }
            dialog.setReportListener { type -> reportComment(type) }
            dialog.show(Gravity.BOTTOM)
        }
    }

    /**
     * 举报 Commet
     * 0-意见建议 1-广告引流 2-政治敏感 3-违法违规 4-色情低俗 5-血腥暴力 6-诱导信息 7-谩骂攻击 8-涉嫌诈骗 9-引人不适 10-其他
     */
    private fun reportComment(type: Int) {
        mDialog?.dismiss()

        CRouter.go(AppRouter.appFeedback, what = type, obj0 = currComment)
    }
}