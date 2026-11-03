# Android MTU 与特征写入长度核实

核实日期：2026-11-03。

结论：项目把 MTU 517 转换为最大 payload 514，确实可能导致非法写入。AOSP Android 15、16 的 BluetoothGatt.writeCharacteristic 明确拒绝超过 512 字节的 value。前次审查将直接抛异常的范围写为 API 33+ 不准确：核对的 Android 13、14 release 分支没有相同 Java 层检查，不能推断它们具有相同异常行为。

## 一手证据

- [Android 15 常量，行95](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android15-release/framework/java/android/bluetooth/BluetoothGatt.java#95)：`GATT_MAX_ATTR_LEN = 512`。
- [Android 15 写入检查，行1558](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android15-release/framework/java/android/bluetooth/BluetoothGatt.java#1558)：当 `value.length > GATT_MAX_ATTR_LEN` 时抛出 `IllegalArgumentException`。
- [Android 16 同一检查，行1674](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android16-release/framework/java/android/bluetooth/BluetoothGatt.java#1674)。
- [官方 requestMtu 文档](https://developer.android.com/reference/android/bluetooth/BluetoothGatt#requestMtu(int))：Android 14 起首个 GATT 客户端请求 MTU 时系统请求517；实际结果由 onMtuChanged 回调报告，不保证每个外设协商得到517。
- [Android 13 源码](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android13-release/framework/java/android/bluetooth/BluetoothGatt.java#1356)、[Android 14 源码](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/android14-release/framework/java/android/bluetooth/BluetoothGatt.java#1433)：核对的版本中没有上述 Java 层512字节长度检查。

## 对本项目的影响

`AndroidBleConnection.requestMaxMtu()` 返回 MTU减3。若实际协商值为517，返回514；Demo直接使用此长度切分payload。`BluetoothGattUtils.executeWrite()`在Android 13及以上调用三参数writeCharacteristic，因此在上述已确认平台上，513或514字节分包会触发异常。

建议对返回值使用 `minOf(mtu - 3, 512)`。这只是系统/协议层上限；外设自身特征或业务协议仍可能要求更小长度。项目Runner的异常传播问题需独立修复。

本次核实为官方文档与源码检查，未进行真机513/514字节写入实验；未修改业务代码。
