package com.upang.hkfacilitator.models

data class Account(
    val login: String? = null,
    var password: String? = null,
    var pin: String? = null
)

data class User(
    val email: String? = null,
    val name: String? = null,
    val gender: String? = null,
    val id: String? = null,
    var course: String? = null,
    val hk: String? = null,
    var hrs: Float? = null,
    var render: Float? = null,
    var counter: Int? = null,
    var profileUrl: String? = null,
    val history: String? = null,
    var timeIn: String? = null,
    var timeOut: String? = null,
    var appeals: Int? = null,
    var lastProfile: Int? = null,
    val extension: Boolean? = null,
    val dtr: HashMap<String, Schedule>? = null,
    var lastSchedule: Int? = null,
    val marked: Boolean? = null,
    var lastSuspend: Int? = null,
    val assignment: String? = null
)

data class Schedule(
    var id: String? = null,
    var title: String? = null,
    val owner: String? = null,
    var email: String? = null,
    var need: Int? = null,
    var room: String? = null,
    var date: String? = null,
    var time: String? = null,
    var joined: Int? = null,
    var detail: String? = null,
    var subject: String? = null,
    var restrict: Boolean? = null,
    var completed: String? = null,
    val edited: Boolean? = null,
    var remark: String? = null,
    var hours: Float? = null,
    var appeal: String? = null,
    var approved: Boolean? = null,
    var proof: String? = null,
    var isActive: Boolean? = null,
    var isJoinedIn: Boolean? = null,
    var isDone: Boolean? = null,
    var faci: HashMap<String, User>? = null,
    var timeIn: String? = null,
    var timeOut: String? = null,
    var extension: Boolean? = null,
    var extended: Boolean? = null,
    var inCompleted: Boolean? = null,
    var suspended: Boolean? = null,
    val notified: String? = null
)

data class Evaluation(
    val id: String? = null,
    val name: String? = null,
    val gender: String? = null,
    val course: String? = null,
    val date: String? = null,
    val comment: String? = null
)

data class Bug(
    val deviceModel : String? = null,
    val androidVersion : String? = null,
    val appVersion : String? = null,
    val bugDescription : String? = null,
    val toAchieve : String? = null
)

data class Timestamp(
    val id: String? = null,
    val time: String? = null
)

data class Notifications(
    val id: String? = null,
    val title: String? = null,
    val message: String? = null,
    val datetime: String? = null,
    val link: String? = null,
    var read: Boolean? = null,
    val notified: Boolean? = null
)

data class App(
    val link: String? = null,
    val version: String? = null,
    val changes: String? = null
)

data class School(
    var icon: String? = null,
    var name: String? = null,
    var appID: String? = null,
    var apiKey: String? = null,
    var dbUrl: String? = null,
    var storage: String? = null
)