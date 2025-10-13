package com.upang.hkfacilitator.utils

import android.net.Uri
import com.upang.hkfacilitator.databinding.ItemAssignBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.viewholders.*

interface AssignClickListener {
    fun onSchedClick(sched: Schedule, card: ItemAssignBinding)
    fun getViewHolder(id: String): List<AssignViewHolder>
}

interface SchedFaciClickListener {
    fun onTimeClick(user: User, isTimeIn: Boolean)
    fun onCheckClick()
}

interface SchedFaciBatchedClickListener {
    fun onRadioSelect()
}

interface EvaluationClickListener {
    fun onEvalEditClick(eval: Evaluation)
    fun onEvalDeleteClick(id: String)
}

interface DTRClickListener {
    fun onBtnClick(sched: Schedule, uri: Uri?)
    fun onAddProofClick(onComplete: (uri: Uri) -> Unit)
    fun onViewProofClick(url: String)
}

interface FacilitatorClickListener {
    fun onFacilitatorClick(user: User)
}

interface FaciReqClickListener {
    fun onReqClick(url: String)
    fun onAddReqClick()
}

interface FacultyClickListener {
    fun onFacultyClick(user: User)
    fun onManagerClick(user: User)
}

interface SchedClickListener {
    fun onSchedClick(sched: Schedule)
}

interface TimeClickListener {
    fun onTimeDeleteClick(id: String)
}

interface ConnectionStateListener {
    fun onNetworkAvailable()
    fun onNetworkLost()
}

interface CourseClickListener {
    fun onDeleteClick(course: String)
}

interface NotificationClickListener {
    fun getViewHolder(id: String): List<NotificationViewHolder>
    fun readNotification(id: String)
    fun navigateToLink(link: String, destination: String)
}

interface SchoolClickListener {
    fun onSchoolClick(school: String)
}