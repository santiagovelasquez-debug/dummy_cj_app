package com.appbase.di

import com.appbase.data.remote.CJ9ApiService
import com.appbase.data.repository.SubscriptionRepositoryImpl
import com.appbase.domain.repository.SubscriptionRepository
import com.appbase.ui.subscription.SubscriptionViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val subscriptionModule = module {

    single<Retrofit> {
        Retrofit.Builder()
            .baseUrl(SubscriptionRepositoryImpl.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    single<CJ9ApiService> {
        get<Retrofit>().create(CJ9ApiService::class.java)
    }

    single<SubscriptionRepository> {
        SubscriptionRepositoryImpl(get())
    }

    viewModel {
        SubscriptionViewModel(get())
    }
}
