package com.example.deviceinfoviewer.di

import com.example.deviceinfoviewer.data.repository.DeviceRepository
import com.example.deviceinfoviewer.ui.AppViewModel
import com.example.deviceinfoviewer.ui.battery.BatteryViewModel
import com.example.deviceinfoviewer.ui.cpu.CpuViewModel
import com.example.deviceinfoviewer.ui.dashboard.DashboardViewModel
import com.example.deviceinfoviewer.ui.device.DeviceViewModel
import com.example.deviceinfoviewer.ui.gps.GpsViewModel
import com.example.deviceinfoviewer.ui.gpu.GpuViewModel
import com.example.deviceinfoviewer.ui.memory.MemoryViewModel
import com.example.deviceinfoviewer.ui.network.NetworkViewModel
import com.example.deviceinfoviewer.ui.oem.OemViewModel
import com.example.deviceinfoviewer.ui.sensors.SensorDetailViewModel
import com.example.deviceinfoviewer.ui.sensors.SensorsViewModel
import com.example.deviceinfoviewer.ui.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { DeviceRepository(androidContext()) }

    viewModel { AppViewModel(get()) }
    viewModel { DashboardViewModel(get()) }
    viewModel { CpuViewModel(get()) }
    viewModel { GpuViewModel(get()) }
    viewModel { MemoryViewModel(get()) }
    viewModel { BatteryViewModel(get()) }
    viewModel { NetworkViewModel(get()) }
    viewModel { GpsViewModel(get()) }
    viewModel { SensorsViewModel(get()) }
    viewModel { SensorDetailViewModel(get()) }
    viewModel { DeviceViewModel(get()) }
    viewModel { OemViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
}
