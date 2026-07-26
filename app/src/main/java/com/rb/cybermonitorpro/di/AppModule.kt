package com.rb.cybermonitorpro.di

import com.rb.cybermonitorpro.data.repository.DeviceRepository
import com.rb.cybermonitorpro.ui.AppViewModel
import com.rb.cybermonitorpro.ui.battery.BatteryViewModel
import com.rb.cybermonitorpro.ui.cpu.CpuViewModel
import com.rb.cybermonitorpro.ui.dashboard.DashboardViewModel
import com.rb.cybermonitorpro.ui.device.DeviceViewModel
import com.rb.cybermonitorpro.ui.gps.GpsViewModel
import com.rb.cybermonitorpro.ui.gpu.GpuViewModel
import com.rb.cybermonitorpro.ui.memory.MemoryViewModel
import com.rb.cybermonitorpro.ui.network.NetworkViewModel
import com.rb.cybermonitorpro.ui.oem.OemViewModel
import com.rb.cybermonitorpro.ui.sensors.SensorDetailViewModel
import com.rb.cybermonitorpro.ui.sensors.SensorsViewModel
import com.rb.cybermonitorpro.ui.settings.SettingsViewModel
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
