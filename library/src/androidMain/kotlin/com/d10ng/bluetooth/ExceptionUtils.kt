package com.d10ng.bluetooth

/**
 * 异常类型
 * @Author d10ng
 * @Date 2024/9/10 16:05
 */
// 缺少定位权限
class LocationPermissionException : Exception("缺少定位权限")

// 缺少蓝牙权限
class BluetoothPermissionException : Exception("缺少蓝牙权限")

// 位置信息未开启
class LocationOffException : Exception("位置信息未开启")

// 未找到设备
class DeviceNotFoundException : Exception("未找到设备")