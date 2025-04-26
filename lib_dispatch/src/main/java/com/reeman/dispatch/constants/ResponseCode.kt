package com.reeman.dispatch.constants

object ResponseCode {

    const val SUCCESS = 0
    const val UNAUTHORIZED = 1
    const val CREATE_ROOM_FAILED = 2
    const val ROOM_ALREADY_EXIST = 3
    const val REQUEST_PARAM_ERROR = 4
    const val UNKNOWN_ERROR = 5
    const val ROOM_PASSWORD_ERROR = 6
    const val ROBOT_NOT_BOUND_TO_ROOM_ERROR = 7
    const val ROBOT_BOUND_TO_TOO_MANY_ROOMS_ERROR = 8
    const val ROBOT_ALREADY_BOUND_TO_ANOTHER_ROOM_ERROR = 9
    const val POINT_CONNECTION_ERROR = 10
    const val PARSE_TOKEN_FAILED_ERROR = 11
    const val UPLOAD_MAP_INFO_FAILED = 12
    const val MAP_ILLEGAL_ERROR = 13
    const val MAP_NOT_FOUND_ERROR = 14
    const val ELEVATOR_SETTING_ERROR = 15
    const val ROOM_NOT_FOUND_ERROR = 16
    const val ROBOT_OFFLINE_ERROR = 17
    const val ROBOT_FAR_FROM_ROUTE_ERROR = 18
    const val CREATE_PATH_TO_TARGET_FAILED_ERROR = 19
    const val CREATE_PATH_TO_WAITING_ELEVATOR_POINT_ERROR = 20
    const val POINT_NOT_FOUND_ERROR = 21
    const val ALREADY_AT_TARGET_POINT_ERROR = 22
    const val MAP_LIST_EMPTY_ERROR = 23
    const val NOT_FOUND_FREE_CHARGE_POINT_ERROR = 24
    const val MISS_CHARGE_AND_PRODUCTION_POINT_ERROR = 25
    const val MISS_CHARGE_POINT_ERROR = 26
    const val MISS_PRODUCTION_POINT_ERROR = 27
}